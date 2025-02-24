package com.igalia.wolvic.audio;

import android.content.Context;
import android.media.SoundPool;
import android.util.Log;

import com.igalia.wolvic.R;
import com.igalia.wolvic.utils.SystemUtils;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

public class AndroidMediaPlayerSoundPool implements AudioEngine.AudioEngineImpl {
    private Context mContext;
    private HashMap<AudioEngine.Sound, Integer> mSoundMap;
    private SoundPool mSoundPool;
    private int mCurrentStreamId = -1;
    protected static final String LOGTAG = SystemUtils.createLogtag(AndroidMediaPlayerSoundPool.class);


    public AndroidMediaPlayerSoundPool(Context aContext) {
        mContext = aContext;
    }

    private boolean preload() {
        mSoundMap = new HashMap<>();
        mSoundPool = new SoundPool.Builder()
                .setMaxStreams(1).build();

        mSoundPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
            if (status == 0)
                Log.d(LOGTAG, "Preloading Android SoundPool for " + sampleId);
            else
                Log.w(LOGTAG, "Error " + status + " when preloading SoundPool for " + sampleId);
        });
        mSoundMap.put(AudioEngine.Sound.CLICK, mSoundPool.load(mContext, R.raw.click_sound, 1));
        mSoundMap.put(AudioEngine.Sound.BACK, mSoundPool.load(mContext, R.raw.back_sound, 1));
        mSoundMap.put(AudioEngine.Sound.EXIT, mSoundPool.load(mContext, R.raw.exit_sound, 1));
        mSoundMap.put(AudioEngine.Sound.ERROR, mSoundPool.load(mContext, R.raw.error_sound, 1));
        mSoundMap.put(AudioEngine.Sound.KEYBOARD, mSoundPool.load(mContext, R.raw.keyboard_sound, 1));
        return true;
    }

    @Override
    public void preloadAsync(Runnable aCallback) {
        CompletableFuture.runAsync(() -> {
            if (preload() && aCallback != null) {
                aCallback.run();
            }
        });
    }

    @Override
    public void pause() {
        mSoundPool.pause(mCurrentStreamId);
    }

    @Override
    public void resume() {}

    @Override
    public void setPose(float qx, float qy, float qz, float qw, float px, float py, float pz) {}

    @Override
    public void update() {}

    @Override
    public void release() {
        mSoundPool.release();
        mSoundMap.clear();
    }

    @Override
    public void playSound(AudioEngine.Sound aSound, float aVolume, boolean aLoop) {
        mCurrentStreamId = mSoundMap.get(aSound);
        mSoundPool.play(mCurrentStreamId, aVolume, aVolume, 1, aLoop ? -1 : 0, 1.0f);
    }

    @Override
    public void stopSound(AudioEngine.Sound aSound) {
        mSoundPool.stop(mSoundMap.get(aSound));
    }
}
