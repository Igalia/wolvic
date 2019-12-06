package org.mozilla.vrbrowser.browser.extensions;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import mozilla.components.concept.sync.AuthType;
import mozilla.components.concept.sync.OAuthAccount;
import mozilla.components.concept.sync.Profile;
import mozilla.components.service.fxa.FxaAuthData;
import mozilla.components.service.fxa.SyncEngine;
import mozilla.components.service.fxa.manager.FxaAccountManager;
import mozilla.components.support.base.feature.LifecycleAwareFeature;

public class FxAWebChannelFeature implements LifecycleAwareFeature, SessionStore.SessionStoreObserver {

    private static final String LOGTAG = SystemUtils.createLogtag(FxAWebChannelFeature.class);

    public enum FxaCapability {
        // Enables "choose what to sync" selection during support auth flows (currently, sign-up).
        CHOOSE_WHAT_TO_SYNC
    }

    enum WebChannelCommand {
        CAN_LINK_ACCOUNT,
        OAUTH_LOGIN,
        FXA_STATUS
    }

    private static final String EXTENSION_ID = "mozacWebchannel";
    private static final String EXTENSION_URL = "resource://android/assets/web_extensions/fxawebchannel/";
    private static final String CHANNEL_ID = "account_updates"; // Constants for incoming messages from the WebExtension.

    private FxaAccountManager mAccountManager;
    private Set<FxaCapability> mFxaCapabilities;
    private WebExtensionController mExtensionController = new WebExtensionController(EXTENSION_ID, EXTENSION_URL);

    public FxAWebChannelFeature(@NonNull FxaAccountManager accountManager, @Nullable Set<FxaCapability> fxaCapabilities) {
        mAccountManager = accountManager;
        mFxaCapabilities = fxaCapabilities;
    }

    @Override
    public void start() {
        SessionStore sessionStore = SessionStore.get();
        sessionStore.addObserver(this);
        mExtensionController.install(sessionStore);
    }

    @Override
    public void stop() {
        SessionStore.get().removeObserver(this);
    }

    @Override
    public void onSessionAdded(@NonNull Session session) {
        registerFxaContentMessageHandler(session);
    }

    @Override
    public void onSessionRemoved(@NonNull Session session) {
        mExtensionController.disconnectPort(session, EXTENSION_ID);
    }

    private void registerFxaContentMessageHandler(@NonNull Session session) {
        WebChannelViewContentMessageHandler messageHandler = new WebChannelViewContentMessageHandler(mAccountManager, mFxaCapabilities);
        mExtensionController.registerContentMessageHandler(session, messageHandler, EXTENSION_ID);
    }

    /**
     * Communication channel is established from fxa-web-content to this class via webextension, as follows:
     * [fxa-web-content] <--js events--> [fxawebchannel.js webextension] <--port messages--> [FxaWebChannelFeature]
     *
     * Overall message flow, as implemented by this class, is documented below. For detailed message descriptions, see:
     * https://github.com/mozilla/fxa/blob/master/packages/fxa-content-server/docs/relier-communication-protocols/fx-webchannel.md
     *
     * [fxa-web-channel]            [FxaWebChannelFeature]         Notes:
     *     loaded           ------>          |                  fxa web content loaded
     *     fxa-status       ------>          |                  web content requests account status & device capabilities
     *        |             <------ fxa-status-response         this class responds, based on state of [accountManager]
     *     can-link-account ------>          |                  user submitted credentials, web content verifying if account linking is allowed
     *        |             <------ can-link-account-response   this class responds, based on state of [accountManager]
     *     oauth-login      ------>                             authentication completed within fxa web content, this class receives OAuth code & state
     */
    private class WebChannelViewContentMessageHandler implements WebExtension.MessageHandler {

        private FxaAccountManager mAccountManager;
        private Set<FxaCapability> mFxACapabilities;

        public WebChannelViewContentMessageHandler(@NonNull FxaAccountManager accountManager,
                                                   @NonNull Set<FxaCapability> fxaCapabilities) {
            mAccountManager = accountManager;
            mFxACapabilities = fxaCapabilities;
        }

        @Override
        public void onPortConnected(@NonNull WebExtension.Port port) {
            // Nothing to do
        }

        @Override
        public void onPortDisconnected(@NonNull WebExtension.Port port) {
            // Nothing to do
        }

