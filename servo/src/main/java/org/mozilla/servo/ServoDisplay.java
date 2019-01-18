package org.mozilla.servo;

import android.view.Surface;

import org.mozilla.geckoview.GeckoDisplay;
import org.mozilla.geckoview.GeckoSession;

public class ServoDisplay extends GeckoDisplay {

    private ServoSession mServoSession;

    public ServoDisplay(final GeckoSession session) {
        super(session);
        mServoSession = (ServoSession) session;
    }

    @Override
    public void surfaceChanged(final Surface surface, final int width, final int height) {
        mServoSession.onSurfaceReady(surface, 0, 0, width, height);
    }

    @Override
    public void surfaceChanged(final Surface surface, int left, int top, int width, int height) {
        mServoSession.onSurfaceReady(surface, left, top, width, height);
    }


    @Override
    public void surfaceDestroyed() {
        mServoSession.onSurfaceDestroyed();
    }

    @Override
    public void screenOriginChanged(final int left, final int top) {
    }

    @Override
    public boolean shouldPinOnScreen() {
        return false;
    }

}
