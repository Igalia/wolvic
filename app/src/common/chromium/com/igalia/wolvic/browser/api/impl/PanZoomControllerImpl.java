package com.igalia.wolvic.browser.api.impl;

import android.view.MotionEvent;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.igalia.wolvic.browser.api.WPanZoomController;

public class PanZoomControllerImpl implements WPanZoomController {
    SessionImpl mSession;

    public PanZoomControllerImpl(SessionImpl session) {
        mSession = session;
    }

    @Override
    public void onTouchEvent(@NonNull MotionEvent event) {
        getContentView().dispatchTouchEvent(event);
    }

    @Override
    public void onMotionEvent(@NonNull MotionEvent event) {
        getContentView().dispatchGenericMotionEvent(event);
    }

    private ViewGroup getContentView() {
        assert mSession != null;
        return mSession.getContentView();
    }
}
