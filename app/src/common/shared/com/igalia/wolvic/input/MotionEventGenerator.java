/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.input;

import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;
import android.view.InputDevice;
import android.view.MotionEvent;

import com.igalia.wolvic.ui.widgets.Widget;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.utils.SystemUtils;

import java.util.Arrays;

public class MotionEventGenerator {
    static final String LOGTAG = SystemUtils.createLogtag(MotionEventGenerator.class);
    static class Device {
        int mDevice;
        Widget mPreviousWidget = null;
        Widget mTouchStartWidget = null;
        Widget mHoverStartWidget = null;
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


    private static void generateEvent(Widget aWidget, Device aDevice, boolean aFocused, int aAction, boolean aGeneric) {
        generateEvent(aWidget, aDevice, aFocused, aAction, aGeneric, aDevice.mCoords);
    }

    private static void generateEvent(Widget aWidget, Device aDevice, boolean aFocused, int aAction, boolean aGeneric, MotionEvent.PointerCoords[] aCoords) {
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
                /*deviceId*/ aDevice.mDevice,
                /*edgeFlags*/ 0,
                /*source*/ InputDevice.SOURCE_TOUCHSCREEN,
                /*flags*/ 0);
        if (aGeneric) {
            if (aWidget.supportsMultipleInputDevices()) {
                aWidget.handleHoverEvent(event);

            } else if (aFocused) {
                aWidget.handleHoverEvent(event);
            }
        } else {
            aWidget.handleTouchEvent(event);
        }
        event.recycle();
    }

    public static void dispatch(WidgetManagerDelegate widgetManager, Widget aWidget, int aDevice, boolean aFocused, boolean aPressed, float aX, float aY) {
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
                generateEvent(device.mPreviousWidget, device, aFocused, MotionEvent.ACTION_CANCEL, false);
                device.mTouchStartWidget = null;
                device.mWasPressed = false;
            }
            generateEvent(device.mPreviousWidget, device, aFocused, MotionEvent.ACTION_HOVER_EXIT, true, device.mMouseOutCoords);
            device.mPreviousWidget = null;
            device.mHoverStartWidget = null;
        }
        if (aWidget == null) {
            device.mPreviousWidget = null;
            device.mHoverStartWidget = null;
            return;
        }
        if (aWidget != device.mPreviousWidget && !aPressed) {
            generateEvent(aWidget, device, aFocused, MotionEvent.ACTION_HOVER_ENTER, true);
            widgetManager.triggerHapticFeedback();
            device.mHoverStartWidget = aWidget;
        }
        if (aPressed && !device.mWasPressed) {
            device.mDownTime = SystemClock.uptimeMillis();
            device.mWasPressed = true;
            if (!isOtherDeviceDown(device.mDevice)) {
                generateEvent(aWidget, device, aFocused, MotionEvent.ACTION_HOVER_EXIT, true);
                generateEvent(aWidget, device, aFocused, MotionEvent.ACTION_DOWN, false);
                device.mHoverStartWidget = null;
            }
            for (int i=0; i<devices.size(); i++) {
                if (devices.get(i) != null && devices.get(i) != device && devices.get(i).mHoverStartWidget != null) {
                    generateEvent(devices.get(i).mHoverStartWidget, devices.get(i), aFocused, MotionEvent.ACTION_HOVER_EXIT, true);
                }
            }
            device.mTouchStartWidget = aWidget;
        } else if (!aPressed && device.mWasPressed) {
            device.mWasPressed = false;
            if (!isOtherDeviceDown(device.mDevice)) {
                generateEvent(device.mTouchStartWidget, device, aFocused, MotionEvent.ACTION_UP, false);
                generateEvent(aWidget, device, aFocused, MotionEvent.ACTION_HOVER_ENTER, true);
                widgetManager.triggerHapticFeedback();
                device.mHoverStartWidget = aWidget;
            }
            device.mTouchStartWidget = null;
        } else if (moving && aPressed) {
            generateEvent(aWidget, device, aFocused, MotionEvent.ACTION_MOVE, false);
        } else if (moving) {
            generateEvent(aWidget, device, aFocused, MotionEvent.ACTION_HOVER_MOVE, true);
        } else {
            Log.e("VRB", "Unknown touch event action");
            return;
        }
        device.mPreviousWidget = aWidget;
    }

    /**
     * Checks if any other device has an ongoing touch down event.
     * Android throw away all previous state when starting a new touch gesture
     * and this seem to make the previous touch to be sent up the view hierarchy.
     * To avoid this we check if any other device has a button down before sending
     * touch down/up event.
     * @param deviceId Device Id to filter
     * @return true if any other device has a button down, false otherwise
     */
    private static boolean isOtherDeviceDown(int deviceId) {
        boolean result = false;
        for (int i=0; i<devices.size(); i++) {
            if (i != deviceId) {
                if (devices.get(i) != null) {
                    result |= devices.get(i).mTouchStartWidget != null;
                }
            }
        }

        return result;
    }

    public static void dispatchScroll(Widget aWidget, int aDevice, boolean aFocused, float aX, float aY) {
        Device device = devices.get(aDevice);
        if (device == null) {
            device = new Device(aDevice);
            devices.put(aDevice, device);
        }
        device.mPreviousWidget = aWidget;
        device.mCoords[0].setAxisValue(MotionEvent.AXIS_VSCROLL, aY);
        device.mCoords[0].setAxisValue(MotionEvent.AXIS_HSCROLL, aX);
        generateEvent(aWidget, device, aFocused, MotionEvent.ACTION_SCROLL, true);
        device.mCoords[0].setAxisValue(MotionEvent.AXIS_VSCROLL, 0.0f);
        device.mCoords[0].setAxisValue(MotionEvent.AXIS_HSCROLL, 0.0f);
    }

    public static void clearDevices() {
        devices.clear();
    }
}
