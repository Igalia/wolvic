/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.input;

import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.util.SparseArray;

import org.mozilla.vrbrowser.ui.widgets.Widget;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.util.Arrays;
import java.util.List;

public class MotionEventGenerator {
    static final String LOGTAG = SystemUtils.createLogtag(MotionEventGenerator.class);
    static class Device {
        int mDevice;
        Widget mPreviousWidget = null;
        Widget mTouchStartWidget = null;
        boolean mWasPressed;
        long mDownTime;
        MotionEvent.PointerProperties mProperties[];
        MotionEvent.PointerCoords mCoords[];
        MotionEvent.PointerCoords mMouseOutCoords[];

        Device(final int aDevice) {
            mDevice = aDevice;
            mProperties = new MotionEvent.PointerProperties[1];
            mProperties[0] = new MotionEvent.PointerProperties();
            mProperties[0].id = 0;
            mProperties[0].toolType = MotionEvent.TOOL_TYPE_FINGER;
            mCoords = new MotionEvent.PointerCoords[1];
            mCoords[0] = new MotionEvent.PointerCoords();
            mMouseOutCoords = new MotionEvent.PointerCoords[1];
            for (MotionEvent.PointerCoords[] coords : Arrays.asList(mCoords, mMouseOutCoords)) {
                coords[0] = new MotionEvent.PointerCoords();
                coords[0].toolMajor = 2;
                coords[0].toolMinor = 2;
                coords[0].touchMajor = 2;
                coords[0].touchMinor = 2;
            }
            mMouseOutCoords[0].x = -10;
            mMouseOutCoords[0].y = -10;
        }
    }

    private static SparseArray<Device> devices = new SparseArray<>();


    private static void generateEvent(Widget aWidget, Device aDevice, int aAction, boolean aGeneric) {
        generateEvent(aWidget, aDevice, aAction, aGeneric, aDevice.mCoords);
    }

    private static void generateEvent(Widget aWidget, Device aDevice, int aAction, boolean aGeneric, MotionEvent.PointerCoords[] aCoords) {
        MotionEvent event = MotionEvent.obtain(
                /*mDownTime*/ aDevice.mDownTime,
                /*eventTime*/ SystemClock.uptimeMillis(),
                /*action*/ aAction,
                /*pointerCount*/ 1,
                /*pointerProperties*/ aDevice.mProperties,
                /*pointerCoords*/ aCoords,
                /*metaState*/ 0,
                /*buttonState*/ 0,
                /*xPrecision*/ 0,
                /*yPrecision*/ 0,
                /*deviceId*/ 0, // aDevice.mDevice,
                /*edgeFlags*/ 0,
                /*source*/ InputDevice.SOURCE_TOUCHSCREEN,
                /*flags*/ 0);
        if (aGeneric) {
            aWidget.handleHoverEvent(event);
        } else {
            aWidget.handleTouchEvent(event);
        }
        event.recycle();
    }

    public static void dispatch(Widget aWidget, int aDevice, boolean aPressed, float aX, float aY) {
        Device device = devices.get(aDevice);
        if (device == null) {
            device = new Device(aDevice);
            devices.put(aDevice, device);
        }
        boolean moving = (device.mCoords[0].x != aX) || (device.mCoords[0].y != aY);
        if (aWidget != null) {
            device.mCoords[0].x = aX;
            device.mCoords[0].y = aY;
            if (aPressed) {
                device.mCoords[0].pressure = 1.0f;
            } else {
                device.mCoords[0].pressure = 0.0f;
            }
        }
        if (!aPressed && (device.mPreviousWidget != null) && (device.mPreviousWidget != aWidget)) {
            if (device.mWasPressed) {
                generateEvent(device.mPreviousWidget, device, MotionEvent.ACTION_CANCEL, false);
                device.mWasPressed = false;
            }
            generateEvent(device.mPreviousWidget, device, MotionEvent.ACTION_HOVER_EXIT, true, device.mMouseOutCoords);
            device.mPreviousWidget = null;
        }
        if (aWidget == null) {
            device.mPreviousWidget = null;
            return;
        }
        if (aWidget != device.mPreviousWidget && !aPressed) {
            generateEvent(aWidget, device, MotionEvent.ACTION_HOVER_ENTER, true);
        }
        if (aPressed && !device.mWasPressed) {
            device.mDownTime = SystemClock.uptimeMillis();
            device.mWasPressed = true;
            generateEvent(aWidget, device, MotionEvent.ACTION_HOVER_EXIT, true);
            generateEvent(aWidget, device, MotionEvent.ACTION_DOWN, false);
            device.mTouchStartWidget = aWidget;
        } else if (!aPressed && device.mWasPressed) {
            device.mWasPressed = false;
            generateEvent(device.mTouchStartWidget, device, MotionEvent.ACTION_UP, false);
            generateEvent(aWidget, device, MotionEvent.ACTION_HOVER_ENTER, true);
        } else if (moving && aPressed) {
            generateEvent(aWidget, device, MotionEvent.ACTION_MOVE, false);
        } else if (moving) {
            generateEvent(aWidget, device, MotionEvent.ACTION_HOVER_MOVE, true);
        } else {
            Log.e("VRB", "Unknown touch event action");
            return;
        }
        device.mPreviousWidget = aWidget;
    }

    public static void dispatchScroll(Widget aWidget, int aDevice, float aX, float aY) {
        Device device = devices.get(aDevice);
        if (device == null) {
            device = new Device(aDevice);
            devices.put(aDevice, device);
        }
        device.mPreviousWidget = aWidget;
        device.mCoords[0].setAxisValue(MotionEvent.AXIS_VSCROLL, aY);
        device.mCoords[0].setAxisValue(MotionEvent.AXIS_HSCROLL, aX);
        generateEvent(aWidget, device, MotionEvent.ACTION_SCROLL, true);
        device.mCoords[0].setAxisValue(MotionEvent.AXIS_VSCROLL, 0.0f);
        device.mCoords[0].setAxisValue(MotionEvent.AXIS_HSCROLL, 0.0f);
    }

    public static void clearDevices() {
        devices.clear();
    }
}
