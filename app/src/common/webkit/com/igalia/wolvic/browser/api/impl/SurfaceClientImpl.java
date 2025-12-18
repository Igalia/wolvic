package com.igalia.wolvic.browser.api.impl;

import android.view.SurfaceHolder;

import androidx.annotation.Nullable;

import com.wpe.wpeview.SurfaceClient;
import com.wpe.wpeview.WPEView;

import java.util.concurrent.CopyOnWriteArrayList;

class SurfaceClientImpl implements SurfaceClient {
    SurfaceClient mProxy;
    CopyOnWriteArrayList<SurfaceHolder.Callback2> mCallbacks = new CopyOnWriteArrayList<>();

    @Override
    public void addCallback(WPEView wpeView, SurfaceHolder.Callback2 callback) {
        if (!mCallbacks.contains(callback)) {
            mCallbacks.add(callback);
        }
        if (mProxy != null) {
            mProxy.addCallback(wpeView, callback);
        }
    }

    @Override
    public void removeCallback(WPEView wpeView, SurfaceHolder.Callback2 callback) {
        mCallbacks.remove(callback);
        if (mProxy != null) {
            mProxy.removeCallback(wpeView, callback);
        }
    }

    public void setProxy(@Nullable SurfaceClient proxy) {
        mProxy = proxy;
    }
}
