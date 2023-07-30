package com.igalia.wolvic.ui.views.library;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.BuildConfig;
import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.VRBrowserApplication;
import com.igalia.wolvic.databinding.LibraryBinding;
import com.igalia.wolvic.ui.delegates.LibraryNavigationDelegate;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.Windows;

import java.util.concurrent.Executor;

public class LibraryPanel extends FrameLayout {

    private LibraryBinding mBinding;
    protected WidgetManagerDelegate mWidgetManager;
    protected Executor mUIThreadExecutor;
    private WebAppsView mWebAppsView;
    private BookmarksView mBookmarksView;
    private HistoryView mHistoryView;
    private SystemNotificationsView mSystemNotificationsView;
    private LibraryView mCurrentView;
    private @Windows.PanelType int mCurrentPanel;

    public LibraryPanel(@NonNull Context context) {
        super(context);
        initialize();
    }

    public LibraryPanel(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public LibraryPanel(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    protected void initialize() {
        mWidgetManager = ((VRBrowserActivity) getContext());
        mUIThreadExecutor = ((VRBrowserApplication) getContext().getApplicationContext()).getExecutors().mainThread();

        mWebAppsView = new WebAppsView(getContext(), this);
        mBookmarksView = new BookmarksView(getContext(), this);
        mHistoryView = new HistoryView(getContext(), this);
        mSystemNotificationsView = new SystemNotificationsView(getContext(), this);
        mCurrentPanel = Windows.BOOKMARKS;

        updateUI();
    }

    @SuppressLint("ClickableViewAccessibility")
    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.library, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity) getContext());
        mBinding.setSupportsSystemNotifications(BuildConfig.SUPPORTS_SYSTEM_NOTIFICATIONS);
        mBinding.setDelegate(new LibraryNavigationDelegate() {
            @Override
            public void onClose(@NonNull View view) {
                requestFocus();
                mWidgetManager.getFocusedWindow().hidePanel();
            }

            @Override
            public void onBack(@NonNull View view) {
                requestFocus();
                mCurrentView.onBack();
                mBinding.setCanGoBack(mCurrentView.canGoBack());
            }

            @Override
            public void onButtonClick(@NonNull View view) {
                requestFocus();
                selectTab(view);
            }
        });
        mBinding.executePendingBindings();

        selectPanel(mCurrentPanel);

        setOnTouchListener((v, event) -> {
            v.requestFocusFromTouch();
            return false;
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (mBinding != null) {
            mBinding.tabcontent.removeAllViews();
        }

        mHistoryView.updateUI();
        mBookmarksView.updateUI();
        mWebAppsView.updateUI();
        mSystemNotificationsView.updateUI();

        updateUI();
    }

    public void onShow() {
        if (mCurrentView != null) {
            mCurrentView.onShow();
        }
    }

    public void onHide() {
        if (mCurrentView != null) {
            mCurrentView.onHide();
        }
    }

    public boolean onBack() {
        if (mCurrentView != null) {
            return mCurrentView.onBack();
        }

        return false;
    }

    public void onDestroy() {
        mBookmarksView.onDestroy();
        mHistoryView.onDestroy();
        mWebAppsView.onDestroy();
        mSystemNotificationsView.onDestroy();
    }

    public @Windows.PanelType int getSelectedPanelType() {
        if (mCurrentView == mBookmarksView) {
            return Windows.BOOKMARKS;

        } else if (mCurrentView == mWebAppsView) {
            return Windows.WEB_APPS;

        } else if (mCurrentView == mHistoryView) {
            return Windows.HISTORY;

        } else if (mCurrentView == mSystemNotificationsView) {
            return Windows.NOTIFICATIONS;

        } else {
            return Windows.NONE;
        }
    }

    private void selectTab(@NonNull View view) {
        mBinding.tabcontent.removeAllViews();

        mBinding.bookmarks.setActiveMode(false);
        mBinding.webApps.setActiveMode(false);
        mBinding.history.setActiveMode(false);
        mBinding.notifications.setActiveMode(false);
        if(view.getId() == R.id.bookmarks){
            selectBookmarks();

        } else if(view.getId() == R.id.history){
            selectHistory();

        } else if (view.getId() == R.id.notifications) {
            selectNotifications();

        } else if (view.getId() == R.id.web_apps) {
            selectWebApps();
        }

        mBinding.setCanGoBack(mCurrentView.canGoBack());
        mCurrentView.onShow();
    }

    public void selectPanel(@Windows.PanelType int panelType) {
        mCurrentPanel = panelType;

        if (panelType == Windows.NONE) {
            panelType = getSelectedPanelType();
        }
        switch (panelType) {
            case Windows.NONE:
            case Windows.BOOKMARKS:
                selectTab(mBinding.bookmarks);
                break;
            case Windows.WEB_APPS:
                selectTab(mBinding.webApps);
                break;
            case Windows.HISTORY:
                selectTab(mBinding.history);
                break;
            case Windows.NOTIFICATIONS:
                selectTab(mBinding.notifications);
                break;
        }
    }

    private void selectBookmarks() {
        mCurrentView = mBookmarksView;
        mBinding.bookmarks.setActiveMode(true);
        mBinding.tabcontent.addView(mBookmarksView);
    }

    private void selectWebApps() {
        mCurrentView = mWebAppsView;
        mBinding.webApps.setActiveMode(true);
        mBinding.tabcontent.addView(mWebAppsView);
    }

    private void selectHistory() {
        mCurrentView = mHistoryView;
        mBinding.history.setActiveMode(true);
        mBinding.tabcontent.addView(mHistoryView);
    }

    private void selectNotifications() {
        mCurrentView = mSystemNotificationsView;
        mBinding.notifications.setActiveMode(true);
        mBinding.tabcontent.addView(mSystemNotificationsView);
    }

    public void onViewUpdated(@NonNull String title) {
        if (mBinding != null) {
            mBinding.title.setText(title);
            mBinding.setCanGoBack(mCurrentView.canGoBack());
        }
    }
}
