package com.igalia.wolvic.addons.views;

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

import java.util.concurrent.Executor;

public class AddonsPanel extends FrameLayout {

    private LibraryBinding mBinding;
    protected WidgetManagerDelegate mWidgetManager;
    protected Executor mUIThreadExecutor;
    private AddonsView mAddonsView;

    public AddonsPanel(@NonNull Context context) {
        super(context);
        initialize();
    }

    public AddonsPanel(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public AddonsPanel(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    protected void initialize() {
        mWidgetManager = ((VRBrowserActivity) getContext());
        mUIThreadExecutor = ((VRBrowserApplication) getContext().getApplicationContext()).getExecutors().mainThread();

        mAddonsView = new AddonsView(getContext(), this);

        updateUI();
    }

    @SuppressLint("ClickableViewAccessibility")
    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.library, this, true);
        mBinding.buttons.setVisibility(View.GONE);
        mBinding.setLifecycleOwner((VRBrowserActivity) getContext());
        mBinding.setSupportsSystemNotifications(BuildConfig.SUPPORTS_SYSTEM_NOTIFICATIONS);
        mBinding.setDelegate(new LibraryNavigationDelegate() {
            @Override
            public void onClose(@NonNull View view) {
                requestFocus();
                mWidgetManager.getFocusedWindow().hideAddonsPanel();
            }

            @Override
            public void onBack(@NonNull View view) {
                requestFocus();
                mAddonsView.onBack();
                mBinding.setCanGoBack(mAddonsView.canGoBack());
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

        mAddonsView.updateUI();

        updateUI();
    }

    public void onShow() {
        if (mAddonsView != null) {
            mAddonsView.onShow();
        }
    }

    public void onHide() {
        if (mAddonsView != null) {
            mAddonsView.onHide();
        }
    }

    public boolean onBack() {
        if (mAddonsView != null) {
            return mAddonsView.onBack();
        }

        return false;
    }

    public void onDestroy() {
        mAddonsView.onDestroy();
    }

    private void selectTab() {
        mBinding.setCanGoBack(mAddonsView.canGoBack());
        mBinding.tabcontent.addView(mAddonsView);
        mAddonsView.onShow();
    }

    public void onViewUpdated(@NonNull String title) {
        if (mBinding != null) {
            mBinding.title.setText(title);
            mBinding.setCanGoBack(mAddonsView.canGoBack());
        }
    }
}
