/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.vrbrowser.*;
import org.mozilla.vrbrowser.audio.AudioEngine;

public class TrayWidget extends UIWidget implements SessionStore.SessionChangeListener {
    private static final String LOGTAG = "VRB";

    private UIButton mHelpButton;
    private UIButton mSettingsButton;
    private UIButton mPrivateButton;
    private AudioEngine mAudio;
    private SettingsWidget mSettingsWidget;
    private boolean mIsLastSessionPrivate;

    public TrayWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public TrayWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public TrayWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.tray, this);

        mHelpButton = findViewById(R.id.helpButton);
        mHelpButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }
            }
        });

        mPrivateButton = findViewById(R.id.privateButton);
        mPrivateButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }

                SessionStore.get().switchPrivateMode();
            }
        });

        mSettingsButton = findViewById(R.id.settingsButton);
        mSettingsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }

                if (mSettingsWidget == null) {
                    mSettingsWidget = new SettingsWidget(getContext());
                }
                mSettingsWidget.toggle();
            }
        });

        mAudio = AudioEngine.fromContext(aContext);

        mIsLastSessionPrivate = false;

        SessionStore.get().addSessionChangeListener(this);
    }

    @Override
    void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        Context context = getContext();
        aPlacement.width = WidgetPlacement.dpDimension(context, R.dimen.tray_width);
        aPlacement.height = WidgetPlacement.dpDimension(context, R.dimen.tray_height);
        aPlacement.worldWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.tray_world_width);
        aPlacement.translationY = WidgetPlacement.unitFromMeters(context, R.dimen.tray_world_y);
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(context, R.dimen.tray_world_z);
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.5f;
        aPlacement.rotationAxisX = 1.0f;
        aPlacement.rotation = -45.0f;
        aPlacement.opaque = false;
    }

    @Override
    public void releaseWidget() {
        SessionStore.get().removeSessionChangeListener(this);

        super.releaseWidget();
    }

    // SessionStore.SessionChangeListener

    @Override
    public void onNewSession(GeckoSession aSession, int aId) {

    }

    @Override
    public void onRemoveSession(GeckoSession aSession, int aId) {

    }

    @Override
    public void onCurrentSessionChange(GeckoSession aSession, int aId) {
        boolean isPrivateMode  = aSession.getSettings().getBoolean(GeckoSessionSettings.USE_PRIVATE_MODE);

        if (isPrivateMode != mIsLastSessionPrivate) {
            mPrivateButton.setPrivateMode(isPrivateMode);
            if (isPrivateMode) {
                mWidgetManager.fadeOutWorld();
                mPrivateButton.setImageResource(R.drawable.ic_tray_private_on);

            } else {
                mWidgetManager.fadeInWorld();
                mPrivateButton.setImageResource(R.drawable.ic_tray_private);
            }
        }

        mIsLastSessionPrivate = isPrivateMode;
    }

    public void setVisible(boolean isVisible) {
        getPlacement().visible = isVisible;

        if (isVisible)
            mWidgetManager.addWidget(this);
        else
            mWidgetManager.removeWidget(this);
    }

}
