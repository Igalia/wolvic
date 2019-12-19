package org.mozilla.vrbrowser.browser;

import android.app.Application;
import android.content.Context;
import android.util.Pair;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.SlowScriptResponse;
import org.mozilla.vrbrowser.AppExecutors;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.db.PopUpSite;
import org.mozilla.vrbrowser.ui.viewmodel.PopUpsViewModel;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.ui.widgets.WindowWidget;
import org.mozilla.vrbrowser.ui.widgets.dialogs.PopUpBlockDialogWidget;
import org.mozilla.vrbrowser.ui.widgets.prompts.AlertPromptWidget;
import org.mozilla.vrbrowser.ui.widgets.prompts.AuthPromptWidget;
import org.mozilla.vrbrowser.ui.widgets.prompts.ChoicePromptWidget;
import org.mozilla.vrbrowser.ui.widgets.prompts.ConfirmPromptWidget;
import org.mozilla.vrbrowser.ui.widgets.prompts.PromptWidget;
import org.mozilla.vrbrowser.ui.widgets.prompts.TextPromptWidget;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class PromptDelegate implements
        GeckoSession.PromptDelegate,
        WindowWidget.WindowListener,
        GeckoSession.NavigationDelegate,
        GeckoSession.ContentDelegate {

    public interface PopUpDelegate {
        void onPopUpAvailable();
        void onPopUpsCleared();
    }

    private PromptWidget mPrompt;
    private PopUpBlockDialogWidget mPopUpPrompt;
    private ConfirmPromptWidget mSlowScriptPrompt;
    private Context mContext;
    private WindowWidget mAttachedWindow;
    private List<PopUpSite> mAllowedPopUpSites;
    private PopUpsViewModel mViewModel;
    private AppExecutors mExecutors;
    private PopUpDelegate mPopupDelegate;

    public PromptDelegate(@NonNull Context context) {
        mContext = context;
        mExecutors = ((VRBrowserApplication)context.getApplicationContext()).getExecutors();
        mViewModel = new PopUpsViewModel(((Application)context.getApplicationContext()));
        mAllowedPopUpSites = new ArrayList<>();
    }

    public void attachToWindow(@NonNull WindowWidget window) {
        if (window == mAttachedWindow) {
            return;
        }
        detachFromWindow();

        mAttachedWindow = window;
        mAttachedWindow.addWindowListener(this);
        mViewModel.getAll().observeForever(mObserver);

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
        mViewModel.getAll().removeObserver(mObserver);

        clearPopUps();
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
        mPopUpRequests.remove(aSession.hashCode());
    }

    public void setPopupDelegate(@Nullable PopUpDelegate delegate) {
        mPopupDelegate = delegate;
    }

    public void clearPopUps() {
        mPopUpRequests.clear();

        if (mPopupDelegate != null) {
            mPopupDelegate.onPopUpsCleared();
        }
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
        mPrompt.show(UIWidget.REQUEST_FOCUS);

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
        mPrompt.show(UIWidget.REQUEST_FOCUS);

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
        mPrompt.show(UIWidget.REQUEST_FOCUS);

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
        mPrompt.show(UIWidget.REQUEST_FOCUS);

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
        mPrompt.show(UIWidget.REQUEST_FOCUS);

        return result;
    }

    private Observer<List<PopUpSite>> mObserver = popUpSites -> {
        mAllowedPopUpSites = popUpSites;
    };

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onPopupPrompt(@NonNull GeckoSession geckoSession, @NonNull PopupPrompt popupPrompt) {
        final GeckoResult<PromptResponse> result = new GeckoResult<>();

        if (!SettingsStore.getInstance(mContext).isPopUpsBlockingEnabled()) {
            result.complete(popupPrompt.confirm(AllowOrDeny.ALLOW));

        } else {
            final int sessionId = geckoSession.hashCode();
            final String uri = mAttachedWindow.getSession().getCurrentUri();

            Optional<PopUpSite> site = mAllowedPopUpSites.stream().filter((item) -> item.url.equals(uri)).findFirst();
            if (site.isPresent()) {
                mAttachedWindow.postDelayed(() -> {
                    if (site.get().allowed) {
                        result.complete(popupPrompt.confirm(AllowOrDeny.ALLOW));

                    } else {
                        result.complete(popupPrompt.dismiss());
                    }
                }, 500);

            } else {
                PopUpRequest request = PopUpRequest.newRequest(popupPrompt, result, sessionId);
                Pair<String, LinkedList<PopUpRequest>> domainRequestList = mPopUpRequests.get(sessionId);
                if (domainRequestList == null) {
                    LinkedList<PopUpRequest> requestList = new LinkedList<>();
                    domainRequestList = new Pair<>(uri, requestList);
                    mPopUpRequests.put(sessionId, domainRequestList);
                }
                domainRequestList.second.add(request);

                if (mPopupDelegate != null) {
                    mPopupDelegate.onPopUpAvailable();
                }
            }
        }

        return result;
    }

    static class PopUpRequest {

        public static PopUpRequest newRequest(@NonNull PopupPrompt prompt, @NonNull GeckoResult<PromptResponse> response, int sessionId) {
            PopUpRequest request = new PopUpRequest();
            request.prompt = prompt;
            request.response = response;
            request.sessionId = sessionId;

            return request;
        }

        PopupPrompt prompt;
        GeckoResult<PromptResponse> response;
        int sessionId;
    }

    private SparseArray<Pair<String, LinkedList<PopUpRequest>>> mPopUpRequests = new SparseArray<>();

    public void showPopUps(GeckoSession session) {
        if (session == null) {
            return;
        }
        Pair<String, LinkedList<PopUpRequest>> requests = mPopUpRequests.get(session.hashCode());
        if (requests != null && !requests.second.isEmpty()) {
            showPopUp(session.hashCode(), requests);
        }
    }

    public boolean hasPendingPopUps(GeckoSession session) {
        if (session != null) {
            Pair<String, LinkedList<PopUpRequest>> requests = mPopUpRequests.get(session.hashCode());
            if (requests != null) {
                return !requests.second.isEmpty();
            }
        }

        return false;
    }

    private void showPopUp(int sessionId, @NonNull Pair<String, LinkedList<PopUpRequest>> requests) {
        String uri = requests.first;
        Optional<PopUpSite> site = mAllowedPopUpSites.stream().filter((item) -> item.url.equals(uri)).findFirst();
        if (!site.isPresent()) {
            mPopUpPrompt = new PopUpBlockDialogWidget(mContext);
            mPopUpPrompt.setButtonsDelegate(index -> {
                boolean allowed = index != PopUpBlockDialogWidget.NEGATIVE;
                boolean askAgain = mPopUpPrompt.askAgain();
                if (allowed && !askAgain) {
                    mAllowedPopUpSites.add(new PopUpSite(uri, allowed));
                    mViewModel.insertSite(uri, allowed);
                }

                if (allowed) {
                    requests.second.forEach((request) -> {
                        request.response.complete(request.prompt.confirm(AllowOrDeny.ALLOW));
                    });

                    mPopUpRequests.remove(sessionId);

                    mExecutors.mainThread().execute(() -> {
                        if (mPopupDelegate != null) {
                            mPopupDelegate.onPopUpsCleared();
                        }
                    });

                } else {
                    mExecutors.mainThread().execute(() -> {
                        if (mPopupDelegate != null) {
                            mPopupDelegate.onPopUpAvailable();
                        }
                    });
                }

                mPopUpPrompt.hide(UIWidget.REMOVE_WIDGET);
            });
            mPopUpPrompt.setDelegate(() -> mExecutors.mainThread().execute(() -> {
                if (mPopupDelegate != null) {
                    mPopupDelegate.onPopUpAvailable();
                }
            }));
            mPopUpPrompt.show(UIWidget.REQUEST_FOCUS);

        } else {
            requests.second.forEach((request) -> {
                if (site.get().allowed) {
                    request.response.complete(request.prompt.confirm(AllowOrDeny.ALLOW));

                } else {
                    request.response.complete(request.prompt.dismiss());
                }
            });
        }
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
            mSlowScriptPrompt.show(UIWidget.REQUEST_FOCUS);
        }

        return result.then(value -> {
            if (mSlowScriptPrompt != null && !mSlowScriptPrompt.isReleased()) {
                mSlowScriptPrompt.releaseWidget();
            }
            mSlowScriptPrompt = null;
            return GeckoResult.fromValue(value);
        });
    }

    // WindowWidget.WindowListener

    @Override
    public void onSessionChanged(@NonNull Session aOldSession, @NonNull Session aSession) {
        cleanSession(aOldSession);
        setUpSession(aSession);
    }

    // NavigationDelegate

    @Override
    public void onLocationChange(@NonNull GeckoSession geckoSession, @Nullable String s) {
        clearPopUps();
    }
}
