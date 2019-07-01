/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets;

import android.content.Context;
import android.util.AttributeSet;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.SessionChangeListener;
import org.mozilla.vrbrowser.browser.engine.SessionManager;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.ui.views.UIButton;

public class TopBarWidget extends UIWidget implements SessionChangeListener, WidgetManagerDelegate.UpdateListener {

    private UIButton mCloseButton;
    private AudioEngine mAudio;
    private UIWidget mBrowserWidget;
    private SessionStore mSessionStore;

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

        mCloseButton = findViewById(R.id.negativeButton);
        mCloseButton.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            mSessionStore.exitPrivateMode();
        });

        mAudio = AudioEngine.fromContext(aContext);

        mWidgetManager.addUpdateListener(this);
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        Context context = getContext();
        aPlacement.width = WidgetPlacement.dpDimension(context, R.dimen.top_bar_width);
        aPlacement.height = WidgetPlacement.dpDimension(context, R.dimen.top_bar_height);
        aPlacement.worldWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.window_world_width) * aPlacement.width/getWorldWidth();
        //aPlacement.translationY = WidgetPlacement.unitFromMeters(context, R.dimen.top_bar_world_y);
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.0f;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 1.0f;
        aPlacement.opaque = false;
    }

    @Override
    public void releaseWidget() {
        if (mSessionStore != null) {
            mSessionStore.removeSessionChangeListener(this);
        }

        mWidgetManager.removeUpdateListener(this);

        super.releaseWidget();
    }

    @Override
    public void detachFromWindow(WindowWidget window) {
        if (mSessionStore != null) {
            mSessionStore.removeSessionChangeListener(this);
        }
    }

    @Override
    public void attachToWindow(WindowWidget window) {
        setBrowserWidget(window);

        mSessionStore = window.getSessionStore();
        if (mSessionStore != null) {
            mSessionStore.addSessionChangeListener(this);
            handleSessionState();
        }
    }

    public void setBrowserWidget(UIWidget aWidget) {
        if (aWidget != null) {
            mWidgetPlacement.parentHandle = aWidget.getHandle();
        }
        mBrowserWidget = aWidget;
    }

    // SessionStore.SessionChangeListener

    @Override
    public void onCurrentSessionChange(GeckoSession aSession, int aId) {
        handleSessionState();
    }

    @Override
    public void setVisible(boolean isVisible) {
        getPlacement().visible = isVisible;

        if (isVisible)
            mWidgetManager.addWidget(this);
        else
            mWidgetManager.removeWidget(this);
    }

    private void handleSessionState() {
        if (mSessionStore != null) {
            boolean isPrivateMode = mSessionStore.isPrivateMode();
            setVisible(isPrivateMode);
            mCloseButton.setPrivateMode(isPrivateMode);
        }
    }

    // WidgetManagerDelegate.UpdateListener

    @Override
    public void onWidgetUpdate(Widget aWidget) {
        if (aWidget != mBrowserWidget) {
            return;
        }

        if (mBrowserWidget.isVisible()) {
            boolean isVisible = isVisible();
            boolean mustBeVisible = mSessionStore.isPrivateMode();
            if (mustBeVisible && !isVisible) {
                setVisible(true);
            } else if (isVisible) {
                mWidgetManager.updateWidget(this);
            }
        } else {
            setVisible(false);
        }
    }

}
