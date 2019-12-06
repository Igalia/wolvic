package org.mozilla.vrbrowser.browser.extensions;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.browser.extensions.WebExtension.MessageHandler;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class WebExtensionController {

    private static final String LOGTAG = SystemUtils.createLogtag(WebExtensionController.class);

    private String mExtensionId;
    private String mExtensionUrl;
    private Function<WebExtension, Void> mRegisterContentMessageHandler;

    private static ConcurrentHashMap<String, WebExtension> installedExtensions = new ConcurrentHashMap<>();

    /**
     * Provides functionality to feature modules that need to interact with a web extension.
     *
     * @param extensionId the unique ID of the web extension e.g. mozacReaderview.
     * @param extensionUrl the url pointing to a resources path for locating the
     * extension within the APK file e.g. resource://android/assets/extensions/my_web_ext.
     */
    WebExtensionController(@NonNull String extensionId,
                           @NonNull String extensionUrl) {
        mExtensionId = extensionId;
        mExtensionUrl = extensionUrl;
    }

    /**
     * Makes sure the web extension is installed in the provided engine. If a
     * content message handler was registered (see
     * [registerContentMessageHandler]) before install completed, registration
     * will happen upon successful installation.
     *
     * @param sessionStore the [SessionStore] the web extension should be installed in.
     */
    void install(@NonNull SessionStore sessionStore) {
        if (!installedExtensions.containsKey(mExtensionId)) {
            sessionStore.installWebExtension(mExtensionId, mExtensionUrl, true, extension -> {
                Log.d(LOGTAG, "Installed extension: " + mExtensionId);
                synchronized(WebExtensionController.this) {
                    if (mRegisterContentMessageHandler != null) {
                        mRegisterContentMessageHandler.apply(extension);
                    }
                    installedExtensions.put(mExtensionId, extension);
                }
                return null;
            }, throwable -> {
                Log.e(LOGTAG, throwable);
                return null;
            });
        }
    }

    /**
     * Registers a content message handler for the provided session. Currently only one
     * handler can be registered per session. An existing handler will be replaced and
     * there is no need to unregister.
     *
     * @param engineSession the session the content message handler should be registered with.
     * @param messageHandler the message handler to register.
     * @param name (optional) name of the port, defaults to the provided extensionId.
     */
    void registerContentMessageHandler(@NonNull Session engineSession,
                                       @NonNull MessageHandler messageHandler,
                                       @NonNull String name) {
        synchronized(this) {
            mRegisterContentMessageHandler = webExtension -> {
                webExtension.registerContentMessageHandler(engineSession, name, messageHandler);
                return null;
            };

            if (installedExtensions.get(mExtensionId) != null) {
                mRegisterContentMessageHandler.apply(installedExtensions.get(mExtensionId));
            }
        }
    }

    /**
     * Sends a content message to the provided session.
     * @param msg the message to send
     * @param engineSession the session to send the content message to.
     * @param name (optional) name of the port, defaults to the provided extensionId.
     */
    void sendContentMessage(@NonNull JSONObject msg,
                            @Nullable Session engineSession,
                            @NonNull String name) {
        if (engineSession != null) {
            WebExtension extension = installedExtensions.get(mExtensionId);
            if (extension != null) {
                WebExtension.Port port = extension.getConnectedPort(name, engineSession);
                if (port != null) {
                    port.postMessage(msg);
                }
            }
        }
    }

    /**
     * Checks whether or not a port is connected for the provided session.
     * @param engineSession the session the port should be connected to.
     * @param name (optional) name of the port, defaults to the provided extensionId.
     */
    boolean portConnected(@Nullable Session engineSession,
                          @NonNull String name) {
        if (engineSession != null) {
            WebExtension extension = installedExtensions.get(mExtensionId);
            if (extension != null) {
                return extension.getConnectedPort(name, engineSession) != null;
            }

            return false;
        }

        return false;
    }

    /**
     * Disconnects the port of the provided session.
     *
     * @param engineSession the session the port is connected to.
     * @param name (optional) name of the port, defaults to the provided extensionId.
     */
    void disconnectPort(@Nullable Session engineSession,
                        @NonNull String name) {
        if (engineSession != null) {
            WebExtension extension = installedExtensions.get(mExtensionId);
            if (extension != null) {
                extension.disconnectPort(name, engineSession);
            }
        }
    }

}
