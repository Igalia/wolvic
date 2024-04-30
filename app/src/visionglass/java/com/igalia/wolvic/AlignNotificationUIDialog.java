package com.igalia.wolvic;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.widget.Button;

import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.ui.widgets.dialogs.UIDialog;

public class AlignNotificationUIDialog extends UIDialog {

    public AlignNotificationUIDialog(Context aContext) {
        super(aContext);
        updateUI();
    }

    public AlignNotificationUIDialog(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        updateUI();
    }

    public AlignNotificationUIDialog(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        updateUI();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateUI();
    }

    private void updateUI() {
        removeAllViews();
        inflate(getContext(), R.layout.dialog_align, this);
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.prompt_dialog_width);
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.prompt_dialog_height);
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.parentAnchorY = 0.0f;
        aPlacement.parentAnchorX = 0.5f;
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
}
