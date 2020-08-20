package org.mozilla.vrbrowser.browser;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.Autocomplete;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.SlowScriptResponse;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.components.GeckoLoginDelegateWrapper;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.browser.engine.SessionState;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.db.SitePermission;
import org.mozilla.vrbrowser.ui.viewmodel.SitePermissionViewModel;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.ui.widgets.WindowWidget;
import org.mozilla.vrbrowser.ui.widgets.prompts.AlertPromptWidget;
import org.mozilla.vrbrowser.ui.widgets.prompts.AuthPromptWidget;
import org.mozilla.vrbrowser.ui.widgets.prompts.ChoicePromptWidget;
import org.mozilla.vrbrowser.ui.widgets.prompts.ConfirmPromptWidget;
import org.mozilla.vrbrowser.ui.widgets.prompts.PromptWidget;
import org.mozilla.vrbrowser.ui.widgets.prompts.SaveLoginPromptWidget;
import org.mozilla.vrbrowser.ui.widgets.prompts.SelectLoginPromptWidget;
import org.mozilla.vrbrowser.ui.widgets.prompts.TextPromptWidget;
import org.mozilla.vrbrowser.ui.widgets.settings.SettingsView;
import org.mozilla.vrbrowser.utils.StringUtils;
import org.mozilla.vrbrowser.utils.UrlUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import mozilla.components.concept.storage.Login;

