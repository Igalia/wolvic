package com.igalia.wolvic.browser;

import android.app.Application;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.api.WAllowOrDeny;
import com.igalia.wolvic.browser.api.WAutocomplete;
import com.igalia.wolvic.browser.api.WResult;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.api.WSlowScriptResponse;
import com.igalia.wolvic.browser.components.LoginDelegateWrapper;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.browser.engine.SessionState;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.db.SitePermission;
import com.igalia.wolvic.ui.viewmodel.SitePermissionViewModel;
import com.igalia.wolvic.ui.widgets.UIWidget;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.ui.widgets.WindowWidget;
import com.igalia.wolvic.ui.widgets.prompts.AlertPromptWidget;
import com.igalia.wolvic.ui.widgets.prompts.AuthPromptWidget;
import com.igalia.wolvic.ui.widgets.prompts.ChoicePromptWidget;
import com.igalia.wolvic.ui.widgets.prompts.ColorPromptWidget;
import com.igalia.wolvic.ui.widgets.prompts.ConfirmPromptWidget;
import com.igalia.wolvic.ui.widgets.prompts.DateTimePromptWidget;
import com.igalia.wolvic.ui.widgets.prompts.FilePromptWidget;
import com.igalia.wolvic.ui.widgets.prompts.PromptWidget;
import com.igalia.wolvic.ui.widgets.prompts.SaveLoginPromptWidget;
import com.igalia.wolvic.ui.widgets.prompts.SelectLoginPromptWidget;
import com.igalia.wolvic.ui.widgets.prompts.TextPromptWidget;
import com.igalia.wolvic.ui.widgets.settings.SettingsView;
import com.igalia.wolvic.utils.StringUtils;
import com.igalia.wolvic.utils.UrlUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import mozilla.components.concept.storage.Login;

