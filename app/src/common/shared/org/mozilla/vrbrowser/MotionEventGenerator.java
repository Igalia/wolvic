/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.util.SparseArray;

class MotionEventGenerator {
    static class Device {
        Widget mPreviousWidget = null;
        boolean mWasPressed;
        long mDownTime;
        MotionEvent.PointerProperties mProperties[];
        MotionEvent.PointerCoords mCoords[];

        Device() {
            mProperties = new MotionEvent.PointerProperties[1];
            mProperties[0] = new MotionEvent.PointerProperties();
            mProperties[0].id = 0;
            mProperties[0].toolType = MotionEvent.TOOL_TYPE_FINGER;
            mCoords = new MotionEvent.PointerCoords[1];
            mCoords[0] = new MotionEvent.PointerCoords();
            mCoords[0].toolMajor = 2;
            mCoords[0].toolMinor = 2;
            mCoords[0].touchMajor = 2;
            mCoords[0].touchMinor = 2;
        }
    }

    private static SparseArray<Device> devices = new SparseArray<Device>();

    static void dispatch(Widget aWidget, int aDevice, boolean aPressed, float aX, float aY) {
        Device device = devices.get(aDevice);
        if (device == null) {
            device = new Device();
            devices.put(aDevice, device);
        }
        int action = 0;
        boolean moving = (device.mCoords[0].x != aX) || (device.mCoords[0].y != aY);
        boolean hover = false;
        float previousX = device.mCoords[0].x;
        float previousY = device.mCoords[0].y;
        device.mCoords[0].x = aX;
        device.mCoords[0].y = aY;
        if (aPressed) {
            device.mCoords[0].pressure = 1.0f;
        } else {
            device.mCoords[0].pressure = 0.0f;
        }
        if (aPressed && !device.mWasPressed) {
            device.mDownTime = SystemClock.uptimeMillis();
            device.mWasPressed = true;
            action |= MotionEvent.ACTION_DOWN;
        } else if (!aPressed && device.mWasPressed) {
            device.mWasPressed = false;
            action |= MotionEvent.ACTION_UP;
        } else if (moving && aPressed) {
            action |= MotionEvent.ACTION_MOVE;
        } else if (moving && !aPressed) {
            action |= MotionEvent.ACTION_HOVER_MOVE;
            hover = true;
            if ((device.mPreviousWidget == null) || (!device.mPreviousWidget.equals(aWidget))) {
                Log.e("VRB", "HOVER ENTER!");
                action |= MotionEvent.ACTION_HOVER_ENTER;
            }
        } else {
            Log.e("VRB", "Unknown touch event action");
            return;
        }
        device.mPreviousWidget = aWidget;
        MotionEvent event = MotionEvent.obtain(
                /*mDownTime*/ device.mDownTime,
                /*eventTime*/ SystemClock.uptimeMillis(),
                /*action*/ action,
                /*pointerCount*/ 1,
                /*pointerProperties*/ device.mProperties,
                /*pointerCoords*/ device.mCoords,
                /*metaState*/ 0,
                /*buttonState*/ 0,
                /*xPrecision*/ 0,
                /*yPrecision*/ 0,
                /*deviceId*/ aDevice,
                /*edgeFlags*/ 0,
                /*source*/ InputDevice.SOURCE_TOUCHSCREEN,
                /*flags*/ 0);
        if (hover) {
            aWidget.handleHoverEvent(event);
            return;
        }
        aWidget.handleTouchEvent(event);
    }
}
