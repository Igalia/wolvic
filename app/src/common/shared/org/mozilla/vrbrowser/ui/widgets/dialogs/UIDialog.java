package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.content.Context;
import android.util.AttributeSet;

import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;

public abstract class UIDialog extends UIWidget implements WidgetManagerDelegate.WorldClickListener {
    public UIDialog(Context aContext) {
        super(aContext);
        initialize();
    }

    public UIDialog(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize();
    }

    public UIDialog(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize();
    }

    private void initialize() {
        mWidgetManager.addWorldClickListener(this);
    }

    @Override
    public void releaseWidget() {
        mWidgetManager.removeWorldClickListener(this);
        super.releaseWidget();
    }

    @Override
    public boolean isDialog() {
        return true;
    }

    @Override
    public void show(int aShowFlags) {
        super.show(aShowFlags);

        mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);
    }

    @Override
    public void hide(int aHideFlags) {
        super.hide(aHideFlags);

        mWidgetManager.popWorldBrightness(this);
    }

    @Override
    public void onWorldClick() {
        if (this.isVisible()) {
            onDismiss();
        }
    }
}
