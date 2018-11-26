/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.audio;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.google.vr.sdk.audio.GvrAudioEngine;
import java.util.concurrent.ConcurrentHashMap;

public class AudioEngine {
    private Context mContext;
    private GvrAudioEngine mEngine;
    private AudioTheme mTheme;
    private ConcurrentHashMap<Sound, Integer> mSourceIds;
    private float mMasterVolume = 1.0f;
    private static ConcurrentHashMap<Context, AudioEngine> mEngines = new ConcurrentHashMap<>();
    private boolean mEnabled;
    private static final String LOGTAG = "VRB";

    public enum SoundType {
        STEREO,
        OBJECT,
        FIELD
    }

    public enum Sound {
        CLICK,
        BACK,
        EXIT,
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
        String getPath(Sound aSound);
    }

    public static AudioEngine fromContext(Context aContext) {
        return mEngines.get(aContext);
    }

    public AudioEngine(Context aContext, AudioTheme aTheme) {
        mContext = aContext;
        mTheme = aTheme;
        mEngine = new GvrAudioEngine(aContext, GvrAudioEngine.RenderingMode.BINAURAL_HIGH_QUALITY);
        mSourceIds = new ConcurrentHashMap<>();
        mEngines.put(aContext, this);
        mEnabled = true;
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    private void preload() {
        for (Sound sound: Sound.values()) {
            if (sound.getType() == SoundType.FIELD) {
                // Ambisonic soundfields do *not* need to be preloaded
                // They are directly streamed and rendered from the compressed audio file.
                // The handle automatically destroys itself at the moment the sound playback has stopped.
                continue;
            }
            String path = mTheme.getPath(sound);
            if (path != null && path.length() > 0) {
                preloadFile(path);
            }
        }
    }

    public void preloadAsync() {
        preloadAsync(null);
    }

    // Perform preloading in a separate thread in order to avoid blocking the main thread
    public void preloadAsync(final Runnable aCallback) {
        if (mEnabled) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    preload();
                    if (aCallback != null) {
                        ((Activity) mContext).runOnUiThread(aCallback);
                    }
                }
            });
            thread.start();
        }
    }

    public void release() {
        mSourceIds.clear();
        mEngines.remove(mContext);
        for (Sound sound: Sound.values()) {
            String path = mTheme.getPath(sound);
            if (path != null && sound.getType() != SoundType.FIELD) {
                mEngine.unloadSoundFile(mTheme.getPath(sound));
            }
        }
    }

    public void pauseEngine() {
        mEngine.pause();
    }

    public void resumeEngine() {
        mEngine.resume();
    }

    public void setPose(float qx, float qy, float qz, float qw, float px, float py, float pz) {
        mEngine.setHeadRotation(qx, qy, qz, qw);
        mEngine.setHeadPosition(px, py, pz);
    }

    public void update() {
        mEngine.update();
    }

    public void playSound(Sound aSound) {
        if (mEnabled) {
            playSound(aSound, false);
        }
    }

    private void playSound(Sound aSound, boolean aLoopEnabled) {
        String path = mTheme.getPath(aSound);
        if (path == null || path.length() == 0) {
            return;
        }
        int sourceId = createSound(aSound.getType(), mTheme.getPath(aSound));
        if (sourceId != GvrAudioEngine.INVALID_ID) {
            mSourceIds.put(aSound, sourceId);
            playSound(sourceId, aLoopEnabled);
        }
    }

    private void playSound(int aSourceId, boolean aLoopEnabled) {
        mEngine.playSound(aSourceId, aLoopEnabled);
    }

    public void pauseSound(Sound aSound) {
        if (mEnabled) {
            Integer sourceId = findSourceId(aSound);
            if (sourceId != null) {
                pauseSound(sourceId);
            }
        }
    }

    private void pauseSound(int aSourceId) {
        mEngine.pauseSound(aSourceId);
    }

    private void resumeSound(Sound aSound) {
        Integer sourceId = findSourceId(aSound);
        if (sourceId != null) {
            resumeSound(sourceId);
        }
    }

    public void resumeSound(int aSourceId) {
        if (mEnabled) {
            mEngine.stopSound(aSourceId);
        }
    }

    private void setSoundPosition(Sound aSound, float x, float y, float z) {
        if (aSound.getType() != SoundType.OBJECT) {
            Log.e(LOGTAG, "Sound position can only be set for SoundType.Object!");
            return;
        }
        Integer sourceId = findSourceId(aSound);
        if (sourceId != null) {
            setSoundPosition(sourceId, x, y, z);
        }
    }

    public void setSoundPosition(int aSoundObjectId, float x, float y, float z) {
        if (mEnabled) {
            mEngine.setSoundObjectPosition(aSoundObjectId, x, y, z);
        }
    }

    private void setSoundVolume(Sound aSound, float aVolume) {
        Integer sourceId = findSourceId(aSound);
        if (sourceId != null) {
            setSoundVolume(sourceId, aVolume);
        }
    }

    public void setSoundVolume(int aSourceId, float aVolume) {
        if (mEnabled) {
            mEngine.setSoundVolume(aSourceId, aVolume * mMasterVolume);
        }
    }

    private void setMasterVolume(float aMasterVolume) {
        mMasterVolume = aMasterVolume;
    }


    private int createSound(SoundType aType, String path) {
        mEngine.preloadSoundFile(path);
        int sourceId = GvrAudioEngine.INVALID_ID;
        switch (aType) {
            case FIELD: sourceId = mEngine.createSoundfield(path); break;
            case OBJECT: sourceId = mEngine.createSoundObject(path); break;
            case STEREO: sourceId = mEngine.createStereoSound(path); break;
        }

        if (sourceId == GvrAudioEngine.INVALID_ID) {
            Log.e(LOGTAG, "Error loading sound from path: " + path);
        }

        return sourceId;
    }

    private void preloadFile(String path) {
        mEngine.preloadSoundFile(path);
    }

    private void unloadFile(String path) {
        mEngine.unloadSoundFile(path);
    }

    private Integer findSourceId(Sound aSound) {
        return mSourceIds.get(aSound);
    }
}
