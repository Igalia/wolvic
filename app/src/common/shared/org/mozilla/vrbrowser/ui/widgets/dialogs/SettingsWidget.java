/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.BuildConfig;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.SessionStore;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.ui.views.HoneycombButton;
import org.mozilla.vrbrowser.ui.views.HoneycombSwitch;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.ui.widgets.options.ControllerOptionsWidget;
import org.mozilla.vrbrowser.ui.widgets.options.DeveloperOptionsWidget;
import org.mozilla.vrbrowser.ui.widgets.options.DisplayOptionsWidget;
import org.mozilla.vrbrowser.ui.widgets.options.PrivacyOptionsWidget;
import org.mozilla.vrbrowser.ui.widgets.options.VoiceSearchLanguageOptionsWidget;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;

public class SettingsWidget extends UIWidget implements WidgetManagerDelegate.FocusChangeListener {

    private static final String LOGTAG = "VRB";

    private AudioEngine mAudio;
    private int mDeveloperOptionsDialogHandle = -1;
    private int mLanguageOptionsDialogHandle = -1;
    private int mDisplayOptionsDialogHandle = -1;
    private int mControllerOptionsDialogHandle = -1;
    private int mPrivacyOptionsDialogHandle = -1;
    private int mEnvironmentOptionsDialogHandle = -1;
    private TextView mBuildText;

    class VersionGestureListener extends GestureDetector.SimpleOnGestureListener {

        private boolean mIsHash;

        @Override
        public boolean onDoubleTap(MotionEvent event) {
            if (mIsHash)
                mBuildText.setText(versionCodeToDate(BuildConfig.VERSION_CODE));
            else
                mBuildText.setText(BuildConfig.GIT_HASH);

            mIsHash = !mIsHash;

            return true;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }
    }

