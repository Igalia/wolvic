/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.WidgetPlacement;
import org.mozilla.vrbrowser.audio.AudioEngine;

public class CrashReportingWidget extends UIWidget {
    private static final String LOGTAG = "VRB";

    private TextView mAcceptButton;
    private UIButton mCancelButton;
    private AudioEngine mAudio;
    private Runnable mBackHandler;

    public CrashReportingWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public CrashReportingWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public CrashReportingWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.crash_reporting, this);

        mAcceptButton = findViewById(R.id.crashAcceptButton);
        mCancelButton = findViewById(R.id.crashCancelButton);

        mAcceptButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }

                getHandler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        handleRestartApp();
                    }
                }, 500);
            }
        });
        mCancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }

                hide();
            }
        });

        mAudio = AudioEngine.fromContext(aContext);

        mBackHandler = new Runnable() {
            @Override
            public void run() {
                hide();
            }
        };
    }

    @Override
    void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.crash_width);
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.crash_height);
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.5f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.crash_world_z);
    }

    public void show() {
        getPlacement().visible = true;
        mWidgetManager.addWidget(this);
        mWidgetManager.pushBackHandler(mBackHandler);
    }

    public void hide() {
        mWidgetManager.removeWidget(this);
        mWidgetManager.popBackHandler(mBackHandler);
    }

    private void handleRestartApp() {
        Intent i = getContext().getApplicationContext().getPackageManager()
                .getLaunchIntentForPackage(getContext().getApplicationContext().getPackageName());
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);

        PendingIntent mPendingIntent = PendingIntent.getActivity(getContext(), 0, i,
                PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager mgr = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);

        ((Activity)getContext()).finish();
    }
}
