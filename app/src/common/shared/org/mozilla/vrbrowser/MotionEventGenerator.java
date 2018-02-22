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
        boolean wasPressed;
        long downTime;
        MotionEvent.PointerProperties properties[];
        MotionEvent.PointerCoords coords[];

        Device() {
            properties = new MotionEvent.PointerProperties[1];
            properties[0] = new MotionEvent.PointerProperties();
            properties[0].id = 0;
            properties[0].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords = new MotionEvent.PointerCoords[1];
            coords[0] = new MotionEvent.PointerCoords();
            coords[0].toolMajor = 2;
            coords[0].toolMinor = 2;
            coords[0].touchMajor = 2;
            coords[0].touchMinor = 2;
        }
    }

    private static SparseArray<Device> devices = new SparseArray<Device>();

    static void dispatch(Widget aWidget, int aDevice, boolean aPressed, int aX, int aY) {
        Device device = devices.get(aDevice);
        if (device == null) {
            device = new Device();
            devices.put(aDevice, device);
        }
        int action = 0;
        boolean moving = (device.coords[0].x != aX) || (device.coords[0].y != aY);
        boolean hover = false;
        device.coords[0].x = aX;
        device.coords[0].y = aY;
        if (aPressed) {
            device.coords[0].pressure = 1.0f;
        } else {
            device.coords[0].pressure = 0.0f;
        }
        if (aPressed && !device.wasPressed) {
            device.downTime = SystemClock.uptimeMillis();
            device.wasPressed = true;
            action |= MotionEvent.ACTION_DOWN;
        } else if (!aPressed && device.wasPressed) {
            device.wasPressed = false;
            action |= MotionEvent.ACTION_UP;
        } else if (moving && aPressed) {
            action |= MotionEvent.ACTION_MOVE;
        } else if (moving && !aPressed) {
            action |= MotionEvent.ACTION_HOVER_MOVE;
            hover = true;
        } else {
            Log.e("VRB", "Unknown touch event action");
            return;
        }
        MotionEvent event = MotionEvent.obtain(
                /*downTime*/ device.downTime,
                /*eventTime*/ SystemClock.uptimeMillis(),
                /*action*/ action,
                /*pointerCount*/ 1,
                /*pointerProperties*/ device.properties,
                /*pointerCoords*/ device.coords,
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
