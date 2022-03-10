package com.igalia.wolvic.browser.api.impl;

import android.view.MotionEvent;

import androidx.annotation.NonNull;

import com.igalia.wolvic.browser.api.WPanZoomController;

import org.mozilla.geckoview.GeckoSession;

class PanZoomControllerImpl implements WPanZoomController {
    GeckoSession mSession;

    public PanZoomControllerImpl(GeckoSession session) {
        mSession = session;
    }

    @Override
    public void onTouchEvent(@NonNull MotionEvent event) {
        mSession.getPanZoomController().onTouchEvent(event);
    }

    @Override
    public void onMotionEvent(@NonNull MotionEvent event) {
        mSession.getPanZoomController().onMotionEvent(event);
    }
}
