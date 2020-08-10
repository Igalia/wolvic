package org.mozilla.vrbrowser.ui.widgets.settings;

import android.content.Context;
import android.graphics.Point;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.ui.views.CustomScrollView;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;

public abstract class SettingsView extends FrameLayout {

    public enum SettingViewType {
        MAIN,
        LANGUAGE,
        LANGUAGE_DISPLAY,
        LANGUAGE_CONTENT,
        LANGUAGE_VOICE,
        DISPLAY,
        PRIVACY,
        POPUP_EXCEPTIONS,
        WEBXR_EXCEPTIONS,
        DEVELOPER,
        FXA,
        ENVIRONMENT,
        CONTROLLER,
        TRACKING_EXCEPTION,
        LOGINS_AND_PASSWORDS,
        SAVED_LOGINS,
        LOGIN_EXCEPTIONS,
        LOGIN_EDIT
    }

    protected Delegate mDelegate;
    protected WidgetManagerDelegate mWidgetManager;
    protected CustomScrollView mScrollbar;

    public interface Delegate {
        void onDismiss();
        void exitWholeSettings();
        void showRestartDialog();
        void showAlert(String aTitle, String aMessage);
        void showView(SettingsView.SettingViewType type);
        void showView(SettingsView.SettingViewType type, @Nullable Object extras);
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
        if (mScrollbar != null) {
            mScrollbar.fullScroll(ScrollView.FOCUS_UP);
            mScrollbar.setSmoothScrollingEnabled(true);
            mScrollbar.smoothScrollTo(0,0);
        }

        setFocusableInTouchMode(true);
        requestFocusFromTouch();
    }

    public void onHidden() {
        clearFocus();
    }

    public boolean isEditing() {
        return false;
    }

    protected void onGlobalFocusChanged(View oldFocus, View newFocus) {}

    public Point getDimensions() {
        return new Point( WidgetPlacement.dpDimension(getContext(), R.dimen.options_width),
                WidgetPlacement.dpDimension(getContext(), R.dimen.options_height));
    }

    protected boolean reset() {
        return false;
    }

    protected void updateUI() {
        removeAllViews();
    }

    protected abstract SettingViewType getType();

}
