package com.igalia.wolvic.ui.widgets.dialogs;

import android.content.Context;
import android.util.AttributeSet;

import com.igalia.wolvic.ui.widgets.UIWidget;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;

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

        // For visibility, dialogs are not shown on the cylinder by default.
        mWidgetPlacement.cylinder = false;
    }

    @Override
    public void releaseWidget() {
        if (mWidgetManager != null) {
            mWidgetManager.removeWorldClickListener(this);
        }
        mDialogs.remove(this);
        super.releaseWidget();
    }

    @Override
    public boolean isDialog() {
        return true;
    }

    @Override
    public void show(int aShowFlags) {
        show(aShowFlags, false);
    }

    public void show(int aShowFlags, boolean addLast) {
        if (!isVisible()) {
            boolean showFront = !addLast || mDialogs.size() == 0;

            if (showFront){
                super.show(aShowFlags);

                mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);

                UIDialog head = mDialogs.peek();
                if (head != null && head.isVisible()) {
                    head.hide();
                }

                mDialogs.push(this);

            } else {
                mDialogs.addLast(this);
            }
        }
        if (visibilityListener != null) {
            visibilityListener.onUIDialogVisibilityChanged(true);
        }
    }

    @Override
    public void hide(int aHideFlags) {
        super.hide(aHideFlags);

        if (mWidgetManager != null) {
            mWidgetManager.popWorldBrightness(this);
        }

        mDialogs.remove(this);
        UIDialog head = mDialogs.peek();
        if (head != null) {
            head.show();
        }
        if (visibilityListener != null) {
            visibilityListener.onUIDialogVisibilityChanged(false);
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

    public interface VisibilityListener {
        void onUIDialogVisibilityChanged(boolean isVisible);
    }

    private VisibilityListener visibilityListener;

    public void setVisibilityListener(VisibilityListener listener) {
        this.visibilityListener = listener;
    }
}
