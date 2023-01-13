/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic;

import android.view.Surface;

import com.igalia.wolvic.ui.widgets.Widget;

public class FinalizerRunnable implements Runnable {
    public FinalizerRunnable(Runnable callback, Runnable destroyCallback) {
        mCallback = callback;
        mDestroyCallback = destroyCallback;
    }
    @Override
    public void run() {
        mExecuted = true;
        mCallback.run();
    }
    protected void finalize()
    {
        if (mExecuted)
            return;
        mDestroyCallback.run();
    }

    private Runnable mCallback;
    private Runnable mDestroyCallback;
    private boolean mExecuted = false;
}
