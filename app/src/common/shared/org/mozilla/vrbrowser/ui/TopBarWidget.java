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

public class TopBarWidget extends UIWidget implements SessionStore.SessionChangeListener {
    private static final String LOGTAG = "VRB";

    public interface TopBarDelegate {
        void onCloseClicked();
    }

    private NavigationBarButton mCloseButton;
    private TopBarDelegate mDelegate;
    private AudioEngine mAudio;

    public TopBarWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public TopBarWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public TopBarWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.top_bar, this);

        mCloseButton = findViewById(R.id.closeButton);
        mCloseButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }

                if (mDelegate != null)
                    mDelegate.onCloseClicked();
            }
        });

        mAudio = AudioEngine.fromContext(aContext);

        SessionStore.get().addSessionChangeListener(this);
    }

    @Override
    void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        Context context = getContext();
        aPlacement.width = WidgetPlacement.dpDimension(context, R.dimen.top_bar_width);
        aPlacement.height = WidgetPlacement.dpDimension(context, R.dimen.top_bar_height);
        aPlacement.worldWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.top_bar_world_width);
        aPlacement.translationY = WidgetPlacement.unitFromMeters(context, R.dimen.top_bar_world_y);
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 1.0f;
    }

    @Override
    public void releaseWidget() {
        SessionStore.get().removeSessionChangeListener(this);

        super.releaseWidget();
    }

    private void setPrivateBrowsingEnabled(boolean isEnabled) {
        if (isEnabled) {
            mCloseButton.setBackground(getContext().getDrawable(R.drawable.main_button_private));
            mCloseButton.setTintColorList(R.drawable.main_button_icon_color_private);

        } else {
            mCloseButton.setBackground(getContext().getDrawable(R.drawable.main_button));
            mCloseButton.setTintColorList(R.drawable.main_button_icon_color);
        }
    }

    // SessionStore.SessionChangeListener
    public void setDelegate(TopBarDelegate aDelegate) {
        mDelegate = aDelegate;
    }

    @Override
    public void onNewSession(GeckoSession aSession, int aId) {

    }

    @Override
    public void onRemoveSession(GeckoSession aSession, int aId) {

    }

    @Override
    public void onCurrentSessionChange(GeckoSession aSession, int aId) {
        boolean isPrivateMode  = aSession.getSettings().getBoolean(GeckoSessionSettings.USE_PRIVATE_MODE);
        if (isPrivateMode)
            setPrivateBrowsingEnabled(true);
        else
            setPrivateBrowsingEnabled(false);
    }
}