    public SettingsWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public SettingsWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public SettingsWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.settings, this);

        mWidgetManager.addFocusChangeListener(this);

        ImageButton cancelButton = findViewById(R.id.settingsCancelButton);

        cancelButton.setOnClickListener(v -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            onDismiss();
        });

        HoneycombButton languageButton = findViewById(R.id.languageButton);
        languageButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            onLanguageOptionsClick();
        });

        HoneycombButton privacyButton = findViewById(R.id.privacyButton);
        privacyButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            onSettingsPrivacyClick();
        });

        HoneycombSwitch crashSwitch = findViewById(R.id.crashReportingSwitch);
        crashSwitch.setChecked(SettingsStore.getInstance(getContext()).isCrashReportingEnabled());
        crashSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            onSettingsCrashReportingChange(b);
        });

        HoneycombSwitch telemetrySwitch = findViewById(R.id.telemetry_switch);
        telemetrySwitch.setChecked(SettingsStore.getInstance(getContext()).isTelemetryEnabled());
        telemetrySwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            onSettingsTelemetryChange(b);
        });

        HoneycombButton displayButton = findViewById(R.id.displayButton);
        displayButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            onDisplayOptionsClick();
        });

        HoneycombButton environmentButton = findViewById(R.id.environmentButton);
        environmentButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            showEnvironmentOptionsDialog();
        });

        TextView versionText = findViewById(R.id.versionText);
        try {
            PackageInfo pInfo = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0);
            versionText.setText(String.format(getResources().getString(R.string.settings_version), pInfo.versionName));

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        mBuildText = findViewById(R.id.buildText);
        mBuildText.setText(versionCodeToDate(BuildConfig.VERSION_CODE));

        ViewGroup versionLayout = findViewById(R.id.optionsLayout);
        final GestureDetector gd = new GestureDetector(getContext(), new VersionGestureListener());
        versionLayout.setOnTouchListener((view, motionEvent) -> {
            if (gd.onTouchEvent(motionEvent)) {
                return true;
            }
            return view.onTouchEvent(motionEvent);
        });

        HoneycombButton reportButton = findViewById(R.id.reportButton);
        reportButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            onSettingsReportClick();
        });

        HoneycombButton developerOptionsButton = findViewById(R.id.developerOptionsButton);
        developerOptionsButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            onDeveloperOptionsClick();
        });

        HoneycombButton controllerOptionsButton = findViewById(R.id.controllerOptionsButton);
        controllerOptionsButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            showControllerOptionsDialog();
        });

        mAudio = AudioEngine.fromContext(aContext);
    }

    @Override
    public void releaseWidget() {
        mWidgetManager.removeFocusChangeListener(this);

        super.releaseWidget();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.settings_width);
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.settings_height);
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.5f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.translationY = WidgetPlacement.unitFromMeters(getContext(), R.dimen.settings_world_y);
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.settings_world_z);
    }

    private void onSettingsCrashReportingChange(boolean isEnabled) {
        SettingsStore.getInstance(getContext()).setCrashReportingEnabled(isEnabled);
    }

    private void onSettingsTelemetryChange(boolean isEnabled) {
        SettingsStore.getInstance(getContext()).setTelemetryEnabled(isEnabled);
        // TODO: Waiting for Telemetry to be merged
    }

    private void onSettingsPrivacyClick() {
        mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);
        hide(REMOVE_WIDGET);
        UIWidget widget = getChild(mPrivacyOptionsDialogHandle);
        if (widget == null) {
            widget = createChild(PrivacyOptionsWidget.class, false);
            mPrivacyOptionsDialogHandle = widget.getHandle();
            widget.setDelegate(() -> onOptionsDialogDismissed());
        }

        widget.show();
    }

    private void onSettingsReportClick() {
        String url = SessionStore.get().getCurrentUri();

        GeckoSession session = SessionStore.get().getCurrentSession();
        if (session == null) {
            int sessionId = SessionStore.get().createSession();
            SessionStore.get().setCurrentSession(sessionId);
        }

        try {
            if (url == null) {
                // In case the user had no active sessions when reporting, just leave the URL field empty.
                url = "";
            } else if (url.startsWith("jar:") || url.startsWith("resource:") || url.startsWith("about:")) {
                url = "";
            } else if (SessionStore.get().isHomeUri(url)) {
                // Use the original URL (without any hash).
                url = SessionStore.get().getHomeUri();
            }

            url = URLEncoder.encode(url, "UTF-8");

        } catch (UnsupportedEncodingException e) {
            Log.e(LOGTAG, "Cannot encode URL");
        }
        SessionStore.get().loadUri(getContext().getString(R.string.private_report_url, url));

        hide(REMOVE_WIDGET);
    }

    private void onDeveloperOptionsClick() {
        showDeveloperOptionsDialog();
    }

    private void onLanguageOptionsClick() {
        showLanguageOptionsDialog();
    }

    private void onDisplayOptionsClick() {
        showDisplayOptionsDialog();
    }

    /**
     * The version code is composed like: yDDDHHmm
     *  * y   = Double digit year, with 16 substracted: 2017 -> 17 -> 1
     *  * DDD = Day of the year, pad with zeros if needed: September 6th -> 249
     *  * HH  = Hour in day (00-23)
     *  * mm  = Minute in hour
     *
     * For September 6th, 2017, 9:41 am this will generate the versionCode: 12490941 (1-249-09-41).
     *
     * For local debug builds we use a fixed versionCode to not mess with the caching mechanism of the build
     * system. The fixed local build number is 1.
     *
     * @param aVersionCode Application version code minus the leading architecture digit.
     * @return String The converted date in the format yyyy-MM-dd
     */
    private String versionCodeToDate(final int aVersionCode) {
        String versionCode = Integer.toString(aVersionCode);

        String formatted;
        try {
            int year = Integer.parseInt(versionCode.substring(0, 1)) + 2016;
            int dayOfYear = Integer.parseInt(versionCode.substring(1, 4));

            GregorianCalendar cal = (GregorianCalendar)GregorianCalendar.getInstance();
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.DAY_OF_YEAR, dayOfYear);

            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            formatted = format.format(cal.getTime());

        } catch (StringIndexOutOfBoundsException e) {
            formatted = getContext().getString(R.string.settings_version_developer);
        }

        return formatted;
    }

    private void showDeveloperOptionsDialog() {
        mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);
        hide(REMOVE_WIDGET);
        UIWidget widget = getChild(mDeveloperOptionsDialogHandle);
        if (widget == null) {
            widget = createChild(DeveloperOptionsWidget.class, false);
            mDeveloperOptionsDialogHandle = widget.getHandle();
            widget.setDelegate(() -> onOptionsDialogDismissed());
        }

        widget.show();
    }

    private void showControllerOptionsDialog() {
        mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);
        hide(REMOVE_WIDGET);
        UIWidget widget = getChild(mControllerOptionsDialogHandle);
        if (widget == null) {
            widget = createChild(ControllerOptionsWidget.class, false);
            mControllerOptionsDialogHandle = widget.getHandle();
            widget.setDelegate(() -> onOptionsDialogDismissed());
        }

        widget.show();
    }

    private void showLanguageOptionsDialog() {
        mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);
        hide(UIWidget.REMOVE_WIDGET);
        UIWidget widget = getChild(mLanguageOptionsDialogHandle);
        if (widget == null) {
            widget = createChild(VoiceSearchLanguageOptionsWidget.class, false);
            mLanguageOptionsDialogHandle = widget.getHandle();
            widget.setDelegate(() -> onOptionsDialogDismissed());
        }

        widget.show();
    }

    private void showDisplayOptionsDialog() {
        mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);
        hide(UIWidget.REMOVE_WIDGET);
        UIWidget widget = getChild(mDisplayOptionsDialogHandle);
        if (widget == null) {
            widget = createChild(DisplayOptionsWidget.class, false);
            mDisplayOptionsDialogHandle = widget.getHandle();
            widget.setDelegate(() -> onOptionsDialogDismissed());
        }

        widget.show();
    }

    private void showEnvironmentOptionsDialog() {
        mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);
        hide(UIWidget.REMOVE_WIDGET);
        UIWidget widget = getChild(mEnvironmentOptionsDialogHandle);
        if (widget == null) {
            widget = createChild(EnvironmentOptionsWidget.class, false);
            mEnvironmentOptionsDialogHandle = widget.getHandle();
            widget.setDelegate(() -> onOptionsDialogDismissed());
        }

        widget.show();
    }

    private void onOptionsDialogDismissed() {
        mWidgetManager.popWorldBrightness(this);
        show();
    }

    // WindowManagerDelegate.FocusChangeListener
    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (oldFocus == this && isVisible()) {
            onDismiss();
        }
    }

    @Override
    public void show() {
        super.show();

        mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);
    }

    @Override
    public void hide(@HideFlags int aHideFlags) {
        super.hide(aHideFlags);

        mWidgetManager.popWorldBrightness(this);
    }

}
