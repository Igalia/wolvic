/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import com.picovr.cvclient.ButtonNum;
import com.picovr.cvclient.CVController;
import com.picovr.cvclient.CVControllerListener;
import com.picovr.cvclient.CVControllerManager;
import com.picovr.vractivity.Eye;
import com.picovr.vractivity.HmdState;
import com.picovr.vractivity.RenderInterface;
import com.picovr.vractivity.VRActivity;
import com.psmart.vrlib.PicovrSDK;

import org.mozilla.vrbrowser.utils.SystemUtils;


public class PlatformActivity extends VRActivity implements RenderInterface, CVControllerListener {
    static String LOGTAG = SystemUtils.createLogtag(PlatformActivity.class);
    public static boolean filterPermission(final String aPermission) {
        return false;
    }

    CVControllerManager mControllerManager;
    private boolean mControllersReady;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mControllerManager = new CVControllerManager(this);
        mControllerManager.setListener(this);
    }

    @Override
    protected void onPause() {
        mControllerManager.unbindService();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mControllerManager.bindService();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mControllerManager.setListener(null);
        nativeDestroy();
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

        CVController main = mControllerManager.getMainController();
        if (main != null) {
            updateController(0, main);
        }
        CVController sub = mControllerManager.getSubController();
        if (sub != null) {
            updateController(1, sub);
        }
    }

    private void updateController(int aIndex, @NonNull CVController aController) {
        boolean connected = aController.getConnectState() > 0;
        if (!connected) {
            nativeUpdateControllerState(aIndex, false, 0, 0);
            return;
        }

        int buttons = 0;
        float trigger = (float)aController.getTriggerNum() / 255.0f;
        boolean appButton = aController.getButtonState(ButtonNum.app);
        boolean actionButton = aController.getButtonState(ButtonNum.buttonAX) ||
                aController.getButtonState(ButtonNum.buttonBY) ||
                trigger >= 0.9f;

        if (appButton) {
            buttons |= 1;
        }
        if (actionButton) {
            buttons |= 1 << 1;
        }

        nativeUpdateControllerState(aIndex, true, buttons, trigger);

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
    }

    @Override
    public void initGL(int width, int height) {
        nativeInitialize(width, height, getAssets());
    }

    @Override
    public void renderEventCallBack(int i) {
    }

    @Override
    public void surfaceChangedCallBack(int width, int height) {
    }

    // CVControllerListener
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


    protected native void nativeInitialize(int width, int height, Object aAssetManager);
    protected native void nativeShutdown();
    protected native void nativeDestroy();
    protected native void nativeStartFrame(float ipd, float fov, float px, float py, float pz, float qx, float qy, float qz, float qw);
    protected native void nativeDrawEye(int eye);
    protected native void nativeEndFrame();
    protected native void nativePause();
    protected native void nativeResume();
    protected native void nativeUpdateControllerState(int index, boolean connected, int buttons, float grip);
    protected native void nativeUpdateControllerPose(int index, boolean dof6, float px, float py, float pz, float qx, float qy, float qz, float qw);
    protected native void queueRunnable(Runnable aRunnable);
}
