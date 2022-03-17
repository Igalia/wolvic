package com.igalia.wolvic.browser.api.impl;

import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WPanZoomController;
import com.wpe.wpeview.WPEView;

public class PanZoomCrontrollerImpl implements WPanZoomController {
    SessionImpl mSession;

    public PanZoomCrontrollerImpl(SessionImpl session) {
        mSession = session;
    }

    @Override
    public void onTouchEvent(@NonNull MotionEvent event) {
        @Nullable View view = getViewForEvents();
        if (view != null) {
            view.dispatchTouchEvent(event);
        }
    }

    @Override
    public void onMotionEvent(@NonNull MotionEvent event) {
        @Nullable View view = getViewForEvents();
        if (view != null) {
            view.dispatchGenericMotionEvent(event);
        }
    }

    private @Nullable
    View getViewForEvents() {
        if (mSession == null) {
            return null;
        }
        return mSession.mWPEView.getGfxView();
    }
}
