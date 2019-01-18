package org.mozilla.servo;

import android.graphics.Rect;
import android.util.Pair;
import android.view.InputDevice;
import android.view.MotionEvent;

import org.mozilla.geckoview.PanZoomController;

import java.util.ArrayList;

public class ServoPanZoomController extends PanZoomController {

    private static final int EVENT_SOURCE_SCROLL = 0;
    private static final int EVENT_SOURCE_MOTION = 1;
    private static final int EVENT_SOURCE_MOUSE = 2;

    private final Rect mTempRect = new Rect();
    private boolean mAttached;
    private float mPointerScrollFactor = 64.0f;
    private long mLastDownTime;
    private boolean mIsScrolling = false;
    private final ServoSession mSession;
    private ArrayList<Pair<Integer, MotionEvent>> mQueuedEvents;

    ServoPanZoomController(ServoSession session) {
        super(null);
        mSession = session;
        enableEventQueue();
        setAttached(true);
    }

    private boolean handleMotionEvent(MotionEvent event) {
        if (!mAttached) {
            mQueuedEvents.add(new Pair(EVENT_SOURCE_MOTION, event));
            return false;
        }

        if (event.getPointerCount() <= 0) {
            return false;
        }

        final int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            mLastDownTime = event.getDownTime();
        } else if (mLastDownTime != event.getDownTime()) {
            return false;
        }

        final MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        event.getPointerCoords(0, coords);

        if (action == MotionEvent.ACTION_UP) {
            mSession.click((int) coords.x, (int) coords.y);
        }

        return true;
    }

    private boolean handleScrollEvent(MotionEvent event) {
        if (!mAttached) {
            mQueuedEvents.add(new Pair(EVENT_SOURCE_SCROLL, event));
            return false;
        }

        if (event.getPointerCount() <= 0) {
            return false;
        }

        final MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        event.getPointerCoords(0, coords);

        mSession.getSurfaceBounds(mTempRect);
        final float x = coords.x - mTempRect.left;
        final float y = coords.y - mTempRect.top;

        final float hScroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL) * mPointerScrollFactor;
        final float vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL) * mPointerScrollFactor;

        if (!mIsScrolling) {
            mSession.scrollStart((int) hScroll, (int) vScroll, (int) x, (int) y);
        } else {
            mSession.scroll((int) hScroll, (int) vScroll, (int) x, (int) y);
        }
        mIsScrolling = true;
        return true;
    }

    private boolean handleMouseEvent(MotionEvent event) {
        // FIXME: treat these events as mouse events
        return handleMotionEvent(event);
    }

    @Override
    public boolean onMouseEvent(final MotionEvent event) {
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) {
            return handleMouseEvent(event);
        }
        return handleMotionEvent(event);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        return handleMotionEvent(event);
    }

    @Override
    public boolean onMotionEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_SCROLL) {
            if (event.getDownTime() >= mLastDownTime) {
                mLastDownTime = event.getDownTime();
            } else if ((InputDevice.getDevice(event.getDeviceId()).getSources() &
                    InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD) {
                return false;
            }
            return handleScrollEvent(event);
        } else if ((action == MotionEvent.ACTION_HOVER_MOVE) ||
            (action == MotionEvent.ACTION_HOVER_ENTER) ||
            (action == MotionEvent.ACTION_HOVER_EXIT)) {
          return onMouseEvent(event);
        } else {
            return false;
        }
    }

    @Override
    public void setScrollFactor(final float factor) {
        mPointerScrollFactor = factor;
    }

    @Override
    public float getScrollFactor() {
        return mPointerScrollFactor;
    }

    @Override
    public void setIsLongpressEnabled(boolean isLongpressEnabled) {
        // FIXME: Not supported by Servo
    }

    private void enableEventQueue() {
        if (mQueuedEvents != null) {
            throw new IllegalStateException("Already have an event queue");
        }
        mQueuedEvents = new ArrayList();
    }

    private void flushEventQueue() {
        if (mQueuedEvents == null) {
            return;
        }

        ArrayList<Pair<Integer, MotionEvent>> events = mQueuedEvents;
        mQueuedEvents = null;
        for (Pair<Integer, MotionEvent> pair : events) {
            switch (pair.first) {
                case EVENT_SOURCE_MOTION:
                    handleMotionEvent(pair.second);
                    break;
                case EVENT_SOURCE_SCROLL:
                    handleScrollEvent(pair.second);
                    break;
                case EVENT_SOURCE_MOUSE:
                    handleMouseEvent(pair.second);
                    break;

            }
        }
    }

    private void setAttached(final boolean attached) {
        if (attached) {
            mAttached = true;
            flushEventQueue();
        } else if (mAttached) {
            mAttached = false;
            enableEventQueue();
        }
    }
}
