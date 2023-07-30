package com.igalia.wolvic.ui.views.downloads;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
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

public class DownloadsPanel extends FrameLayout {

    private LibraryBinding mBinding;
    protected WidgetManagerDelegate mWidgetManager;
    protected Executor mUIThreadExecutor;
    private DownloadsView mDownloadsView;

    public DownloadsPanel(@NonNull Context context) {
        super(context);
        initialize();
    }

    public DownloadsPanel(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public DownloadsPanel(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    protected void initialize() {
        mWidgetManager = ((VRBrowserActivity) getContext());
        mUIThreadExecutor = ((VRBrowserApplication) getContext().getApplicationContext()).getExecutors().mainThread();

        mDownloadsView = new DownloadsView(getContext(), this);

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
                mDownloadsView.updateSearchFilter(s.toLowerCase());
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (s.isEmpty()) {
                    mDownloadsView.updateSearchFilter(s.toLowerCase());
                    return true;
                }
                return false;
            }
        });
        mBinding.searchBar.setOnCloseListener(() -> {
            mDownloadsView.updateSearchFilter("");
            return true;
        });
        mBinding.buttons.setVisibility(View.GONE);
        mBinding.setLifecycleOwner((VRBrowserActivity) getContext());
        mBinding.setSupportsSystemNotifications(BuildConfig.SUPPORTS_SYSTEM_NOTIFICATIONS);
        mBinding.setDelegate(new LibraryNavigationDelegate() {
            @Override
            public void onClose(@NonNull View view) {
                requestFocus();
                mWidgetManager.getFocusedWindow().hideDownloadsPanel();
            }

            @Override
            public void onBack(@NonNull View view) {
                requestFocus();
                mDownloadsView.onBack();
                mBinding.setCanGoBack(mDownloadsView.canGoBack());
            }

            @Override
            public void onButtonClick(@NonNull View view) {
                requestFocus();
                selectTab();
            }
        });
        mBinding.executePendingBindings();

        selectTab();

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

        mDownloadsView.updateUI();

        updateUI();
    }

    public void onShow() {
        if (mDownloadsView == null) {
            return;
        }

        mDownloadsView.onShow();        
    }

    public void onHide() {
        if (mDownloadsView == null) {
            return;
        }

        mDownloadsView.onHide();
        mBinding.searchBar.setQuery("", false);
        mBinding.searchBar.clearFocus();
    }

    public boolean onBack() {
        if (mDownloadsView == null) {
            return false;
        }

        return mDownloadsView.onBack();
    }

    public void onDestroy() {
        mDownloadsView.onDestroy();
    }

    private void selectTab() {
        mBinding.setCanGoBack(mDownloadsView.canGoBack());
        mBinding.tabcontent.addView(mDownloadsView);
        mDownloadsView.onShow();
    }

    public void onViewUpdated(@NonNull String title) {
        if (mBinding == null) {
            return;
        }

        mBinding.title.setText(title);
        mBinding.setCanGoBack(mDownloadsView.canGoBack());
    }
}
