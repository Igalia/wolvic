package org.mozilla.vrbrowser.browser;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.AppExecutors;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.db.PopUpSite;
import org.mozilla.vrbrowser.ui.viewmodel.PopUpsViewModel;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.ui.widgets.WindowWidget;
import org.mozilla.vrbrowser.ui.widgets.dialogs.BaseAppDialogWidget;
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
import java.util.NoSuchElementException;
import java.util.Optional;

public class PromptDelegate implements GeckoSession.PromptDelegate {

    private PromptWidget mPrompt;
    private PopUpBlockDialogWidget mPopUpPrompt;
    private Context mContext;
    private WindowWidget mAttachedWindow;
    private List<PopUpSite> mAllowedPopUpSites;
    private PopUpsViewModel mViewModel;
    private AppExecutors mExecutors;

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
        mAttachedWindow.getSessionStack().setPromptDelegate(this);
        mViewModel.getAll().observeForever(mObserver);
    }

    public void detachFromWindow() {
        mAttachedWindow = null;
        mViewModel.getAll().removeObserver(mObserver);
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onAlertPrompt(@NonNull GeckoSession geckoSession, @NonNull AlertPrompt alertPrompt) {
        final GeckoResult<PromptResponse> result = new GeckoResult<>();

        mPrompt = new AlertPromptWidget(mContext);
        mPrompt.getPlacement().parentHandle = mAttachedWindow.getHandle();
        mPrompt.getPlacement().parentAnchorY = 0.0f;
        mPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.base_app_dialog_y_distance);
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
        mPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.base_app_dialog_y_distance);
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
        mPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.base_app_dialog_y_distance);
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
        mPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.base_app_dialog_y_distance);
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
        mPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.base_app_dialog_y_distance);
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
            String uri = mAttachedWindow.getSessionStack().getUriFromSession(geckoSession);
            PopUpRequest request = PopUpRequest.newRequest(uri, popupPrompt, result);
            handlePopUpRequest(request);
        }

        return result;
    }

    static class PopUpRequest {

        public static PopUpRequest newRequest(@NonNull String uri, @NonNull PopupPrompt prompt, @NonNull GeckoResult<PromptResponse> response) {
            PopUpRequest request = new PopUpRequest();
            request.uri = uri;
            request.prompt = prompt;
            request.response = response;

            return request;
        }

        String uri;
        PopupPrompt prompt;
        GeckoResult<PromptResponse> response;
    }

    private LinkedList<PopUpRequest> mPopUpRequests = new LinkedList<>();

    private void handlePopUpRequest(@NonNull PopUpRequest request) {
        if (mPopUpPrompt != null && mPopUpPrompt.isVisible()) {
            mPopUpRequests.add(request);

        } else {
            Optional<PopUpSite> site = mAllowedPopUpSites.stream().filter((item) -> item.url.equals(request.uri)).findFirst();
            if (!site.isPresent()) {
                mPopUpPrompt = new PopUpBlockDialogWidget(mContext);
                mPopUpPrompt.getPlacement().parentHandle = mAttachedWindow.getHandle();
                mPopUpPrompt.getPlacement().parentAnchorY = 0.0f;
                mPopUpPrompt.getPlacement().translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.base_app_dialog_y_distance);
                mPopUpPrompt.setTitle(request.uri);
                mPopUpPrompt.setButtonsDelegate(new BaseAppDialogWidget.Delegate() {
                    @Override
                    public void onButtonClicked(int index) {
                        boolean allowed = index != PopUpBlockDialogWidget.LEFT;
                        boolean askAgain = mPopUpPrompt.askAgain();
                        if (!askAgain) {
                            mAllowedPopUpSites.add(new PopUpSite(request.uri, allowed));
                            mViewModel.insertSite(request.uri, allowed);
                        }

                        if (allowed) {
                            request.response.complete(request.prompt.confirm(AllowOrDeny.ALLOW));

                        } else {
                            request.response.complete(request.prompt.dismiss());
                        }

                        mExecutors.mainThread().execute(() -> {
                            try {
                                PopUpRequest next = mPopUpRequests.pop();
                                handlePopUpRequest(next);

                            } catch (NoSuchElementException ignored) {}
                        });
                    }

                    @Override
                    public void onDismiss() {
                        request.response.complete(request.prompt.dismiss());

                        mExecutors.mainThread().execute(() -> {
                            try {
                                PopUpRequest next = mPopUpRequests.pop();
                                handlePopUpRequest(next);

                            } catch (NoSuchElementException ignored) {}
                        });
                    }
                });
                mPopUpPrompt.show(UIWidget.REQUEST_FOCUS);

            } else {
                if (site.get().allowed) {
                    request.response.complete(request.prompt.confirm(AllowOrDeny.ALLOW));

                } else {
                    request.response.complete(request.prompt.dismiss());
                }
            }

        }
    }

}
