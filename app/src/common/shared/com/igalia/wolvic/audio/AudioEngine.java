/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.audio;

import android.content.Context;

import com.igalia.wolvic.utils.SystemUtils;

import java.util.concurrent.ConcurrentHashMap;

public class AudioEngine {
    private Context mContext;
    private AudioEngineImpl mEngine;
    private ConcurrentHashMap<Sound, Integer> mSourceIds;
    private float mMainVolume = 1.0f;
    private static ConcurrentHashMap<Context, AudioEngine> mEngines = new ConcurrentHashMap<>();
    private boolean mEnabled;
    private static final String LOGTAG = SystemUtils.createLogtag(AudioEngine.class);

    public enum SoundType {
        STEREO,
        OBJECT,
        FIELD
    }

    public enum Sound {
        CLICK,
        BACK,
        EXIT,
        ERROR,
        KEYBOARD,
        AMBIENT(SoundType.FIELD);

        private SoundType mType;

        Sound() {
            mType = SoundType.STEREO;
        }
        Sound(SoundType aType) {
            mType = aType;
        }

        public SoundType getType() {
            return mType;
        }
    }

    public interface AudioTheme {
        int getResourceId(Sound aSound);
    }

    public interface AudioEngineImpl {
        void preloadAsync(final Runnable aCallback);
        void pause();
        void resume();
        void setPose(float qx, float qy, float qz, float qw, float px, float py, float pz);
        void update();
        void release();
        void playSound(Sound aSound, float aVolume, boolean aLoop);
        void stopSound(Sound aSound);
    }

    public static AudioEngine fromContext(Context aContext) {
        return mEngines.get(aContext);
    }

    public AudioEngine(Context aContext, AudioEngineImpl aImpl) {
        mContext = aContext;
        mEngine = aImpl;
        mSourceIds = new ConcurrentHashMap<>();
        mEngines.put(aContext, this);
        mEnabled = true;
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }


    public void preloadAsync() {
        preloadAsync(null);
    }

    // Perform preloading in a separate thread in order to avoid blocking the main thread
    public void preloadAsync(final Runnable aCallback) {
        if (mEngine != null) {
            mEngine.preloadAsync(aCallback);
        }
    }

    public void release() {
        if (mEngine != null) {
            mEngine.release();
        }
    }

    public void pauseEngine() {
        if (mEngine != null) {
            mEngine.pause();
        }
    }

    public void resumeEngine() {
        if (mEngine != null) {
            mEngine.resume();
        }
    }

    public void setPose(float qx, float qy, float qz, float qw, float px, float py, float pz) {
        if (mEngine != null) {
            mEngine.setPose(qx, qy, qz, qw, px, py, pz);
        }
    }

    public void update() {
        if (mEngine != null) {
            mEngine.update();
        }
    }

    public void playSound(Sound aSound) {
        playSound(aSound, 1.0f,false);
    }

    public void setMainVolume(float aVolume) {
        mMainVolume = aVolume;
    }

    public void playSound(Sound aSound, float aVolume, boolean aLoop) {
        if (mEnabled && mEngine != null) {
            mEngine.playSound(aSound, aVolume * mMainVolume, aLoop);
        }
    }

    public void stopSound(Sound aSound) {
        if (mEnabled && mEngine != null) {
            mEngine.stopSound(aSound);
        }
    }

}
