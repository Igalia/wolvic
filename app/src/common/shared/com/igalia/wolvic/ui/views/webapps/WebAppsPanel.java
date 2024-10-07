package com.igalia.wolvic.ui.views.webapps;

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
import com.igalia.wolvic.databinding.LibraryBinding;
import com.igalia.wolvic.ui.delegates.LibraryNavigationDelegate;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;

import java.util.concurrent.Executor;

public class WebAppsPanel extends FrameLayout {

    private LibraryBinding mBinding;
    protected WidgetManagerDelegate mWidgetManager;
    protected Executor mUIThreadExecutor;
    private WebAppsView mWebAppsView;

    public WebAppsPanel(@NonNull Context context) {
        super(context);
        initialize();
    }

    public WebAppsPanel(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public WebAppsPanel(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    protected void initialize() {
        mWidgetManager = ((VRBrowserActivity) getContext());
        mUIThreadExecutor = ((VRBrowserApplication) getContext().getApplicationContext()).getExecutors().mainThread();

        mWebAppsView = new WebAppsView(getContext(), this);

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
                mWebAppsView.updateSearchFilter(s.toLowerCase());
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (s.isEmpty()) {
                    mWebAppsView.updateSearchFilter(s.toLowerCase());
                    return true;
                }
                return false;
            }
        });
        mBinding.searchBar.setOnCloseListener(() -> {
            mWebAppsView.updateSearchFilter("");
            return true;
        });
        mBinding.buttons.setVisibility(View.GONE);
        mBinding.setLifecycleOwner((VRBrowserActivity) getContext());
        mBinding.setSupportsSystemNotifications(BuildConfig.SUPPORTS_SYSTEM_NOTIFICATIONS);
        mBinding.setDelegate(new LibraryNavigationDelegate() {
            @Override
            public void onClose(@NonNull View view) {
                requestFocus();
                mWidgetManager.getFocusedWindow().hideWebAppsPanel();
            }

            @Override
            public void onBack(@NonNull View view) {
                requestFocus();
                mWebAppsView.onBack();
                mBinding.setCanGoBack(mWebAppsView.canGoBack());
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

        mWebAppsView.updateUI();

        updateUI();
    }

    public void onShow() {
        if (mWebAppsView == null) {
            return;
        }

        mWebAppsView.onShow();
        mBinding.searchBar.setQuery("", false);
        mBinding.searchBar.clearFocus();
    }

    public void onHide() {
        if (mWebAppsView == null) {
            return;
        }

        mWebAppsView.onHide();
    }

    public boolean onBack() {
        if (mWebAppsView == null) {
            return false;
        }

        return mWebAppsView.onBack();
    }

    public void onDestroy() {
        mWebAppsView.onDestroy();
    }

    private void selectTab() {
        mBinding.setCanGoBack(mWebAppsView.canGoBack());
        mBinding.tabcontent.addView(mWebAppsView);
        mWebAppsView.onShow();
    }

    public void onViewUpdated(@NonNull String title) {
        if (mBinding == null) {
            return;
        }

        mBinding.title.setText(title);
        mBinding.setCanGoBack(mWebAppsView.canGoBack());
    }
}
