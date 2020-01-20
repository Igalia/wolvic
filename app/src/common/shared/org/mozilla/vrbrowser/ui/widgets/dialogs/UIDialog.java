package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.content.Context;
import android.util.AttributeSet;

import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;

import java.util.LinkedList;

public abstract class UIDialog extends UIWidget implements WidgetManagerDelegate.WorldClickListener {

    private static LinkedList<UIDialog> mDialogs = new LinkedList<>();

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
        if (!isVisible()) {
            super.show(aShowFlags);

            mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);

            UIDialog head = mDialogs.peek();
            if (head != null && head.isVisible()) {
                head.hide();
            }
            mDialogs.push(this);
        }
    }

    @Override
    public void hide(int aHideFlags) {
        super.hide(aHideFlags);

        mWidgetManager.popWorldBrightness(this);

        mDialogs.remove(this);
        UIDialog head = mDialogs.peek();
        if (head != null) {
            head.show();
        }
    }

    private void show() {
        if (!isVisible()) {
            super.show(REQUEST_FOCUS);

            mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);
        }
    }

    private void hide() {
        super.hide(KEEP_WIDGET);

        mWidgetManager.popWorldBrightness(this);
    }

    @Override
    public void onWorldClick() {
        if (isVisible()) {
            post(this::onDismiss);
        }
    }

    public static void closeAllDialogs() {
        new LinkedList<>(mDialogs).forEach(dialog -> dialog.onDismiss());
    }
}
