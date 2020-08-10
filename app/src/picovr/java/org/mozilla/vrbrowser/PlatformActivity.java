/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.picovr.client.HbListener;
import com.picovr.cvclient.ButtonNum;
import com.picovr.client.HbController;
import com.picovr.client.HbManager;
import com.picovr.client.HbTool;
import com.picovr.client.Orientation;
import com.picovr.cvclient.CVController;
import com.picovr.cvclient.CVControllerListener;
import com.picovr.cvclient.CVControllerManager;
import com.picovr.picovrlib.cvcontrollerclient.ControllerClient;
import com.picovr.vractivity.Eye;
import com.picovr.vractivity.HmdState;
import com.picovr.vractivity.RenderInterface;
import com.picovr.vractivity.VRActivity;
import com.psmart.vrlib.VrActivity;
import com.psmart.vrlib.PicovrSDK;

import org.mozilla.vrbrowser.utils.DeviceType;
import org.mozilla.vrbrowser.utils.SystemUtils;


public class PlatformActivity extends VRActivity implements RenderInterface, CVControllerListener {
    static String LOGTAG = SystemUtils.createLogtag(PlatformActivity.class);
    private static final int CONFIRM_BUTTON = 1001;

    public static boolean filterPermission(final String aPermission) {
        return aPermission.equals(Manifest.permission.CAMERA);
    }

    public static boolean isNotSpecialKey(KeyEvent event) {
        return event.getKeyCode() != CONFIRM_BUTTON;
    }

