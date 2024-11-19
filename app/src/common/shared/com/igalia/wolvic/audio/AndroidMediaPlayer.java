package com.igalia.wolvic.audio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.util.Log;

import com.igalia.wolvic.utils.SystemUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

public class AndroidMediaPlayer implements AudioEngine.AudioEngineImpl {
    private Context mContext;
    private HashMap<AudioEngine.Sound, MediaPlayer> mMediaPlayerList;
    protected static final String LOGTAG = SystemUtils.createLogtag(AndroidMediaPlayer.class);

    public AndroidMediaPlayer(Context aContext) {
        mContext = aContext;
    }

    private boolean preload() {
        VRAudioTheme vrAudioTheme = new VRAudioTheme();
        mMediaPlayerList = new HashMap<>();
        for (AudioEngine.Sound sound: AudioEngine.Sound.values()) {
            if (sound == AudioEngine.Sound.AMBIENT) {
                continue;
            }
            try {
                MediaPlayer mediaPlayer = new MediaPlayer();
                Log.d(LOGTAG, "Preloading Android Media Player for " + sound);
                AssetFileDescriptor fd = mContext.getAssets().openFd(vrAudioTheme.getPath(sound));
                mediaPlayer.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                mediaPlayer.prepare();
                mMediaPlayerList.put(sound, mediaPlayer);
            } catch (IOException e) {
                Log.e(LOGTAG, "Error when preloading Android Media Player: " + e.getMessage());
                return false;
            }
        }
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
        for (MediaPlayer mediaPlayer: mMediaPlayerList.values()) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
        }
    }

    @Override
    public void resume() {
        // NOTE: We don't need to resume in Android Media Player
    }

    @Override
    public void setPose(float qx, float qy, float qz, float qw, float px, float py, float pz) {
        // NOTE: Nothing related to Audio Pose in Android Media Player
    }

    @Override
    public void update() {
        // NOTE: Nothing related to updating Audio Pose in Android Media Player
    }

    @Override
    public void release() {
        for (MediaPlayer mediaPlayer: mMediaPlayerList.values()) {
            mediaPlayer.release();
        }
        mMediaPlayerList.clear();
    }

    @Override
    public void playSound(AudioEngine.Sound aSound, float aVolume, boolean aLoop) {
        MediaPlayer mediaPlayer = mMediaPlayerList.get(aSound);
        if (mediaPlayer == null) { return; }

        mediaPlayer.setVolume(aVolume, aVolume);
        mediaPlayer.setLooping(aLoop);
        mediaPlayer.start();
    }

    @Override
    public void stopSound(AudioEngine.Sound aSound) {
        MediaPlayer mediaPlayer = mMediaPlayerList.get(aSound);
        if (mediaPlayer == null || !mediaPlayer.isPlaying()) { return; }

        mediaPlayer.stop();
    }
}
