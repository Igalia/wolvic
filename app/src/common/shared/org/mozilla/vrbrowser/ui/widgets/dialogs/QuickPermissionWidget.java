package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.content.Context;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.QuickPermissionDialogBinding;
import org.mozilla.vrbrowser.db.SitePermission;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.ViewUtils;

public class QuickPermissionWidget extends UIWidget implements WidgetManagerDelegate.FocusChangeListener {
    public interface Delegate {
        void onBlock();
        void onAllow();
    }

    private Delegate mDelegate;
    private String mDomain = "";
    QuickPermissionDialogBinding mBinding;
    private @SitePermission.Category int mCategory = SitePermission.SITE_PERMISSION_WEBXR;

    public QuickPermissionWidget(Context aContext) {
        super(aContext);
        initialize();
    }

    private void initialize() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        mBinding = DataBindingUtil.inflate(inflater, R.layout.quick_permission_dialog, this, true);
        mBinding.setBlockButtonVisible(false);
        mBinding.allowButton.setOnClickListener(v -> {
            if (mDelegate != null) {
                mDelegate.onAllow();
            }
        });
        mBinding.blockButton.setOnClickListener(v -> {
            if (mDelegate != null) {
                mDelegate.onBlock();
            }
        });
        updateUI();
    }

    public void setData(String uri, int aCategory, boolean aBlocked) {
        mCategory = aCategory;
        mDomain = uri;
        mBinding.setBlockButtonVisible(aBlocked);
        updateUI();
    }

    public void updateUI() {
        switch (mCategory) {
            case SitePermission.SITE_PERMISSION_WEBXR: {
                mBinding.message.setText(
                        getResources().getString(R.string.webxr_permission_dialog_message,
                                mBinding.getBlockButtonVisible() ?
                                        getResources().getString(R.string.off).toUpperCase() :
                                        getResources().getString(R.string.on).toUpperCase(),
                                getResources().getString(R.string.sumo_webxr_url)));
                mBinding.allowButton.setText(R.string.permission_allow);
                mBinding.blockButton.setText(R.string.pop_up_site_switch_block);
                break;
            }
            case SitePermission.SITE_PERMISSION_TRACKING: {
                mBinding.message.setText(
                        getResources().getString(R.string.tracking_dialog_message,
                        mBinding.getBlockButtonVisible() ?
                                getResources().getString(R.string.on).toUpperCase() :
                                getResources().getString(R.string.off).toUpperCase(),
                                getResources().getString(R.string.sumo_etp_url)));
                mBinding.allowButton.setText(R.string.tracking_dialog_button_disable);
                mBinding.blockButton.setText(R.string.tracking_dialog_button_enable);
                break;
            }
        }

        mBinding.message.setLinkClickListener((widget, url) -> {
            mWidgetManager.openNewTabForeground(url);
            onDismiss();
        });

        mBinding.executePendingBindings();
    }

    public @SitePermission.Category int getCategory() {
        return mCategory;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        updateUI();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.quick_permission_width);
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.quick_permission_height);
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 1.0f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.0f;
        aPlacement.translationX = 0.0f;
        aPlacement.translationY = 0.0f;
        aPlacement.translationZ = 1.0f;
        aPlacement.visible = false;
    }

    public void setDelegate(Delegate aDelegate) {
        mDelegate = aDelegate;
    }

    @Override
    public void show(@ShowFlags int aShowFlags) {
        super.show(aShowFlags);
        mWidgetManager.addFocusChangeListener(this);
    }

    @Override
    public void hide(@HideFlags int aHideFlags) {
        super.hide(aHideFlags);
        mWidgetManager.removeFocusChangeListener(this);
    }

    // WidgetManagerDelegate.FocusChangeListener
    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (!ViewUtils.isEqualOrChildrenOf(this, newFocus)) {
            onDismiss();
        }
    }
}