    private BroadcastReceiver mKeysReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String s = intent.getStringExtra("reason");
            if (s == null) {
                return;
            }
            if (s.equalsIgnoreCase("recenter")) {
                nativeRecenter();
            }
        }
    };

    CVControllerManager mControllerManager;
    HbManager mHbManager;
    private boolean mControllersReady;
    private int mType = 0;
    private int mHand = 0;
    // These need to match DeviceDelegatePicoVR.cpp
    private final int BUTTON_APP       = 1;
    private final int BUTTON_TRIGGER   = 1 << 1;
    private final int BUTTON_TOUCHPAD  = 1 << 2;
    private final int BUTTON_AX        = 1 << 3;
    private final int BUTTON_BY        = 1 << 4;
    private final int BUTTON_GRIP      = 1 << 5;


    private static final int GAZE_INDEX = 2;

    @Override
    protected void onCreate(Bundle bundle) {
        nativeOnCreate();
        super.onCreate(bundle);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mKeysReceiver, filter);

        if (ControllerClient.isControllerServiceExisted(this)) {
            mControllerManager = new CVControllerManager(this);
            mControllerManager.setListener(this);
            mType = 1; // 6Dof Headset
            // Enable high res
            PicovrSDK.SetEyeBufferSize(1920, 1920);
            DeviceType.setType(DeviceType.PicoNeo2);
        } else {
            DeviceType.setType(DeviceType.PicoG2);
            mHbManager = new HbManager(this);
            mHbManager.InitServices();
            mHbManager.setHbListener(new HbListener() {
                // Does not seem to get called.
                @Override
                public void onConnect() {}

                // Does not seem to get called.
                @Override
                public void onDisconnect() {}

                @Override
                public void onDataUpdate() {
                }

                // Does not seem to get called.
                @Override
                public void onReCenter() {}

                @Override
                public void onBindService() {
                    mControllersReady = true;
                }
            });

        }
    }

    @Override
    protected void onPause() {
        if (mControllerManager != null) {
            cancelAllHaptics();
            mControllerManager.unbindService();
        } else if (mHbManager != null) {
            mHbManager.Pause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mControllerManager != null) {
            mControllerManager.bindService();
        } else if (mHbManager != null) {
            mHbManager.Resume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mControllerManager != null) {
            mControllerManager.setListener(null);
        }
        unregisterReceiver(mKeysReceiver);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == CONFIRM_BUTTON) {
            int buttons = 0;
            buttons |= event.getAction() == KeyEvent.ACTION_DOWN ? BUTTON_TRIGGER : 0;
            nativeUpdateControllerState(GAZE_INDEX, true, buttons, 0, 0, 0, false, -1);
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onFrameBegin(HmdState hmdState) {
        updateControllers();
        float[] q = hmdState.getOrientation();
        float[] p = hmdState.getPos();
        float ipd = hmdState.getIpd();
        float fov = hmdState.getFov();
        nativeStartFrame(ipd, fov, p[0], p[1], p[2], q[0], q[1], q[2], q[3]);
    }

    private void updateControllers() {
        if (!mControllersReady) {
            return;
        }

        if (mControllerManager != null) {
            int hand = VrActivity.getPvrHandness(this);
            if (hand != mHand) {
                nativeSetFocusedController(hand);
                mHand = hand;
            }
            CVController main = mControllerManager.getMainController();
            if (main != null) {
                updateController(1, main);
            }
            CVController sub = mControllerManager.getSubController();
            if (sub != null) {
                updateController(0, sub);
            }
        } else if (mHbManager != null) {
            update3DofController();
        }
    }

    private void update3DofController() {
        if (mHbManager == null) {
            return;
        }
        HbController controller = mHbManager.getHbController();
        if (controller == null) {
            Log.e(LOGTAG, "CONTROLLER NULL");
            return;
        }

        if (controller.getConnectState() < 2) {
            nativeUpdateControllerState(0, false, 0, 0, 0, 0, false, -1);
            return;
        }
        int hand = VrActivity.getPvrHandness(this);
        if (mHand != hand) {
            nativeUpdateControllerState(mHand, false, 0, 0, 0, 0, false, -1);
            mHand = hand;
        }
        controller.update();

        int battery = controller.getBattaryLevel() * 25;

        float axisX = 0;
        float axisY = 0;
        int[] stick = controller.getTouchPosition();
        if (stick.length >= 2) {
            axisY =  1.0f -((float)stick[0] / 255.0f);
            axisX = (float)stick[1] / 255.0f;
        }

        int buttons = 0;
        int trigger = controller.getTrigerKeyEvent();
        boolean touched = controller.isTouching();
        buttons |= controller.getButtonState(HbTool.ButtonNum.app) ? BUTTON_APP : 0;
        buttons |= controller.getButtonState(HbTool.ButtonNum.click) ? BUTTON_TOUCHPAD : 0;
        buttons |= trigger > 0 ? BUTTON_TRIGGER : 0;

        nativeUpdateControllerState(mHand, true, buttons, (float)trigger, axisX, axisY, touched, battery);

        Orientation q = controller.getOrientation();
        nativeUpdateControllerPose(mHand, false, 0.0f, 0.0f, 0.0f, q.x, q.y, q.z, q.w);
    }

    private void updateController(int aIndex, @NonNull CVController aController) {
        final float kMax = 255.0f;
        final float kHalfMax = kMax / 2.0f;
        boolean connected = aController.getConnectState() > 0;
        if (!connected) {
            nativeUpdateControllerState(aIndex, false, 0, 0, 0, 0, false, -1);
            return;
        }
        float axisX = 0.0f;
        float axisY = 0.0f;
        int[] stick = aController.getTouchPad();
        if (stick.length >= 2) {
            axisY = ((float)stick[0] - kHalfMax) / kHalfMax;
            axisX = ((float)stick[1] - kHalfMax) / kHalfMax;
            if (axisX < 0.1f && axisX > -0.1f) { axisX = 0.0f; }
            if (axisY < 0.1f && axisY > -0.1f) { axisY = 0.0f; }

        } else {
            stick = new int[2];
        }

        int buttons = 0;
        float trigger = (float)aController.getTriggerNum() / 255.0f;
        buttons |= aController.getButtonState(ButtonNum.app) ? BUTTON_APP : 0;
        buttons |= aController.getButtonState(ButtonNum.buttonAX) ? BUTTON_AX : 0;
        buttons |= aController.getButtonState(ButtonNum.buttonBY) ? BUTTON_BY : 0;
        buttons |= trigger >= 0.9f ? BUTTON_TRIGGER : 0;
        buttons |= aController.getButtonState(ButtonNum.buttonRG) ? BUTTON_GRIP : 0;
        buttons |= aController.getButtonState(ButtonNum.buttonLG) ? BUTTON_GRIP : 0;
        buttons |= aController.getButtonState(ButtonNum.click) ? BUTTON_TOUCHPAD : 0;

        nativeUpdateControllerState(aIndex, true, buttons, trigger, axisX, axisY, (stick[0] != 0 ) || (stick[1] != 0), aController.getBatteryLevel());

        boolean supports6Dof = aController.get6DofAbility() > 0;
        float[] q = aController.getOrientation();
        float[] p = aController.getPosition();
        nativeUpdateControllerPose(aIndex, supports6Dof, p[0], p[1], p[2], q[0], q[1], q[2], q[3]);
    }

    @Override
    public void onDrawEye(Eye eye) {
        nativeDrawEye(eye.getType());
    }

    @Override
    public void onFrameEnd() {
        nativeEndFrame();
    }

    @Override
    public void onTouchEvent() {
    }

    @Override
    public void onRenderPause() {
        nativePause();
    }

    @Override
    public void onRenderResume() {
        nativeResume();
    }

    @Override
    public void onRendererShutdown() {
        nativeShutdown();
        nativeDestroy();
    }

    @Override
    public void initGL(int width, int height) {
        mHand = VrActivity.getPvrHandness(this);
        nativeInitialize(width, height, getAssets(), mType, mHand);
    }

    @Override
    public void renderEventCallBack(int i) {
    }

    @Override
    public void surfaceChangedCallBack(int width, int height) {
    }

    // CVControllerListener
    @Override
    public void onBindSuccess() {

    }

    @Override
    public void onBindFail() {
        mControllersReady = false;
    }

    @Override
    public void onThreadStart() {
        mControllersReady = true;
    }

    @Override
    public void onConnectStateChanged(int serialNum, int state) {
    }

    @Override
    public void onMainControllerChanged(int serialNum) {
    }

    @Override
    public void onChannelChanged(int i, int i1) {

    }

    // Called by native
    @Keep
    @SuppressWarnings("unused")
    private void updateHaptics(int aControllerIndex, float aIntensity, float aDurationSeconds) {
        runOnUiThread(() -> {
            if (mControllerManager != null) {
                float intensity = 255.0f * Math.max(Math.min(aIntensity, 1.0f), 0.0f);
                int durationMs = Math.round(aDurationSeconds * 1000);
                ControllerClient.vibrateCV2ControllerStrength(intensity, durationMs, 1 - aControllerIndex);
            }
        });
    }

    // Called by native
    @Keep
    @SuppressWarnings("unused")
    private void cancelAllHaptics() {
        runOnUiThread(() -> {
            if (mControllerManager != null) {
                ControllerClient.vibrateCV2ControllerStrength(0, 0, 0);
                ControllerClient.vibrateCV2ControllerStrength(0, 0, 1);
            }
        });
    }

    // Called by native
    @Keep
    @SuppressWarnings("unused")
    private int getGazeIndex() {
        return GAZE_INDEX;
    }

    protected native void nativeOnCreate();
    protected native void nativeInitialize(int width, int height, Object aAssetManager, int type, int focusIndex);
    protected native void nativeShutdown();
    protected native void nativeDestroy();
    protected native void nativeStartFrame(float ipd, float fov, float px, float py, float pz, float qx, float qy, float qz, float qw);
    protected native void nativeDrawEye(int eye);
    protected native void nativeEndFrame();
    protected native void nativePause();
    protected native void nativeResume();
    protected native void nativeSetFocusedController(int index);
    protected native void nativeUpdateControllerState(int index, boolean connected, int buttons, float grip, float axisX, float axisY, boolean touched, int batteryLevel);
    protected native void nativeUpdateControllerPose(int index, boolean dof6, float px, float py, float pz, float qx, float qy, float qz, float qw);
    protected native void nativeRecenter();
    protected native void queueRunnable(Runnable aRunnable);
}
