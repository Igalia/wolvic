package com.igalia.wolvic.ui.widgets;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.ObservableBoolean;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.browser.Accounts;
import com.igalia.wolvic.browser.SessionChangeListener;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.ui.viewmodel.WindowViewModel;
import com.igalia.wolvic.utils.SystemUtils;

import mozilla.components.concept.sync.AccountObserver;
import mozilla.components.concept.sync.AuthFlowError;
import mozilla.components.concept.sync.AuthType;
import mozilla.components.concept.sync.OAuthAccount;
import mozilla.components.concept.sync.Profile;

public abstract class AbstractTabsBar extends UIWidget implements SessionChangeListener, WidgetManagerDelegate.UpdateListener, AccountObserver {

    protected final String LOGTAG = SystemUtils.createLogtag(this.getClass());

    protected boolean mPrivateMode;
    protected WindowWidget mAttachedWindow;
    protected WindowViewModel mWindowViewModel;
    protected MutableLiveData<Boolean> mSyncAccountEnabled = new MutableLiveData<>(false);

    private Accounts mAccounts;

    public AbstractTabsBar(Context aContext) {
        super(aContext);

        SessionStore.get().addSessionChangeListener(this);
        mAccounts = mWidgetManager.getServicesProvider().getAccounts();
    }

    public abstract void updateWidgetPlacement();

    @Override
    public void attachToWindow(@NonNull WindowWidget window) {
        if (mAttachedWindow == window) {
            return;
        }
        detachFromWindow();
        mAttachedWindow = window;

        mPrivateMode = mAttachedWindow.getSession() != null && mAttachedWindow.getSession().isPrivateMode();
        mWidgetManager.addUpdateListener(this);
        mWindowViewModel = new ViewModelProvider(
                (VRBrowserActivity) getContext(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(((VRBrowserActivity) getContext()).getApplication()))
                .get(String.valueOf(mAttachedWindow.hashCode()), WindowViewModel.class);
        mWindowViewModel.getIsTabsBarVisible().observe((VRBrowserActivity) getContext(), mIsTabsBarVisibleObserver);

        mAccounts.addAccountListener(this);
        mSyncAccountEnabled.postValue(mAccounts.isSignedIn());

        updateWidgetPlacement();
        refreshTabs();
    }

    @Override
    public void detachFromWindow() {
        if (mWindowViewModel != null) {
            mWindowViewModel.getIsTabsBarVisible().removeObserver(mIsTabsBarVisibleObserver);
            mWindowViewModel = null;
        }
        mAccounts.removeAccountListener(this);
        mAttachedWindow = null;
    }

    Observer<? super ObservableBoolean> mIsTabsBarVisibleObserver = isTabsVisible -> {
        if (isTabsVisible.get()) {
            show(CLEAR_FOCUS);
        } else {
            hide(KEEP_WIDGET);
        }
    };

    @Override
    public void show(@ShowFlags int aShowFlags) {
        updateWidgetPlacement();
        mWidgetPlacement.visible = true;
        mWidgetManager.updateWidget(this);
    }

    @Override
    public void hide(@HideFlags int aHideFlag) {
        mWidgetPlacement.visible = false;
        mWidgetManager.updateWidget(this);
    }

    @Override
    public void releaseWidget() {
        if (mWidgetManager != null) {
            mWidgetManager.removeUpdateListener(this);
            mWindowViewModel.getIsTabsBarVisible().removeObserver(mIsTabsBarVisibleObserver);
        }
        SessionStore.get().removeSessionChangeListener(this);
        super.releaseWidget();
    }

    // WidgetManagerDelegate.UpdateListener
    @Override
    public void onWidgetUpdate(Widget aWidget) {
        if (aWidget == mAttachedWindow && !mAttachedWindow.isResizing()) {
            updateWidgetPlacement();
        }
    }

    // TODO Use more fine-grained updates.
    public abstract void refreshTabs();

    // SessionChangeListener

    @Override
    public void onSessionAdded(Session aSession) {
        refreshTabs();
    }

    @Override
    public void onSessionOpened(Session aSession) {
        refreshTabs();
    }

    @Override
    public void onSessionClosed(Session aSession) {
        refreshTabs();
    }

    @Override
    public void onSessionRemoved(String aId) {
        refreshTabs();
    }

    @Override
    public void onSessionStateChanged(Session aSession, boolean aActive) {
        refreshTabs();
    }

    @Override
    public void onCurrentSessionChange(WSession aOldSession, WSession aSession) {
        refreshTabs();
    }

    @Override
    public void onStackSession(Session aSession) {
        refreshTabs();
    }

    @Override
    public void onUnstackSession(Session aSession, Session aParent) {
        refreshTabs();
    }

    // AccountObserver

    @Override
    public void onLoggedOut() {
        mSyncAccountEnabled.postValue(mAccounts.isSignedIn());
    }

    @Override
    public void onAuthenticated(@NonNull OAuthAccount oAuthAccount, @NonNull AuthType authType) {
        mSyncAccountEnabled.postValue(mAccounts.isSignedIn());
    }


    @Override
    public void onAuthenticationProblems() {
    }

    @Override
    public void onReady(@Nullable OAuthAccount oAuthAccount) {
    }

    @Override
    public void onProfileUpdated(@NonNull Profile profile) {
    }

    @Override
    public void onFlowError(@NonNull AuthFlowError authFlowError) {
    }
}
