package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.content.Context;
import android.view.LayoutInflater;

import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.databinding.SettingDialogBinding;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;

public abstract class SettingDialogWidget extends UIDialog {

    protected SettingDialogBinding mBinding;

    public SettingDialogWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    protected void initialize(Context aContext) {
        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.setting_dialog, this, true);

        // Header
        mBinding.headerLayout.setBackClickListener(view -> onDismiss());

        // Footer
        mBinding.footerLayout.setFooterButtonClickListener(v -> onFooterButton());
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
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.settings_world_z) -
                WidgetPlacement.unitFromMeters(getContext(), R.dimen.window_world_z);
    }

    @Override
    public void show(@ShowFlags int aShowFlags) {
        mWidgetPlacement.parentHandle = mWidgetManager.getFocusedWindow().getHandle();

        super.show(aShowFlags);
    }

    protected void onFooterButton() {

    }

}
