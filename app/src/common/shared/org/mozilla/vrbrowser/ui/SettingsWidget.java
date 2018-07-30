/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.*;
import org.mozilla.vrbrowser.audio.AudioEngine;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public class SettingsWidget extends UIWidget {
    private static final String LOGTAG = "VRB";

    private AudioEngine mAudio;
    private CrashReportingWidget mCrashReportingWidget;
    private Runnable mBackHandler;

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

        ImageButton cancelButton = findViewById(R.id.settingsCancelButton);

        cancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }

                toggle();
            }
        });

        SettingsButton privacyButton = findViewById(R.id.privacyButton);
        privacyButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }

                onSettingsPrivacyClick();
            }
        });

        Switch crashReportingSwitch  = findViewById(R.id.crash_reporting_switch);
        crashReportingSwitch.setChecked(SettingsStore.getInstance(getContext()).isCrashReportingEnabled());
        crashReportingSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }

                onSettingsCrashReportingChange(b);
            }
        });

        Switch telemetrySwitch  = findViewById(R.id.telemetry_switch);
        telemetrySwitch.setChecked(SettingsStore.getInstance(getContext()).isTelemetryEnabled());
        telemetrySwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }

                onSettingsTelemetryChange(b);
            }
        });

        TextView versionText = findViewById(R.id.versionText);
        try {
            PackageInfo pInfo = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0);
            versionText.setText(String.format(getResources().getString(R.string.settings_version), pInfo.versionName, versionCodeToDate(BuildConfig.VERSION_CODE)));

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        SettingsButton reportButton = findViewById(R.id.reportButton);
        reportButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }

                onSettingsReportClick();
            }
        });

        mAudio = AudioEngine.fromContext(aContext);

        mBackHandler = new Runnable() {
            @Override
            public void run() {
                toggle();
            }
        };
    }

    @Override
    void initializeWidgetPlacement(WidgetPlacement aPlacement) {
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

    public void toggle() {
        if (getPlacement().visible) {
            getPlacement().visible = false;
            mWidgetManager.removeWidget(this);
            mWidgetManager.popBackHandler(mBackHandler);

            if (mCrashReportingWidget != null) {
                mCrashReportingWidget.hide();
            }

        } else {
            getPlacement().visible = true;
            mWidgetManager.addWidget(this);
            mWidgetManager.pushBackHandler(mBackHandler);
        }
    }

    private void onSettingsCrashReportingChange(boolean isEnabled) {
        SettingsStore.getInstance(getContext()).setCrashReportingEnabled(isEnabled);

        if (mCrashReportingWidget == null) {
            mCrashReportingWidget = new CrashReportingWidget(getContext());
            mCrashReportingWidget.getPlacement().parentHandle = getHandle();
        }

        mCrashReportingWidget.show();
    }

    private void onSettingsTelemetryChange(boolean isEnabled) {
        SettingsStore.getInstance(getContext()).setTelemetryEnabled(isEnabled);
        // TODO: Waiting for Telemetry to be merged
    }

    private void onSettingsPrivacyClick() {
        SessionStore.SessionSettings settings = new SessionStore.SessionSettings();
        GeckoSession session = SessionStore.get().getCurrentSession();
        if (session == null) {
            int sessionId = SessionStore.get().createSession(settings);
            SessionStore.get().setCurrentSession(sessionId);
        }

        SessionStore.get().loadUri(getContext().getString(R.string.private_policy_url));

        toggle();
    }

    private void onSettingsReportClick() {
        String url = SessionStore.get().getCurrentUri();

        SessionStore.SessionSettings settings = new SessionStore.SessionSettings();
        GeckoSession session = SessionStore.get().getCurrentSession();
        if (session == null) {
            int sessionId = SessionStore.get().createSession(settings);
            SessionStore.get().setCurrentSession(sessionId);
        }

        try {
            // In case the user had no active sessions when reporting just leave the URL field empty
            url = (url != null) ? URLEncoder.encode(url, "UTF-8") : "";

        } catch (UnsupportedEncodingException e) {
            Log.e(LOGTAG, "Cannot encode URL");
        }
        SessionStore.get().loadUri(getContext().getString(R.string.private_report_url, url));

        toggle();
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
     * @param aVersionCode
     * @return String The converted date in the format yyyy-MM-dd
     */
    private String versionCodeToDate(int aVersionCode) {
        String versionCode = Integer.toString(aVersionCode);

        String formatted;
        try {
            int year = Integer.parseInt(versionCode.substring(1, 2)) + 2016;
            int dayOfYear = Integer.parseInt(versionCode.substring(2, 5));

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

}