        @Override
        public void onPortMessage(@NonNull Object message, @NonNull WebExtension.Port port) {
            JSONObject json;
            try {
                json = ((JSONObject) message);

            } catch (ClassCastException e) {
                Log.e(LOGTAG, "Received an invalid WebChannel message of type: " + message.getClass().getName());
                return;
            }

            JSONObject payload;
            WebChannelCommand command;
            String messageId;

            try {
                payload = json.getJSONObject("message");
                command = toWebChannelCommand(payload.getString("command"));
                if (command == null) {
                    throw new JSONException("Couldn't get WebChannel command");
                }
                messageId = payload.optString("messageId", "");

            } catch (JSONException e) {
                // We don't have control over what messages we will get from the webchannel.
                // If somehow we're receiving mis-constructed messages, it's probably best to not
                // blow up the host application. This comes at a cost: we might not catch problems
                // as quickly if we're not crashing (and thus receiving crash logs).
                // TODO ideally, this should log to Sentry.
                Log.e(LOGTAG, "Error while processing WebChannel command", e);
                return;
            }

            Log.d(LOGTAG, "Processing WebChannel command: " + command.name());

            JSONObject response;
            switch (command) {
                case CAN_LINK_ACCOUNT:
                    response = processCanLinkAccountCommand(messageId);
                    break;

                case FXA_STATUS:
                    response = processFxaStatusCommand(mAccountManager, messageId, mFxACapabilities);
                    break;

                case OAUTH_LOGIN:
                    response = processOauthLoginCommand(mAccountManager, payload);
                    break;

                default:
                    response = null;
            }

            if (response != null) {
                port.postMessage(response);
            }
        }

        @Nullable
        @Override
        public Object onMessage(@NonNull Object message, @Nullable Session source) {
            // Nothing to do
            return null;
        }
    }

    // For all possible messages and their meaning/payloads, see:
    // https://github.com/mozilla/fxa/blob/master/packages/fxa-content-server/docs/relier-communication-protocols/fx-webchannel.md
    /**
     * Gets triggered when user initiates a login within FxA web content.
     * Expects a response.
     * On Fx Desktop, this event triggers "a different user was previously signed in on this machine" warning.
     */
    private static final String COMMAND_CAN_LINK_ACCOUNT = "fxaccounts:can_link_account";

    /**
     * Gets triggered when a user successfully authenticates via OAuth.
     */
    private static final String COMMAND_OAUTH_LOGIN = "fxaccounts:oauth_login";

    /**
     * Gets triggered on startup to fetch the FxA state from the host application.
     * Expects a response, which includes application's capabilities and a description of the
     * current Firefox Account (if present).
     */
    private static final String COMMAND_STATUS = "fxaccounts:fxa_status";

    /**
     * Handles the [COMMAND_CAN_LINK_ACCOUNT] event from the web-channel.
     * Currently this always response with 'ok=true'.
     * On Fx Desktop, this event prompts a possible "another user was previously logged in on
     * this device" warning. Currently we don't support propagating this warning to a consuming application.
     */
    private JSONObject processCanLinkAccountCommand(@NonNull String messageId) {
        JSONObject status = new JSONObject();
        try {
            JSONObject data = new JSONObject();
            data.put("ok", true);

            JSONObject message = new JSONObject();
            message.put("messageId", messageId);
            message.put("command", COMMAND_CAN_LINK_ACCOUNT);
            message.put("data", data);

            status.put("id", CHANNEL_ID);
            status.put("message", message);

        } catch (JSONException e) {
            Log.d(LOGTAG, "Error: " + e.getLocalizedMessage());
            e.printStackTrace();
        }

        return status;
    }

