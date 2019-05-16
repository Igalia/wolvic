package org.mozilla.vrbrowser.ui.widgets.settings;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;

abstract class SettingsView extends FrameLayout {
    protected Delegate mDelegate;
    protected WidgetManagerDelegate mWidgetManager;

    public interface Delegate {
        void onDismiss();
        void exitWholeSettings();
        void showRestartDialog();
        void showAlert(String aTitle, String aMessage);
    }

    public SettingsView(@NonNull Context context, WidgetManagerDelegate aWidgetManager) {
        super(context);
        mWidgetManager = aWidgetManager;
    }

    public void setDelegate(Delegate aDelegate) {
        mDelegate = aDelegate;
    }

    protected void onDismiss() {
        if (mDelegate != null) {
            mDelegate.onDismiss();
        }
    }

    protected void exitWholeSettings() {
        if (mDelegate != null) {
            mDelegate.exitWholeSettings();
        }
    }

    protected void showRestartDialog() {
        if (mDelegate != null) {
            mDelegate.showRestartDialog();
        }
    }

    protected void showAlert(String aTitle, String aMessage) {
        if (mDelegate != null) {
            mDelegate.showAlert(aTitle, aMessage);
        }
    }

    protected boolean isVisible() {
        return this.getVisibility() == View.VISIBLE;
    }

    public void onShown() {
        setFocusableInTouchMode(true);
        requestFocusFromTouch();
    }

    public void onHidden() {
        clearFocus();
    }

    public void onGlobalFocusChanged(View oldFocus, View newFocus) {}
}
