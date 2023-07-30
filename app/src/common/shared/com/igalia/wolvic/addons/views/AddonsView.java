package com.igalia.wolvic.addons.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.addons.adapters.AddonsViewAdapter;
import com.igalia.wolvic.addons.delegates.AddonsDelegate;
import com.igalia.wolvic.databinding.AddonsBinding;
import com.igalia.wolvic.ui.views.library.LibraryView;
import com.igalia.wolvic.utils.SystemUtils;

import mozilla.components.feature.addons.Addon;
import mozilla.components.feature.addons.ui.ExtensionsKt;

public class AddonsView extends LibraryView implements AddonsDelegate {

    private static final String LOGTAG = SystemUtils.createLogtag(AddonsView.class);

    private AddonsBinding mBinding;
    private AddonsViewAdapter mAdapter;
    private AddonsPanel mAddonsPanel;

    public AddonsView(@NonNull Context context) {
        super(context);

        initialize();
    }

    public AddonsView(@NonNull Context context, @NonNull AddonsPanel panel) {
        super(context);
        mAddonsPanel = panel;
        initialize();
    }

    protected void initialize() {
        super.initialize();

        updateUI();
    }

    @SuppressLint("ClickableViewAccessibility")
    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.addons, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity) getContext());

        mAdapter = new AddonsViewAdapter(this);
        mBinding.pager.setAdapter(mAdapter);
        mBinding.pager.setUserInputEnabled(false);
        mBinding.pager.setPageTransformer((page, position) -> post(() -> mAdapter.notifyDataSetChanged()));

        setView(AddonsViewAdapter.ADDONS_LIST);

        mBinding.executePendingBindings();

        setOnTouchListener((v, event) -> {
            v.requestFocusFromTouch();
            return false;
        });
    }

    @Override
    public void onDestroy() {

    }

    @Override
    public void onShow() {
        updateLayout();

        if (mBinding.pager.getCurrentItem() == AddonsViewAdapter.ADDONS_LEVEL_0) {
            if (mAddonsPanel != null) {
                mAddonsPanel.onViewUpdated(getContext().getString(R.string.addons_title));
            }
        } else {
            if (mAddonsPanel != null) {
                mAddonsPanel.onViewUpdated(ExtensionsKt.translateName(mAdapter.getCurrentAddon(), getContext()));
            }
        }
    }

    @Override
    public void onHide() {
        super.onHide();
    }

    public boolean onBack() {
        if (mBinding != null && !mBinding.pager.isFakeDragging()) {
            int currentItem = mBinding.pager.getCurrentItem();
            setView(mBinding.pager.getCurrentItem());
            return currentItem != AddonsViewAdapter.ADDONS_LEVEL_0;
        }

        return false;
    }

    @Override
    public boolean canGoBack() {
        switch (mBinding.pager.getCurrentItem()) {
            case AddonsViewAdapter.ADDONS_LEVEL_2:
            case AddonsViewAdapter.ADDONS_LEVEL_1:
                return true;
        }

        return false;
    }

    private void setView(@AddonsViewAdapter.AddonsViews int fromViewId) {
         if (mBinding.pager.isFakeDragging()) {
             return;
         }

        if (fromViewId == AddonsViewAdapter.ADDON_OPTIONS_DETAILS) {
            if (mAdapter.getCurrentAddon() != null && mAdapter.getCurrentAddon().isInstalled()) {
                showAddonOptions(mAdapter.getCurrentAddon());

            } else {
                showAddonsList();
            }
            showAddonOptions(mAdapter.getCurrentAddon());

        } else if (fromViewId == AddonsViewAdapter.ADDON_OPTIONS_PERMISSIONS) {
            showAddonOptions(mAdapter.getCurrentAddon());

        } else {
            showAddonsList();
        }
    }

    // AddonsViewDelegate

    @Override
    public void showAddonsList() {
        mAdapter.setCurrentAddon(null);
        mAdapter.setCurrentItem(AddonsViewAdapter.ADDONS_LIST);
        mBinding.pager.setCurrentItem(AddonsViewAdapter.ADDONS_LEVEL_0);
        if (mAddonsPanel != null) {
            mAddonsPanel.onViewUpdated(getContext().getString(R.string.addons_title));
        }
    }

    @Override
    public void showAddonOptions(@NonNull Addon addon) {
        mAdapter.setCurrentAddon(addon);
        mAdapter.setCurrentItem(AddonsViewAdapter.ADDON_OPTIONS);
        mBinding.pager.setCurrentItem(AddonsViewAdapter.ADDONS_LEVEL_1);
        if (mAddonsPanel != null) {
            mAddonsPanel.onViewUpdated(ExtensionsKt.translateName(addon, getContext()));
        }
    }

    @Override
    public void showAddonOptionsDetails(@NonNull Addon addon, int page) {
        mAdapter.setCurrentAddon(addon);
        mAdapter.setCurrentItem(AddonsViewAdapter.ADDON_OPTIONS_DETAILS);
        mBinding.pager.setCurrentItem(page);
        if (mAddonsPanel != null) {
            mAddonsPanel.onViewUpdated(ExtensionsKt.translateName(addon, getContext()));
        }
    }

    @Override
    public void showAddonOptionsPermissions(@NonNull Addon addon) {
        mAdapter.setCurrentAddon(addon);
        mAdapter.setCurrentItem(AddonsViewAdapter.ADDON_OPTIONS_PERMISSIONS);
        mBinding.pager.setCurrentItem(AddonsViewAdapter.ADDONS_LEVEL_2);
        if (mAddonsPanel != null) {
            mAddonsPanel.onViewUpdated(ExtensionsKt.translateName(addon, getContext()));
        }
    }

}
