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
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.SessionStore;
import org.mozilla.vrbrowser.WidgetPlacement;
import org.mozilla.vrbrowser.audio.AudioEngine;

public class TrayWidget extends UIWidget implements SessionStore.SessionChangeListener {
    private static final String LOGTAG = "VRB";

    private UIButton mHelpButton;
    private UIButton mSettingsButton;
    private UIButton mPrivateButton;
    private AudioEngine mAudio;
    private int mSettingsDialogHandle = -1;
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

                onHelpButtonClicked();
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

                showSettingsDialog();
            }
        });

        mAudio = AudioEngine.fromContext(aContext);

        mIsLastSessionPrivate = false;

        SessionStore.get().addSessionChangeListener(this);
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
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

    private void showSettingsDialog() {
        UIWidget widget = getChild(mSettingsDialogHandle);
        if (widget == null) {
            widget = createChild(SettingsWidget.class, false);
            mSettingsDialogHandle = widget.getHandle();
        }

        widget.toggle();
    }

    public boolean isSettingsDialogOpened() {
        UIWidget widget = getChild(mSettingsDialogHandle);
        if (widget != null) {
            return widget.isOpened();
        }

        return false;
    }

    @Override
    public void show() {
        if (!mWidgetPlacement.visible) {
            mWidgetPlacement.visible = true;
            mWidgetManager.addWidget(this);
        }
    }

    @Override
    public void hide() {
        if (mWidgetPlacement.visible) {
            mWidgetPlacement.visible = false;
            mWidgetManager.removeWidget(this);
        }
    }

    private void onHelpButtonClicked() {
        GeckoSession session = SessionStore.get().getCurrentSession();
        if (session == null) {
            int sessionId = SessionStore.get().createSession();
            SessionStore.get().setCurrentSession(sessionId);
        }

        SessionStore.get().loadUri(getContext().getString(R.string.help_url));
    }

}