    /**
     * Handles the [COMMAND_STATUS] event from the web-channel.
     * Responds with supported application capabilities and information about currently signed-in Firefox Account.
     */
    private JSONObject processFxaStatusCommand(@NonNull FxaAccountManager accountManager,
                                               @NonNull String messageId,
                                               @NonNull Set<FxaCapability> fxaCapabilities) {
        JSONObject status =  new JSONObject();
        try {
            JSONArray engines = new JSONArray();
            Set<SyncEngine> supportedEngines = accountManager.supportedSyncEngines();
            if (supportedEngines != null) {
                supportedEngines.forEach(engine -> engines.put(engine.getNativeName()));
            }
            JSONObject capabilities = new JSONObject();
            capabilities.put("engines", engines);
            if (fxaCapabilities.contains(FxaCapability.CHOOSE_WHAT_TO_SYNC)) {
                capabilities.put("choose_what_to_sync", true);
            }

            JSONObject data = new JSONObject();
            data.put("capabilities", capabilities);
            final OAuthAccount account = accountManager.authenticatedAccount();
            if (account == null) {
                data.put("signedInUser", JSONObject.NULL);

            } else {
                Profile profile = accountManager.accountProfile();
                JSONObject signedInUser = new JSONObject();
                signedInUser.put("email", profile != null ? profile.getEmail() : JSONObject.NULL);
                signedInUser.put("uid", profile != null ? profile.getUid() : JSONObject.NULL);
                signedInUser.put("sessionToken", profile != null ? account.getSessionToken() : JSONObject.NULL);
                // Our account state machine only ever completes authentication for
                // "verified" accounts, so this is always 'true'.
                signedInUser.put("verified", true);
                data.put("signedInUser", signedInUser);
            }

            JSONObject message = new JSONObject();
            message.put("messageId", messageId);
            message.put("command", COMMAND_STATUS);
            message.put("data", data);

            status.put("id", CHANNEL_ID);
            status.put("message", message);

        } catch (JSONException e) {
            Log.d(LOGTAG, "Error: " + e.getLocalizedMessage());
            e.printStackTrace();
        }

        return status;
    }

    private static List<String> toStringList(@Nullable JSONArray array) {
        List<String> result = Collections.emptyList();

        if (array != null) {
            for (int i=0; i<array.length(); i++) {
                String string = array.optString(i, null);
                if (string != null) {
                    result.add(string);
                }
            }
        }

        return result;
    }

    /**
     * Handles the [COMMAND_OAUTH_LOGIN] event from the web-channel.
     */
    @Nullable
    private JSONObject processOauthLoginCommand(FxaAccountManager accountManager, JSONObject payload) {
        AuthType authType;
        String code;
        String state;
        List<String> declinedEngines;

        try {
            JSONObject data = payload.getJSONObject("data");
            data.put("ok", true);

            authType = toAuthType(data.getString("action"));
            code = data.getString("code");
            state = data.getString("state");
            declinedEngines = toStringList(data.optJSONArray("declinedSyncEngines"));

        } catch (JSONException e) {
            Log.d(LOGTAG, "Error: " + e.getLocalizedMessage());
            return null;
        }

        accountManager.finishAuthenticationAsync(new FxaAuthData(
                authType,
                code,
                state,
                toSyncEngines(declinedEngines)
        ));

        return null;
    }

    @Nullable
    private static WebChannelCommand toWebChannelCommand(@NonNull String command) {
        switch (command) {
            case COMMAND_CAN_LINK_ACCOUNT:
                return WebChannelCommand.CAN_LINK_ACCOUNT;

            case COMMAND_OAUTH_LOGIN:
                return WebChannelCommand.OAUTH_LOGIN;

            case COMMAND_STATUS:
                return WebChannelCommand.FXA_STATUS;

            default:
                Log.w(LOGTAG, "Unrecognized WebChannel command: " + command);
                return null;
        }
    }

    /**
     * Converts a raw 'action' string into an [AuthType] instance.
     * Actions come to us from FxA during an OAuth login, either over the WebChannel or via the redirect URL.
     */
    private static AuthType toAuthType(@NonNull String string) {
        switch (string) {
            case "signin":
                return AuthType.Signin.INSTANCE;

            case "signup":
                return AuthType.Signup.INSTANCE;

            case "pairing":
                return AuthType.Pairing.INSTANCE;

            default:
                return new AuthType.OtherExternal(string);
        }
    }

    /**
     * Converts from a list of raw strings describing engines to a set of [SyncEngine] objects.
     */
    private static Set<SyncEngine> toSyncEngines(@NonNull List<String> list) {
        return list.stream().map(FxAWebChannelFeature::toSyncEngine).collect(Collectors.toSet());
    }

    private static SyncEngine toSyncEngine(@NonNull String string) {
        switch (string) {
            case "history":
                return SyncEngine.History.INSTANCE;

            case "bookmarks":
                return SyncEngine.Bookmarks.INSTANCE;

            case "passwords":
                return SyncEngine.Passwords.INSTANCE;

            default:
                return new SyncEngine.Other(string);
        }
    }

}
