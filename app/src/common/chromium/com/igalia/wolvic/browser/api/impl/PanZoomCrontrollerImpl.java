package com.igalia.wolvic.browser.api.impl;

import android.view.MotionEvent;

import androidx.annotation.NonNull;

import com.igalia.wolvic.browser.api.WPanZoomController;

public class PanZoomCrontrollerImpl implements WPanZoomController {
    SessionImpl mSession;

    public PanZoomCrontrollerImpl(SessionImpl session) {
        mSession = session;
    }

    @Override
    public void onTouchEvent(@NonNull MotionEvent event) {
        // TODO: implement
    }

    @Override
    public void onMotionEvent(@NonNull MotionEvent event) {
        // TODO: implement
    }
}
