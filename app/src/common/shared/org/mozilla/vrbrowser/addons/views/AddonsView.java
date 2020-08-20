package org.mozilla.vrbrowser.addons.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserActivity;
import org.mozilla.vrbrowser.addons.adapters.AddonsViewAdapter;
import org.mozilla.vrbrowser.addons.delegates.AddonsDelegate;
import org.mozilla.vrbrowser.databinding.AddonsBinding;
import org.mozilla.vrbrowser.ui.views.library.LibraryPanel;
import org.mozilla.vrbrowser.ui.views.library.LibraryView;
import org.mozilla.vrbrowser.utils.SystemUtils;

import mozilla.components.feature.addons.Addon;
import mozilla.components.feature.addons.ui.ExtensionsKt;

public class AddonsView extends LibraryView implements AddonsDelegate {

    private static final String LOGTAG = SystemUtils.createLogtag(AddonsView.class);

    private AddonsBinding mBinding;
    private AddonsViewAdapter mAdapter;

    public AddonsView(@NonNull Context context) {
        super(context);

        initialize();
    }

    public AddonsView(@NonNull Context context, @NonNull LibraryPanel rootPanel) {
        super(context, rootPanel);

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
        switch (fromViewId) {
            case AddonsViewAdapter.ADDON_OPTIONS_DETAILS: {
                if (mAdapter.getCurrentAddon() != null && mAdapter.getCurrentAddon().isInstalled()) {
                    showAddonOptions(mAdapter.getCurrentAddon());

                } else {
                    showAddonsList();
                }
            }
            case AddonsViewAdapter.ADDON_OPTIONS_PERMISSIONS: {
                showAddonOptions(mAdapter.getCurrentAddon());
                break;
            }
            default: {
                showAddonsList();
                break;
            }
        }
    }

    // AddonsViewDelegate

    @Override
    public void showAddonsList() {
        mAdapter.setCurrentAddon(null);
        mAdapter.setCurrentItem(AddonsViewAdapter.ADDONS_LIST);
        mBinding.pager.setCurrentItem(AddonsViewAdapter.ADDONS_LEVEL_0);
        if (mRootPanel != null) {
            mRootPanel.onViewUpdated(getContext().getString(R.string.addons_title));
        }
    }

    @Override
    public void showAddonOptions(@NonNull Addon addon) {
        mAdapter.setCurrentAddon(addon);
        mAdapter.setCurrentItem(AddonsViewAdapter.ADDON_OPTIONS);
        mBinding.pager.setCurrentItem(AddonsViewAdapter.ADDONS_LEVEL_1);
        if (mRootPanel != null) {
            mRootPanel.onViewUpdated(ExtensionsKt.getTranslatedName(addon));
        }
    }

    @Override
    public void showAddonOptionsDetails(@NonNull Addon addon, int page) {
        mAdapter.setCurrentAddon(addon);
        mAdapter.setCurrentItem(AddonsViewAdapter.ADDON_OPTIONS_DETAILS);
        mBinding.pager.setCurrentItem(page);
        if (mRootPanel != null) {
            mRootPanel.onViewUpdated(ExtensionsKt.getTranslatedName(addon));
        }
    }

    @Override
    public void showAddonOptionsPermissions(@NonNull Addon addon) {
        mAdapter.setCurrentAddon(addon);
        mAdapter.setCurrentItem(AddonsViewAdapter.ADDON_OPTIONS_PERMISSIONS);
        mBinding.pager.setCurrentItem(AddonsViewAdapter.ADDONS_LEVEL_2);
        if (mRootPanel != null) {
            mRootPanel.onViewUpdated(ExtensionsKt.getTranslatedName(addon));
        }
    }
}
