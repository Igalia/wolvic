package org.mozilla.vrbrowser.ui.views.library;

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

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserActivity;
import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.addons.views.AddonsView;
import org.mozilla.vrbrowser.databinding.LibraryBinding;
import org.mozilla.vrbrowser.ui.delegates.LibraryNavigationDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.Windows;

import java.util.concurrent.Executor;

public class LibraryPanel extends FrameLayout {

    private LibraryBinding mBinding;
    protected WidgetManagerDelegate mWidgetManager;
    protected Executor mUIThreadExecutor;
    private BookmarksView mBookmarksView;
    private HistoryView mHistoryView;
    private DownloadsView mDownloadsView;
    private AddonsView mAddonsView;
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
        mUIThreadExecutor = ((VRBrowserApplication)getContext().getApplicationContext()).getExecutors().mainThread();

        mBookmarksView = new BookmarksView(getContext(), this);
        mHistoryView = new HistoryView(getContext(), this);
        mDownloadsView = new DownloadsView(getContext(), this);
        mAddonsView = new AddonsView(getContext(), this);
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
        mDownloadsView.updateUI();
        mAddonsView.updateUI();

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
        mDownloadsView.onDestroy();
        mAddonsView.onDestroy();
    }

    public @Windows.PanelType int getSelectedPanelType() {
        if (mCurrentView == mBookmarksView) {
            return Windows.BOOKMARKS;

        } else if (mCurrentView == mHistoryView) {
            return Windows.HISTORY;

        } else if (mCurrentView == mDownloadsView) {
            return Windows.DOWNLOADS;

        } else if (mCurrentView == mAddonsView) {
            return Windows.ADDONS;

        } else {
            return Windows.NONE;
        }
    }

    private void selectTab(@NonNull View view) {
        mBinding.tabcontent.removeAllViews();

        mBinding.bookmarks.setActiveMode(false);
        mBinding.history.setActiveMode(false);
        mBinding.downloads.setActiveMode(false);
        mBinding.addons.setActiveMode(false);
        if(view.getId() == R.id.bookmarks){
            selectBookmarks();

        } else if(view.getId() == R.id.history){
            selectHistory();

        } else if(view.getId() == R.id.downloads){
            selectDownloads();

        } else if(view.getId() == R.id.addons){
            selectAddons();
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
            case Windows.HISTORY:
                selectTab(mBinding.history);
                break;
            case Windows.DOWNLOADS:
                selectTab(mBinding.downloads);
                break;
            case Windows.ADDONS:
                selectTab(mBinding.addons);
                break;
        }
    }

    private void selectBookmarks() {
        mCurrentView = mBookmarksView;
        mBinding.bookmarks.setActiveMode(true);
        mBinding.tabcontent.addView(mBookmarksView);
        mBinding.title.setText(R.string.bookmarks_title);
    }

    private void selectHistory() {
        mCurrentView = mHistoryView;
        mBinding.history.setActiveMode(true);
        mBinding.tabcontent.addView(mHistoryView);
        mBinding.title.setText(R.string.history_title);
    }

    private void selectDownloads() {
        mCurrentView = mDownloadsView;
        mBinding.downloads.setActiveMode(true);
        mBinding.tabcontent.addView(mDownloadsView);
        mBinding.title.setText(R.string.downloads_title);
    }

    private void selectAddons() {
        boolean alreadySelected = mCurrentView == mAddonsView;
        mCurrentView = mAddonsView;
        mBinding.addons.setActiveMode(true);
        mBinding.tabcontent.addView(mAddonsView);
        if (!alreadySelected) {
            mBinding.title.setText(R.string.addons_title);
        }
    }

    public void onViewUpdated(@NonNull String title) {
        if (mBinding != null) {
            mBinding.title.setText(title);
            mBinding.setCanGoBack(mCurrentView.canGoBack());
        }
    }
}