public class PromptDelegate implements
        GeckoSession.PromptDelegate,
        WindowWidget.WindowListener,
        GeckoSession.NavigationDelegate,
        GeckoSession.ContentDelegate {

    private PromptWidget mPrompt;
    private ConfirmPromptWidget mSlowScriptPrompt;
    private Context mContext;
    private WindowWidget mAttachedWindow;
    private List<SitePermission> mAllowedPopUpSites;
    private List<SitePermission> mSavedLoginBlockedSites;
    private SitePermissionViewModel mViewModel;
    private WidgetManagerDelegate mWidgetManager;
    private SaveLoginPromptWidget mSaveLoginPrompt;
    private SelectLoginPromptWidget mSelectLoginPrompt;

    public PromptDelegate(@NonNull Context context) {
        mContext = context;
        mWidgetManager = (WidgetManagerDelegate) mContext;
        mViewModel = new SitePermissionViewModel(((Application)context.getApplicationContext()));
        mAllowedPopUpSites = new ArrayList<>();
        mSavedLoginBlockedSites = new ArrayList<>();
    }

    public void attachToWindow(@NonNull WindowWidget window) {
        if (window == mAttachedWindow) {
            return;
        }
        detachFromWindow();

        mAttachedWindow = window;
        mAttachedWindow.addWindowListener(this);
        mViewModel.getAll(SitePermission.SITE_PERMISSION_POPUP).observeForever(mPopUpSiteObserver);
        mViewModel.getAll(SitePermission.SITE_PERMISSION_AUTOFILL).observeForever(mSavedLoginExceptionsObserver);

        if (getSession() != null) {
            setUpSession(getSession());
        }
    }

    public void detachFromWindow() {
        if (getSession() != null) {
            cleanSession(getSession());
        }

        if (mAttachedWindow != null) {
            mAttachedWindow.removeWindowListener(this);
            mAttachedWindow = null;
        }
        mViewModel.getAll(SitePermission.SITE_PERMISSION_POPUP).removeObserver(mPopUpSiteObserver);
        mViewModel.getAll(SitePermission.SITE_PERMISSION_AUTOFILL).removeObserver(mSavedLoginExceptionsObserver);
    }

    private Session getSession() {
        if (mAttachedWindow != null) {
            return mAttachedWindow.getSession();
        }
        return null;
    }

    private void setUpSession(@NonNull Session aSession) {
        aSession.setPromptDelegate(this);
        aSession.addNavigationListener(this);
        aSession.addContentListener(this);
    }

    private void cleanSession(@NonNull Session aSession) {
        aSession.setPromptDelegate(null);
        aSession.removeNavigationListener(this);
        aSession.removeContentListener(this);
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onAlertPrompt(@NonNull GeckoSession geckoSession, @NonNull AlertPrompt alertPrompt) {
        final GeckoResult<PromptResponse> result = new GeckoResult<>();

        mPrompt = new AlertPromptWidget(mContext);
        mPrompt.getPlacement().parentHandle = mAttachedWindow.getHandle();
        mPrompt.getPlacement().parentAnchorY = 0.0f;
        mPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.js_prompt_y_distance);
        mPrompt.setTitle(alertPrompt.title);
        mPrompt.setMessage(alertPrompt.message);
        mPrompt.setPromptDelegate(() -> result.complete(alertPrompt.dismiss()));
        mPrompt.show(UIWidget.REQUEST_FOCUS, true);

        return result;
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onButtonPrompt(@NonNull GeckoSession geckoSession, @NonNull ButtonPrompt buttonPrompt) {
        final GeckoResult<PromptResponse> result = new GeckoResult<>();

        mPrompt = new ConfirmPromptWidget(mContext);
        mPrompt.getPlacement().parentHandle = mAttachedWindow.getHandle();
        mPrompt.getPlacement().parentAnchorY = 0.0f;
        mPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.js_prompt_y_distance);
        mPrompt.setTitle(buttonPrompt.title);
        mPrompt.setMessage(buttonPrompt.message);
        ((ConfirmPromptWidget)mPrompt).setButtons(new String[] {
                mContext.getResources().getText(R.string.ok_button).toString(),
                mContext.getResources().getText(R.string.cancel_button).toString()
        });
        mPrompt.setPromptDelegate(new ConfirmPromptWidget.ConfirmPromptDelegate() {
            @Override
            public void confirm(int index) {
                result.complete(buttonPrompt.confirm(index));
            }

            @Override
            public void dismiss() {
                result.complete(buttonPrompt.dismiss());
            }
        });
        mPrompt.show(UIWidget.REQUEST_FOCUS, true);

        return result;
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onTextPrompt(@NonNull GeckoSession geckoSession, @NonNull TextPrompt textPrompt) {
        final GeckoResult<PromptResponse> result = new GeckoResult<>();

        mPrompt = new TextPromptWidget(mContext);
        mPrompt.getPlacement().parentHandle = mAttachedWindow.getHandle();
        mPrompt.getPlacement().parentAnchorY = 0.0f;
        mPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.js_prompt_y_distance);
        mPrompt.setTitle(textPrompt.title);
        mPrompt.setMessage(textPrompt.message);
        ((TextPromptWidget)mPrompt).setDefaultText(textPrompt.defaultValue);
        mPrompt.setPromptDelegate(new TextPromptWidget.TextPromptDelegate() {
            @Override
            public void confirm(String message) {
                result.complete(textPrompt.confirm(message));
            }

            @Override
            public void dismiss() {
                result.complete(textPrompt.dismiss());
            }
        });
        mPrompt.show(UIWidget.REQUEST_FOCUS, true);

        return result;
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onAuthPrompt(@NonNull GeckoSession geckoSession, @NonNull AuthPrompt authPrompt) {
        final GeckoResult<PromptResponse> result = new GeckoResult<>();

        mPrompt = new AuthPromptWidget(mContext);
        mPrompt.getPlacement().parentHandle = mAttachedWindow.getHandle();
        mPrompt.getPlacement().parentAnchorY = 0.0f;
        mPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.js_prompt_y_distance);
        mPrompt.setTitle(authPrompt.title);
        mPrompt.setMessage(authPrompt.message);
        ((AuthPromptWidget)mPrompt).setAuthOptions(authPrompt.authOptions);
        mPrompt.setPromptDelegate(new AuthPromptWidget.AuthPromptDelegate() {
            @Override
            public void dismiss() {
                result.complete(authPrompt.dismiss());
            }

            @Override
            public void confirm(String password) {
                result.complete(authPrompt.confirm(password));
            }

            @Override
            public void confirm(String username, String password) {
                result.complete(authPrompt.confirm(username, password));
            }
        });
        mPrompt.show(UIWidget.REQUEST_FOCUS, true);

        return result;
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onChoicePrompt(@NonNull GeckoSession geckoSession, @NonNull ChoicePrompt choicePrompt) {
        final GeckoResult<PromptResponse> result = new GeckoResult<>();

        mPrompt = new ChoicePromptWidget(mContext);
        mPrompt.getPlacement().parentHandle = mAttachedWindow.getHandle();
        mPrompt.getPlacement().parentAnchorY = 0.0f;
        mPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.js_prompt_y_distance);
        mPrompt.setTitle(choicePrompt.title);
        mPrompt.setMessage(choicePrompt.message);
        ((ChoicePromptWidget)mPrompt).setChoices(choicePrompt.choices);
        ((ChoicePromptWidget)mPrompt).setMenuType(choicePrompt.type);
        mPrompt.setPromptDelegate(new ChoicePromptWidget.ChoicePromptDelegate() {
            @Override
            public void confirm(String[] choices) {
                result.complete(choicePrompt.confirm(choices));
            }

            @Override
            public void dismiss() {
                result.complete(choicePrompt.dismiss());
            }
        });
        mPrompt.show(UIWidget.REQUEST_FOCUS, true);

        return result;
    }

    private Observer<List<SitePermission>> mPopUpSiteObserver = sites -> {
        mAllowedPopUpSites = sites;
    };

    private Observer<List<SitePermission>> mSavedLoginExceptionsObserver = sites -> {
        mSavedLoginBlockedSites = sites;
    };

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onPopupPrompt(@NonNull GeckoSession geckoSession, @NonNull PopupPrompt popupPrompt) {
        final GeckoResult<PromptResponse> result = new GeckoResult<>();

        if (!SettingsStore.getInstance(mContext).isPopUpsBlockingEnabled()) {
            result.complete(popupPrompt.confirm(AllowOrDeny.ALLOW));

        } else {
            Session session = mAttachedWindow.getSession();
            if (session != null) {
                final String uri = UrlUtils.getHost(session.getCurrentUri());
                SitePermission site = mAllowedPopUpSites.stream().filter((item) -> UrlUtils.getHost(item.url).equals(uri)).findFirst().orElse(null);
                if (site != null) {
                    result.complete(popupPrompt.confirm(AllowOrDeny.ALLOW));
                    session.setPopUpState(SessionState.POPUP_ALLOWED);
                } else {
                    result.complete(popupPrompt.confirm(AllowOrDeny.DENY));
                    session.setPopUpState(SessionState.POPUP_BLOCKED);
                }

            } else {
                result.complete(popupPrompt.confirm(AllowOrDeny.DENY));
            }
        }

        return result;
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onLoginSave(@NonNull GeckoSession geckoSession, final @NonNull AutocompleteRequest<Autocomplete.LoginSaveOption> autocompleteRequest) {
        final GeckoResult<PromptResponse> result = new GeckoResult<>();

        // We always get at least one item, at the moment only one item is support.
        if (autocompleteRequest.options.length > 0 && SettingsStore.getInstance(mContext).isAutoFillEnabled()) {
            Autocomplete.LoginSaveOption saveOption = autocompleteRequest.options[0];
            boolean originHasException = mSavedLoginBlockedSites.stream().anyMatch(site -> site.url.equals(saveOption.value.origin));
            if (originHasException || !SettingsStore.getInstance(mContext).isLoginAutocompleteEnabled()) {
                result.complete(autocompleteRequest.dismiss());

            } else {
                mSaveLoginPrompt = new SaveLoginPromptWidget(mContext, new SaveLoginPromptWidget.Delegate() {
                    @Override
                    public void dismiss(@NonNull Login login) {
                        result.complete(autocompleteRequest.dismiss());
                        SessionStore.get().addPermissionException(login.getOrigin(), SitePermission.SITE_PERMISSION_AUTOFILL);
                    }

                    @Override
                    public void confirm(@NonNull Login login) {
                        result.complete(autocompleteRequest.confirm(new Autocomplete.LoginSelectOption(GeckoLoginDelegateWrapper.toLoginEntry(login))));
                    }
                });
                mSaveLoginPrompt.setDelegate(() -> result.complete(autocompleteRequest.dismiss()));
                mSaveLoginPrompt.getPlacement().parentHandle = mAttachedWindow.getHandle();
                mSaveLoginPrompt.getPlacement().parentAnchorY = 0.0f;
                mSaveLoginPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.js_prompt_y_distance);
                mSaveLoginPrompt.setLogin(GeckoLoginDelegateWrapper.toLogin(saveOption.value));
                mSaveLoginPrompt.show(UIWidget.REQUEST_FOCUS, true);
            }

        } else {
            result.complete(autocompleteRequest.dismiss());
        }

        return result;
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onLoginSelect(@NonNull GeckoSession geckoSession, final @NonNull AutocompleteRequest<Autocomplete.LoginSelectOption> autocompleteRequest) {
        final GeckoResult<PromptResponse> result = new GeckoResult<>();

        if (autocompleteRequest.options.length > 1 && SettingsStore.getInstance(mContext).isAutoFillEnabled()) {
            List<Login> logins = Arrays.stream(autocompleteRequest.options).map(item -> GeckoLoginDelegateWrapper.toLogin(item.value)).collect(Collectors.toList());
            mSelectLoginPrompt = new SelectLoginPromptWidget(mContext, new SelectLoginPromptWidget.Delegate() {
                @Override
                public void onLoginSelected(@NonNull Login login) {
                    result.complete(autocompleteRequest.confirm(new Autocomplete.LoginSelectOption(GeckoLoginDelegateWrapper.toLoginEntry(login))));
                }

                @Override
                public void onSettingsClicked() {
                    result.complete(autocompleteRequest.dismiss());
                    mWidgetManager.getTray().toggleSettingsDialog(SettingsView.SettingViewType.LOGINS_AND_PASSWORDS);
                }
            }, logins);
            mSelectLoginPrompt.setDelegate(() -> result.complete(autocompleteRequest.dismiss()));
            mSelectLoginPrompt.getPlacement().parentHandle = mAttachedWindow.getHandle();
            mSelectLoginPrompt.getPlacement().parentAnchorY = 0.0f;
            mSelectLoginPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.js_prompt_y_distance);
            mSelectLoginPrompt.show(UIWidget.KEEP_FOCUS, true);

        } else {
            result.complete(autocompleteRequest.dismiss());
        }

        return result;
    }

    @Nullable
    @Override
    public GeckoResult<SlowScriptResponse> onSlowScript(@NonNull GeckoSession aSession, @NonNull String aScriptFileName) {
        final GeckoResult<SlowScriptResponse> result = new GeckoResult<>();
        if (mSlowScriptPrompt == null) {
            mSlowScriptPrompt = new ConfirmPromptWidget(mContext);
            mSlowScriptPrompt.getPlacement().parentHandle = mAttachedWindow.getHandle();
            mSlowScriptPrompt.getPlacement().parentAnchorY = 0.0f;
            mSlowScriptPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.js_prompt_y_distance);
            mSlowScriptPrompt.setTitle(mContext.getResources().getString(R.string.slow_script_dialog_title));
            mSlowScriptPrompt.setMessage(mContext.getResources().getString(R.string.slow_script_dialog_description, aScriptFileName));
            mSlowScriptPrompt.setButtons(new String[]{
                    mContext.getResources().getString(R.string.slow_script_dialog_action_wait),
                    mContext.getResources().getString(R.string.slow_script_dialog_action_stop)
            });
            mSlowScriptPrompt.setPromptDelegate(new ConfirmPromptWidget.ConfirmPromptDelegate() {
                @Override
                public void confirm(int index) {
                    result.complete(index == 0 ? SlowScriptResponse.CONTINUE : SlowScriptResponse.STOP);
                }
                @Override
                public void dismiss() {
                    result.complete(SlowScriptResponse.CONTINUE);
                }
            });
            mSlowScriptPrompt.show(UIWidget.REQUEST_FOCUS, true);
        }

        return result.then(value -> {
            if (mSlowScriptPrompt != null && !mSlowScriptPrompt.isReleased()) {
                mSlowScriptPrompt.releaseWidget();
            }
            mSlowScriptPrompt = null;
            return GeckoResult.fromValue(value);
        });
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onBeforeUnloadPrompt(@NonNull GeckoSession session, @NonNull BeforeUnloadPrompt prompt) {
        final GeckoResult<PromptResponse> result = new GeckoResult<>();

        mPrompt = new ConfirmPromptWidget(mContext);
        mPrompt.getPlacement().parentHandle = mAttachedWindow.getHandle();
        mPrompt.getPlacement().parentAnchorY = 0.0f;
        mPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.js_prompt_y_distance);
        String message = mContext.getString(R.string.before_unload_prompt_message);
        if (!StringUtils.isEmpty(prompt.title)) {
            message = prompt.title;
        }
        mPrompt.setTitle(mContext.getString(R.string.before_unload_prompt_title));
        mPrompt.setMessage(message);
        ((ConfirmPromptWidget)mPrompt).setButtons(new String[] {
                mContext.getResources().getText(R.string.before_unload_prompt_leave).toString(),
                mContext.getResources().getText(R.string.before_unload_prompt_stay).toString()
        });
        mPrompt.setPromptDelegate(new ConfirmPromptWidget.ConfirmPromptDelegate() {
            @Override
            public void confirm(int index) {
                result.complete(prompt.confirm(index == 0 ? AllowOrDeny.ALLOW : AllowOrDeny.DENY));
            }

            @Override
            public void dismiss() {
                result.complete(prompt.dismiss());
            }
        });
        mPrompt.show(UIWidget.REQUEST_FOCUS, true);

        return result;
    }

    public void hideAllPrompts() {
        if (mPrompt != null) {
            mPrompt.hide(UIWidget.REMOVE_WIDGET);
        }
        if (mSlowScriptPrompt != null) {
            mSlowScriptPrompt.hide(UIWidget.REMOVE_WIDGET);
        }
        if (mSaveLoginPrompt != null) {
            mSaveLoginPrompt.hide(UIWidget.REMOVE_WIDGET);
        }
        if (mSelectLoginPrompt != null) {
            mSelectLoginPrompt.hide(UIWidget.REMOVE_WIDGET);
        }
    }

    // WindowWidget.WindowListener

    @Override
    public void onSessionChanged(@NonNull Session aOldSession, @NonNull Session aSession) {
        cleanSession(aOldSession);
        setUpSession(aSession);
    }
}