public class PromptDelegate implements
        WSession.PromptDelegate,
        WindowWidget.WindowListener,
        WSession.NavigationDelegate,
        WSession.ContentDelegate {

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
        mSaveLoginPrompt = null;
        mSelectLoginPrompt = null;
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
    public WResult<PromptResponse> onFilePrompt(@NonNull final WSession session, @NonNull final WSession.PromptDelegate.FilePrompt prompt) {
        final WResult<PromptResponse> result = WResult.create();

        FilePromptWidget filePromptWidget = new FilePromptWidget(mContext);
        filePromptWidget.setIsMultipleSelection(prompt.type() == FilePrompt.Type.MULTIPLE);
        filePromptWidget.setMimeTypes(prompt.mimeTypes());
        mPrompt = filePromptWidget;
        mPrompt.setTitle(prompt.title());
        mPrompt.setPromptDelegate(new FilePromptWidget.FilePromptDelegate() {
            @Override
            public void confirm(@NonNull Uri[] uris) {
                result.complete(prompt.confirm(mContext, uris));
            }

            @Override
            public void dismiss() {
                result.complete(prompt.dismiss());
            }
        });

        mPrompt.getPlacement().parentHandle = mAttachedWindow.getHandle();
        mPrompt.getPlacement().parentAnchorY = 0.0f;
        mPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.js_prompt_y_distance);
        mPrompt.show(UIWidget.REQUEST_FOCUS, true);

        return result;
    }

    @Nullable
    @Override
    public WResult<PromptResponse> onAlertPrompt(@NonNull WSession session, @NonNull AlertPrompt alertPrompt) {
        final WResult<PromptResponse> result = WResult.create();

        mPrompt = new AlertPromptWidget(mContext);
        mPrompt.getPlacement().parentHandle = mAttachedWindow.getHandle();
        mPrompt.getPlacement().parentAnchorY = 0.0f;
        mPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.js_prompt_y_distance);
        mPrompt.setTitle(alertPrompt.title());
        mPrompt.setMessage(alertPrompt.message());
        mPrompt.setPromptDelegate(() -> result.complete(alertPrompt.dismiss()));
        mPrompt.show(UIWidget.REQUEST_FOCUS, true);

        return result;
    }

    @Nullable
    @Override
    public WResult<PromptResponse> onButtonPrompt(@NonNull WSession session, @NonNull ButtonPrompt buttonPrompt) {
        final WResult<PromptResponse> result = WResult.create();

        mPrompt = new ConfirmPromptWidget(mContext);
        mPrompt.getPlacement().parentHandle = mAttachedWindow.getHandle();
        mPrompt.getPlacement().parentAnchorY = 0.0f;
        mPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.js_prompt_y_distance);
        mPrompt.setTitle(buttonPrompt.title());
        mPrompt.setMessage(buttonPrompt.message());
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
    public WResult<PromptResponse> onTextPrompt(@NonNull WSession session, @NonNull TextPrompt textPrompt) {
        final WResult<PromptResponse> result = WResult.create();

        mPrompt = new TextPromptWidget(mContext);
        mPrompt.getPlacement().parentHandle = mAttachedWindow.getHandle();
        mPrompt.getPlacement().parentAnchorY = 0.0f;
        mPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.js_prompt_y_distance);
        mPrompt.setTitle(textPrompt.title());
        mPrompt.setMessage(textPrompt.message());
        ((TextPromptWidget)mPrompt).setDefaultText(textPrompt.defaultValue());
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
    public WResult<PromptResponse> onAuthPrompt(@NonNull WSession session, @NonNull AuthPrompt authPrompt) {
        final WResult<PromptResponse> result = WResult.create();

        mPrompt = new AuthPromptWidget(mContext);
        mPrompt.getPlacement().parentHandle = mAttachedWindow.getHandle();
        mPrompt.getPlacement().parentAnchorY = 0.0f;
        mPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.js_prompt_y_distance);
        mPrompt.setTitle(authPrompt.title());
        mPrompt.setMessage(authPrompt.message());
        ((AuthPromptWidget)mPrompt).setAuthOptions(authPrompt.authOptions());
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
    public WResult<PromptResponse> onChoicePrompt(@NonNull WSession session, @NonNull ChoicePrompt choicePrompt) {
        final WResult<PromptResponse> result = WResult.create();

        mPrompt = new ChoicePromptWidget(mContext);
        mPrompt.getPlacement().parentHandle = mAttachedWindow.getHandle();
        mPrompt.getPlacement().parentAnchorY = 0.0f;
        mPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.js_prompt_y_distance);
        mPrompt.setTitle(choicePrompt.title());
        mPrompt.setMessage(choicePrompt.message());
        ((ChoicePromptWidget)mPrompt).setChoices(choicePrompt.choices());
        ((ChoicePromptWidget)mPrompt).setMenuType(choicePrompt.type());
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

    @Nullable
    @Override
    public WResult<PromptResponse> onColorPrompt(@NonNull WSession session, @NonNull ColorPrompt prompt) {
        final WResult<PromptResponse> result = WResult.create();

        mPrompt = new ColorPromptWidget(mContext);
        mPrompt.getPlacement().parentHandle = mAttachedWindow.getHandle();
        mPrompt.getPlacement().parentAnchorY = 0.0f;
        mPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.js_prompt_y_distance);
        mPrompt.setTitle(prompt.title());
        mPrompt.setPromptDelegate(new ColorPromptWidget.ColorPromptDelegate() {
            @Override
            public void confirm(@NonNull final String color) {
                result.complete(prompt.confirm(color));
            }

            @Override
            public void dismiss() {
                result.complete(prompt.dismiss());
            }
        });
        mPrompt.show(UIWidget.REQUEST_FOCUS, true);

        return result;
    }

    @Nullable
    @Override
    public WResult<PromptResponse> onDateTimePrompt(@NonNull WSession session, @NonNull DateTimePrompt prompt) {
        final WResult<PromptResponse> result = WResult.create();

        mPrompt = new DateTimePromptWidget(mContext, prompt);
        mPrompt.getPlacement().parentHandle = mAttachedWindow.getHandle();
        mPrompt.getPlacement().parentAnchorY = 0.0f;
        mPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.js_prompt_y_distance);
        mPrompt.setTitle(prompt.title());
        mPrompt.setPromptDelegate(new DateTimePromptWidget.DateTimePromptDelegate() {
            @Override
            public void confirm(@NonNull final String dateTime) {
                result.complete(prompt.confirm(dateTime));
            }
            @Override
            public void dismiss() {
                result.complete(prompt.dismiss());
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
    public WResult<PromptResponse> onPopupPrompt(@NonNull WSession aSession, @NonNull PopupPrompt popupPrompt) {
        final WResult<PromptResponse> result = WResult.create();

        if (!SettingsStore.getInstance(mContext).isPopUpsBlockingEnabled()) {
            result.complete(popupPrompt.confirm(WAllowOrDeny.ALLOW));

        } else {
            Session session = mAttachedWindow.getSession();
            if (session != null) {
                final String uri = UrlUtils.getHost(session.getCurrentUri());
                SitePermission site = mAllowedPopUpSites.stream().filter((item) -> UrlUtils.getHost(item.url).equals(uri)).findFirst().orElse(null);
                if (site != null) {
                    result.complete(popupPrompt.confirm(WAllowOrDeny.ALLOW));
                    session.setPopUpState(SessionState.POPUP_ALLOWED);
                } else {
                    result.complete(popupPrompt.confirm(WAllowOrDeny.DENY));
                    session.setPopUpState(SessionState.POPUP_BLOCKED);
                }

            } else {
                result.complete(popupPrompt.confirm(WAllowOrDeny.DENY));
            }
        }

        return result;
    }

    @Nullable
    @Override
    public WResult<PromptResponse> onSharePrompt(@NonNull WSession session, @NonNull SharePrompt prompt) {
        // TODO implement share request
        final WResult<PromptResponse> result = WResult.create();
        result.cancel();
        return result;
    }

    @Nullable
    @Override
    public WResult<PromptResponse> onLoginSave(@NonNull WSession session, final @NonNull AutocompleteRequest<WAutocomplete.LoginSaveOption> autocompleteRequest) {
        final WResult<PromptResponse> result = WResult.create();

        // We always get at least one item, at the moment only one item is support.
        if (autocompleteRequest.options().length > 0 && SettingsStore.getInstance(mContext).isLoginAutocompleteEnabled()) {
            WAutocomplete.LoginSaveOption saveOption = autocompleteRequest.options()[0];
            boolean originHasException = mSavedLoginBlockedSites.stream().anyMatch(site -> site.url.equals(saveOption.value.origin));
            if (originHasException || !SettingsStore.getInstance(mContext).isLoginAutocompleteEnabled()) {
                result.complete(autocompleteRequest.dismiss());

            } else {
                if (mSaveLoginPrompt == null) {
                    mSaveLoginPrompt = new SaveLoginPromptWidget(mContext);
                }
                mSaveLoginPrompt.setPromptDelegate(new SaveLoginPromptWidget.Delegate() {
                    @Override
                    public void dismiss(@NonNull Login login) {
                        result.complete(autocompleteRequest.dismiss());
                        SessionStore.get().addPermissionException(login.getOrigin(), SitePermission.SITE_PERMISSION_AUTOFILL);
                    }

                    @Override
                    public void confirm(@NonNull Login login) {
                        result.complete(autocompleteRequest.confirm(new WAutocomplete.LoginSaveOption(LoginDelegateWrapper.toLoginEntry(login))));
                    }
                });
                mSaveLoginPrompt.setDelegate(() -> result.complete(autocompleteRequest.dismiss()));
                mSaveLoginPrompt.getPlacement().parentHandle = mAttachedWindow.getHandle();
                mSaveLoginPrompt.getPlacement().parentAnchorY = 0.0f;
                mSaveLoginPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.js_prompt_y_distance);
                mSaveLoginPrompt.setLogin(LoginDelegateWrapper.toLogin(saveOption.value));
                mSaveLoginPrompt.show(UIWidget.REQUEST_FOCUS, true);
            }

        } else {
            result.complete(autocompleteRequest.dismiss());
        }

        return result;
    }

    @Nullable
    @Override
    public WResult<PromptResponse> onLoginSelect(@NonNull WSession session, final @NonNull AutocompleteRequest<WAutocomplete.LoginSelectOption> autocompleteRequest) {
        final WResult<PromptResponse> result = WResult.create();

        if (autocompleteRequest.options().length > 0 && SettingsStore.getInstance(mContext).isAutoFillEnabled()) {
            List<Login> logins = Arrays.stream(autocompleteRequest.options()).map(item -> LoginDelegateWrapper.toLogin(item.value)).collect(Collectors.toList());
            if (mSelectLoginPrompt == null) {
                mSelectLoginPrompt = new SelectLoginPromptWidget(mContext);
            }
            mSelectLoginPrompt.setPromptDelegate(new SelectLoginPromptWidget.Delegate() {
                @Override
                public void onLoginSelected(@NonNull Login login) {
                    result.complete(autocompleteRequest.confirm(new WAutocomplete.LoginSelectOption(LoginDelegateWrapper.toLoginEntry(login))));
                }

                @Override
                public void onSettingsClicked() {
                    result.complete(autocompleteRequest.dismiss());
                    mWidgetManager.getTray().toggleSettingsDialog(SettingsView.SettingViewType.LOGINS_AND_PASSWORDS);
                }
            });
            mSelectLoginPrompt.setItems(logins);
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
    public WResult<WSlowScriptResponse> onSlowScript(@NonNull WSession aSession, @NonNull String aScriptFileName) {
        final WResult<WSlowScriptResponse> result = WResult.create();
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
                    result.complete(index == 0 ? WSlowScriptResponse.CONTINUE : WSlowScriptResponse.STOP);
                }
                @Override
                public void dismiss() {
                    result.complete(WSlowScriptResponse.CONTINUE);
                }
            });
            mSlowScriptPrompt.show(UIWidget.REQUEST_FOCUS, true);
        }

        return result.then(value -> {
            if (mSlowScriptPrompt != null && !mSlowScriptPrompt.isReleased()) {
                mSlowScriptPrompt.releaseWidget();
            }
            mSlowScriptPrompt = null;
            return WResult.fromValue(value);
        });
    }

    @Nullable
    @Override
    public WResult<PromptResponse> onBeforeUnloadPrompt(@NonNull WSession session, @NonNull BeforeUnloadPrompt prompt) {
        final WResult<PromptResponse> result = WResult.create();

        mPrompt = new ConfirmPromptWidget(mContext);
        mPrompt.getPlacement().parentHandle = mAttachedWindow.getHandle();
        mPrompt.getPlacement().parentAnchorY = 0.0f;
        mPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.js_prompt_y_distance);
        String message = mContext.getString(R.string.before_unload_prompt_message);
        if (!StringUtils.isEmpty(prompt.title())) {
            message = prompt.title();
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
                result.complete(prompt.confirm(index == 0 ? WAllowOrDeny.ALLOW : WAllowOrDeny.DENY));
            }

            @Override
            public void dismiss() {
                result.complete(prompt.dismiss());
            }
        });
        mPrompt.show(UIWidget.REQUEST_FOCUS, true);

        return result;
    }

    @Nullable
    @Override
    public WResult<PromptResponse> onRepostConfirmPrompt(@NonNull WSession session, @NonNull RepostConfirmPrompt prompt) {
        final WResult<PromptResponse> result = WResult.create();

        mPrompt = new ConfirmPromptWidget(mContext);
        mPrompt.getPlacement().parentHandle = mAttachedWindow.getHandle();
        mPrompt.getPlacement().parentAnchorY = 0.0f;
        mPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.js_prompt_y_distance);
        mPrompt.setTitle(mContext.getString(R.string.repost_confirm_title));
        mPrompt.setMessage(mContext.getString(R.string.repost_confirm_message));
        ((ConfirmPromptWidget)mPrompt).setButtons(new String[] {
                mContext.getResources().getText(R.string.repost_confirm_continue).toString(),
                mContext.getResources().getText(R.string.cancel_button).toString()
        });
        mPrompt.setPromptDelegate(new ConfirmPromptWidget.ConfirmPromptDelegate() {
            @Override
            public void confirm(int index) {
                result.complete(prompt.confirm(index == 0 ? WAllowOrDeny.ALLOW : WAllowOrDeny.DENY));
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