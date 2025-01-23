package com.igalia.wolvic.ui.views.library;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.BuildConfig;
import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.VRBrowserApplication;
import com.igalia.wolvic.addons.views.AddonsView;
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
    private DownloadsView mDownloadsView;
    private AddonsView mAddonsView;
    private SystemNotificationsView mSystemNotificationsView;
    private LibraryView mCurrentView;
    private Windows.ContentType mCurrentPanel;
    private Controller mController;

    public interface Controller {
        void setPanelContent(Windows.ContentType contentType);
    }

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
        mDownloadsView = new DownloadsView(getContext(), this);
        mAddonsView = new AddonsView(getContext(), this);
        mSystemNotificationsView = new SystemNotificationsView(getContext(), this);
        mCurrentPanel = Windows.ContentType.BOOKMARKS;

        updateUI();
    }

    @SuppressLint("ClickableViewAccessibility")
    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.library, this, true);

        mBinding.searchBar.setIconifiedByDefault(false);
        mBinding.searchBar.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                mCurrentView.updateSearchFilter(s.toLowerCase());
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                mCurrentView.updateSearchFilter(s.toLowerCase());
                return true;
            }
        });
        mBinding.searchBar.setOnCloseListener(() -> {
            mCurrentView.updateSearchFilter("");
            return true;
        });
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
            public void onButtonClick(Windows.ContentType contentType) {
                requestFocus();
                if (mController != null) {
                    mController.setPanelContent(contentType);
                }
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
        mDownloadsView.updateUI();
        mAddonsView.updateUI();
        mSystemNotificationsView.updateUI();

        updateUI();
    }

    public void onShow() {
        if (mCurrentView != null) {
            mCurrentView.onShow();
            mBinding.searchBar.setQuery("", false);
            mBinding.searchBar.clearFocus();
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
        mDownloadsView.onDestroy();
        mAddonsView.onDestroy();
        mSystemNotificationsView.onDestroy();
    }

    public void setController(Controller controller) {
        mController = controller;
    }

    @NonNull
    public Windows.ContentType getSelectedPanelType() {
        if (mCurrentView == mBookmarksView) {
            return Windows.ContentType.BOOKMARKS;

        } else if (mCurrentView == mWebAppsView) {
            return Windows.ContentType.WEB_APPS;

        } else if (mCurrentView == mHistoryView) {
            return Windows.ContentType.HISTORY;

        } else if (mCurrentView == mDownloadsView) {
            return Windows.ContentType.DOWNLOADS;

        } else if (mCurrentView == mAddonsView) {
            return Windows.ContentType.ADDONS;

        } else if (mCurrentView == mSystemNotificationsView) {
            return Windows.ContentType.NOTIFICATIONS;

        } else {
            return Windows.ContentType.WEB_CONTENT;
        }
    }

    public void selectPanel(Windows.ContentType panelType) {
        mCurrentPanel = panelType;

        if (panelType == Windows.ContentType.WEB_CONTENT) {
            panelType = getSelectedPanelType();
        }

        mBinding.tabcontent.removeAllViews();

        if (BuildConfig.FLAVOR_backend.equals("chromium")) {
            mBinding.addons.setVisibility(View.GONE);
        }

        mBinding.bookmarks.setActiveMode(false);
        mBinding.webApps.setActiveMode(false);
        mBinding.history.setActiveMode(false);
        mBinding.downloads.setActiveMode(false);
        mBinding.addons.setActiveMode(false);
        mBinding.notifications.setActiveMode(false);

        switch (panelType) {
            case WEB_CONTENT:
                break;
            case BOOKMARKS:
                selectBookmarks();
                break;
            case WEB_APPS:
                selectWebApps();
                break;
            case HISTORY:
                selectHistory();
                break;
            case DOWNLOADS:
                selectDownloads();
                break;
            case ADDONS:
                selectAddons();
                break;
            case NOTIFICATIONS:
                selectNotifications();
                break;
        }

        mBinding.setCanGoBack(mCurrentView.canGoBack());
        mCurrentView.onShow();

        mBinding.searchBar.setQuery("", false);
        mBinding.searchBar.clearFocus();
        mBinding.searchBar.setVisibility(mCurrentView.supportsSearch() ? View.VISIBLE : View.INVISIBLE);
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

    private void selectDownloads() {
        mCurrentView = mDownloadsView;
        mBinding.downloads.setActiveMode(true);
        mBinding.tabcontent.addView(mDownloadsView);
    }

    private void selectAddons() {
        mCurrentView = mAddonsView;
        mBinding.addons.setActiveMode(true);
        mBinding.tabcontent.addView(mAddonsView);
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
