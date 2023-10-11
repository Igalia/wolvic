package com.igalia.wolvic.ui.widgets.dialogs;

import android.content.Context;
import android.content.res.Configuration;
import android.view.LayoutInflater;

import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.R;
import com.igalia.wolvic.databinding.SettingDialogBinding;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;

public abstract class SettingDialogWidget extends UIDialog {

    protected SettingDialogBinding mBinding;

    public SettingDialogWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    protected void initialize(Context aContext) {
        updateUI();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.settings_dialog_width);
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.settings_dialog_height);
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.0f;
        aPlacement.translationY = WidgetPlacement.unitFromMeters(getContext(), R.dimen.settings_world_y) -
                WidgetPlacement.unitFromMeters(getContext(), R.dimen.window_world_y);
        updatePlacementTranslationZ(); 
    }

    @Override
    public void updatePlacementTranslationZ() {
        getPlacement().translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.settings_world_z) -
                WidgetPlacement.getWindowWorldZMeters(getContext());
    }

    @Override
    public void show(@ShowFlags int aShowFlags) {
        mWidgetPlacement.parentHandle = mWidgetManager.getFocusedWindow().getHandle();

        super.show(aShowFlags);
    }

    protected void onFooterButton() {

    }

    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.setting_dialog, this, true);

        // Header
        mBinding.headerLayout.setBackClickListener(view -> onDismiss());

        // Footer
        mBinding.footerLayout.setFooterButtonClickListener(v -> onFooterButton());
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        updateUI();
    }
}
