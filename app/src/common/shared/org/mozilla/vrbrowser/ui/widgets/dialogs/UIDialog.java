package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;

public abstract class UIDialog extends UIWidget implements WidgetManagerDelegate.FocusChangeListener {
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
        mWidgetManager.addFocusChangeListener(this);
    }

    @Override
    public void releaseWidget() {
        mWidgetManager.removeFocusChangeListener(this);
        super.releaseWidget();
    }

    @Override
    public boolean isDialog() {
        return true;
    }

    // WidgetManagerDelegate.FocusChangeListener

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (oldFocus == this && isVisible()) {
            onDismiss();
        }
    }
}
