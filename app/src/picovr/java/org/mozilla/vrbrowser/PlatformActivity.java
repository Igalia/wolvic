/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;


import android.opengl.GLES20;
import android.os.Bundle;
import android.util.Log;

import com.picovr.vractivity.Eye;
import com.picovr.vractivity.HmdState;
import com.picovr.vractivity.RenderInterface;
import com.picovr.vractivity.VRActivity;
import com.psmart.vrlib.PicovrSDK;

import org.mozilla.vrbrowser.utils.SystemUtils;

import javax.microedition.khronos.opengles.GL11;


public class PlatformActivity extends VRActivity implements RenderInterface {
    static String LOGTAG = SystemUtils.createLogtag(PlatformActivity.class);
    public static boolean filterPermission(final String aPermission) {
        return false;
    }


    String getThreadId() {
        return " Thread: " + Thread.currentThread().getId();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        nativeDestroy();
    }

    @Override
    public void onFrameBegin(HmdState hmdState) {
        Log.e("VRB", "makelele onFrameBegin: " + getThreadId());
        float[] q = hmdState.getOrientation();
        float[] p = hmdState.getPos();
        float ipd = hmdState.getIpd();
        float fov = hmdState.getFov();
        nativeStartFrame(ipd, fov, p[0], p[1], p[2], q[0], q[1], q[2], q[3]);
    }

    @Override
    public void onDrawEye(Eye eye) {
        Log.e("VRB", "makelele onDrawEye: " + eye.getType() + getThreadId());
        nativeDrawEye(eye.getType());
    }

    @Override
    public void onFrameEnd() {
        Log.e("VRB", "makelele onFrameEnd" + getThreadId());
        nativeEndFrame();
    }

    @Override
    public void onTouchEvent() {
        Log.e("VRB", "makelele onTouchEvent" + getThreadId());
    }

    @Override
    public void onRenderPause() {
        Log.e("VRB", "makelele onRenderPause" + getThreadId());
        nativePause();
    }

    @Override
    public void onRenderResume() {
        Log.e("VRB", "makelele onRenderResume" + getThreadId());
        nativeResume();
    }

    @Override
    public void onRendererShutdown() {
        Log.e("VRB", "makelele onRendererShutdown" + getThreadId());
        nativeShutdown();
    }

    @Override
    public void initGL(int width, int height) {
        Log.e("VRB", "makelele initGL" + getThreadId());
        nativeInitialize(width, height, getAssets());
    }

    @Override
    public void renderEventCallBack(int i) {
        Log.e("VRB", "makelele renderEventCallBack" + getThreadId());
    }

    @Override
    public void surfaceChangedCallBack(int width, int height) {
        Log.e("VRB", "makelele surfaceChangedCallBack: " + width + " " + height + getThreadId());
    }

    protected native void nativeInitialize(int width, int height, Object aAssetManager);
    protected native void nativeShutdown();
    protected native void nativeDestroy();
    protected native void nativeStartFrame(float ipd, float fov, float px, float py, float pz, float qx, float qy, float qz, float qw);
    protected native void nativeDrawEye(int eye);
    protected native void nativeEndFrame();
    protected native void nativePause();
    protected native void nativeResume();
    protected native void queueRunnable(Runnable aRunnable);
}
