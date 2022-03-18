package com.igalia.wolvic.browser.api.impl;

import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WPanZoomController;

public class PanZoomCrontrollerImpl implements WPanZoomController {
    SessionImpl mSession;

    public PanZoomCrontrollerImpl(SessionImpl session) {
        mSession = session;
    }

    @Override
    public void onTouchEvent(@NonNull MotionEvent event) {
        @Nullable BrowserDisplay display = getDisplay();
        if (display != null) {
            display.dispatchTouchEvent(event);
        }
    }

    @Override
    public void onMotionEvent(@NonNull MotionEvent event) {
        @Nullable BrowserDisplay display = getDisplay();
        if (display != null) {
            display.dispatchGenericMotionEvent(event);
        }
    }

    private @Nullable
    BrowserDisplay getDisplay() {
        if (mSession == null) {
            return null;
        }
        DisplayImpl display = mSession.getDisplay();
        if (display == null) {
            return  null;
        }
        return display.getBrowserDisplay();
    }
}
