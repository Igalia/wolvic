/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.options;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ScrollView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.SessionStore;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.ui.views.UIButton;
import org.mozilla.vrbrowser.ui.views.settings.ButtonSetting;
import org.mozilla.vrbrowser.ui.views.settings.RadioGroupSetting;
import org.mozilla.vrbrowser.ui.views.settings.SingleEditSetting;
import org.mozilla.vrbrowser.ui.views.settings.SwitchSetting;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.ui.widgets.dialogs.RestartDialogWidget;

import static org.mozilla.vrbrowser.utils.ServoUtils.isServoAvailable;

public class DeveloperOptionsWidget extends UIWidget implements
        WidgetManagerDelegate.WorldClickListener,
        WidgetManagerDelegate.FocusChangeListener {

    private AudioEngine mAudio;
    private UIButton mBackButton;

    private SwitchSetting mRemoteDebuggingSwitch;
    private SwitchSetting mConsoleLogsSwitch;
    private SwitchSetting mMultiprocessSwitch;
    private SwitchSetting mServoSwitch;

    private SingleEditSetting mHomepageEdit;
    private String mDefaultHomepageUrl;

    private ButtonSetting mResetButton;

    private int mRestartDialogHandle = -1;
    private ScrollView mScrollbar;

    public DeveloperOptionsWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public DeveloperOptionsWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public DeveloperOptionsWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.options_developer, this);

        mAudio = AudioEngine.fromContext(aContext);

        mBackButton = findViewById(R.id.backButton);
        mBackButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            hide(REMOVE_WIDGET);
            if (mDelegate != null) {
                mDelegate.onDismiss();
            }
        });

        mDefaultHomepageUrl = getContext().getString(R.string.homepage_url);

        mHomepageEdit = findViewById(R.id.homepage_edit);
        mHomepageEdit.setHint1(getContext().getString(R.string.homepage_hint));
        mHomepageEdit.setDefaultFirstValue(mDefaultHomepageUrl);
        mHomepageEdit.setFirstText(SettingsStore.getInstance(getContext()).getHomepage());
        mHomepageEdit.setOnClickListener(mHomepageListener);
        setHomepage(SettingsStore.getInstance(getContext()).getHomepage());

        mRemoteDebuggingSwitch = findViewById(R.id.remote_debugging_switch);
        mRemoteDebuggingSwitch.setOnCheckedChangeListener(mRemoteDebuggingListener);
        setRemoteDebugging(SettingsStore.getInstance(getContext()).isRemoteDebuggingEnabled(), false);

        mConsoleLogsSwitch = findViewById(R.id.show_console_switch);
        mConsoleLogsSwitch.setOnCheckedChangeListener(mConsoleLogsListener);
        setConsoleLogs(SettingsStore.getInstance(getContext()).isConsoleLogsEnabled(), false);

        mMultiprocessSwitch = findViewById(R.id.multiprocess_switch);
        mMultiprocessSwitch.setOnCheckedChangeListener(mMultiprocessListener);
        setMultiprocess(SettingsStore.getInstance(getContext()).isMultiprocessEnabled(), false);

        mServoSwitch = findViewById(R.id.servo_switch);
        if (!isServoAvailable()) {
            mServoSwitch.setVisibility(View.GONE);
        } else {
            mServoSwitch.setOnCheckedChangeListener(mServoListener);
            setServo(SettingsStore.getInstance(getContext()).isServoEnabled(), false);
        }

        mResetButton = findViewById(R.id.resetButton);
        mResetButton.setOnClickListener(mResetListener);

        mScrollbar = findViewById(R.id.scrollbar);
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.developer_options_width);
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.developer_options_height);
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.5f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.translationY = WidgetPlacement.unitFromMeters(getContext(), R.dimen.restart_dialog_world_y);
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.restart_dialog_world_z);
    }

    @Override
    public void show() {
        super.show();

        mWidgetManager.addWorldClickListener(this);
        mWidgetManager.addFocusChangeListener(this);
        mScrollbar.scrollTo(0, 0);
    }

    @Override
    public void hide(@HideFlags int aHideFlags) {
        super.hide(aHideFlags);

        mHomepageEdit.cancel();

        mWidgetManager.removeWorldClickListener(this);
        mWidgetManager.removeFocusChangeListener(this);
    }

    @Override
    protected void onDismiss() {
        if (mHomepageEdit.isEditing()) {
            mHomepageEdit.cancel();

        } else {
            super.onDismiss();
        }
    }

    private void showRestartDialog() {
        hide(UIWidget.REMOVE_WIDGET);

        UIWidget widget = getChild(mRestartDialogHandle);
        if (widget == null) {
            widget = createChild(RestartDialogWidget.class, false);
            mRestartDialogHandle = widget.getHandle();
            widget.setDelegate(() -> onRestartDialogDismissed());
        }

        widget.show();
    }

    private void onRestartDialogDismissed() {
       show();
    }

    private OnClickListener mHomepageListener = (view) -> {
        if (!mHomepageEdit.getFirstText().isEmpty()) {
            setHomepage(mHomepageEdit.getFirstText());

        } else {
            setHomepage(mDefaultHomepageUrl);
        }
    };

    private SwitchSetting.OnCheckedChangeListener mRemoteDebuggingListener = (compoundButton, value, doApply) -> {
        setRemoteDebugging(value, doApply);
    };

    private SwitchSetting.OnCheckedChangeListener mConsoleLogsListener = (compoundButton, value, doApply) -> {
        setConsoleLogs(value, doApply);
    };

    private SwitchSetting.OnCheckedChangeListener mMultiprocessListener = (compoundButton, value, doApply) -> {
        setMultiprocess(value, doApply);
    };

    private SwitchSetting.OnCheckedChangeListener mServoListener = (compoundButton, b, doApply) -> {
        setServo(b, true);
    };

    private OnClickListener mResetListener = (view) -> {
        boolean restart = false;
        if (mRemoteDebuggingSwitch.isChecked() != SettingsStore.REMOTE_DEBUGGING_DEFAULT) {
            setRemoteDebugging(SettingsStore.REMOTE_DEBUGGING_DEFAULT, true);
            restart = true;
        }

        if (mConsoleLogsSwitch.isChecked() != SettingsStore.CONSOLE_LOGS_DEFAULT) {
            setConsoleLogs(SettingsStore.CONSOLE_LOGS_DEFAULT, true);
        }
        if (mMultiprocessSwitch.isChecked() != SettingsStore.MULTIPROCESS_DEFAULT) {
            setMultiprocess(SettingsStore.MULTIPROCESS_DEFAULT, true);
        }
        if (mServoSwitch.isChecked() != SettingsStore.SERVO_DEFAULT) {
            setServo(SettingsStore.SERVO_DEFAULT, true);
        }
        setHomepage(mDefaultHomepageUrl);

        if (restart)
            showRestartDialog();
    };

    private void setHomepage(String newHomepage) {
        mHomepageEdit.setOnClickListener(null);
        mHomepageEdit.setFirstText(newHomepage);
        SettingsStore.getInstance(getContext()).setHomepage(newHomepage);
        mHomepageEdit.setOnClickListener(mHomepageListener);
    }

    private void setRemoteDebugging(boolean value, boolean doApply) {
        mRemoteDebuggingSwitch.setOnCheckedChangeListener(null);
        mRemoteDebuggingSwitch.setValue(value, doApply);
        mRemoteDebuggingSwitch.setOnCheckedChangeListener(mRemoteDebuggingListener);

        SettingsStore.getInstance(getContext()).setRemoteDebuggingEnabled(value);

        if (doApply) {
            SessionStore.get().setRemoteDebugging(value);
        }
    }

    private void setConsoleLogs(boolean value, boolean doApply) {
        mConsoleLogsSwitch.setOnCheckedChangeListener(null);
        mConsoleLogsSwitch.setValue(value, doApply);
        mConsoleLogsSwitch.setOnCheckedChangeListener(mConsoleLogsListener);

        SettingsStore.getInstance(getContext()).setConsoleLogsEnabled(value);

        if (doApply) {
            SessionStore.get().setConsoleOutputEnabled(value);
        }
    }

    private void setMultiprocess(boolean value, boolean doApply) {
        mMultiprocessSwitch.setOnCheckedChangeListener(null);
        mMultiprocessSwitch.setValue(value, false);
        mMultiprocessSwitch.setOnCheckedChangeListener(mMultiprocessListener);

        SettingsStore.getInstance(getContext()).setMultiprocessEnabled(value);

        if (doApply) {
            SessionStore.get().setMultiprocess(value);
        }
    }

    private void setServo(boolean value, boolean doApply) {
        mServoSwitch.setOnCheckedChangeListener(null);
        mServoSwitch.setValue(value, false);
        mServoSwitch.setOnCheckedChangeListener(mServoListener);

        SettingsStore.getInstance(getContext()).setServoEnabled(value);

        if (doApply) {
            SessionStore.get().setServo(value);
        }
    }

    // WindowManagerDelegate.FocusChangeListener

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (oldFocus != null) {
            if (mHomepageEdit.contains(oldFocus) && mHomepageEdit.isEditing()) {
                mHomepageEdit.cancel();
            }
        }

        if (oldFocus == this && isVisible() && findViewById(newFocus.getId()) == null) {
            onDismiss();
        }
    }

    // WorldClickListener

    @Override
    public void onWorldClick() {
        if (isVisible()) {
            onDismiss();
        }
    }

}
