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

public class AudioEngineGVR implements AudioEngine.AudioEngineImpl {
    private Context mContext;
    private GvrAudioEngine mEngine;
    private AudioEngine.AudioTheme mTheme;
    private ConcurrentHashMap<AudioEngine.Sound, Integer> mSourceIds;
    private static final String LOGTAG = "VRB";


    public AudioEngineGVR(Context aContext, AudioEngine.AudioTheme aTheme) {
        mContext = aContext;
        mTheme = aTheme;
        mEngine = new GvrAudioEngine(aContext, GvrAudioEngine.RenderingMode.BINAURAL_HIGH_QUALITY);
        mSourceIds = new ConcurrentHashMap<>();
    }

    private void preload() {
        for (AudioEngine.Sound sound: AudioEngine.Sound.values()) {
            if (sound.getType() == AudioEngine.SoundType.FIELD) {
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

    @Override
    public void preloadAsync(final Runnable aCallback) {
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

    @Override
    public void release() {
        mSourceIds.clear();
        for (AudioEngine.Sound sound: AudioEngine.Sound.values()) {
            String path = mTheme.getPath(sound);
            if (path != null && sound.getType() != AudioEngine.SoundType.FIELD) {
                mEngine.unloadSoundFile(mTheme.getPath(sound));
            }
        }
    }

    @Override
    public void pause() {
        mEngine.pause();
    }

    @Override
    public void resume() {
        mEngine.resume();
    }

    @Override
    public void setPose(float qx, float qy, float qz, float qw, float px, float py, float pz) {
        mEngine.setHeadRotation(qx, qy, qz, qw);
        mEngine.setHeadPosition(px, py, pz);
    }

    @Override
    public void update() {
        mEngine.update();
    }


    @Override
    public void playSound(AudioEngine.Sound aSound, float aVolume, boolean aLoop) {
        String path = mTheme.getPath(aSound);
        if (path == null || path.length() == 0) {
            return;
        }
        int sourceId = createSound(aSound.getType(), mTheme.getPath(aSound));
        if (sourceId != GvrAudioEngine.INVALID_ID) {
            mSourceIds.put(aSound, sourceId);
            mEngine.playSound(sourceId, aLoop);
            mEngine.setSoundVolume(sourceId, aVolume);
        }
    }

    @Override
    public void stopSound(AudioEngine.Sound aSound) {
        Integer sourceId = findSourceId(aSound);
        if (sourceId != null) {
            mEngine.stopSound(sourceId);
        }
    }


    private void setSoundPosition(AudioEngine.Sound aSound, float x, float y, float z) {
        if (aSound.getType() != AudioEngine.SoundType.OBJECT) {
            Log.e(LOGTAG, "Sound position can only be set for SoundType.Object!");
            return;
        }
        Integer sourceId = findSourceId(aSound);
        if (sourceId != null) {
            setSoundPosition(sourceId, x, y, z);
        }
    }

    private void setSoundPosition(int aSoundObjectId, float x, float y, float z) {
        mEngine.setSoundObjectPosition(aSoundObjectId, x, y, z);
    }


    private int createSound(AudioEngine.SoundType aType, String path) {
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

    private Integer findSourceId(AudioEngine.Sound aSound) {
        return mSourceIds.get(aSound);
    }
}
