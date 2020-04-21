package org.mozilla.vrbrowser.browser;

import androidx.annotation.NonNull;

import org.mozilla.geckoview.MediaElement;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.util.concurrent.CopyOnWriteArrayList;

public class Media implements MediaElement.Delegate {
    private static final String LOGTAG = SystemUtils.createLogtag(Media.class);
    private boolean mIsFullscreen = false;
    private double mCurrentTime  = 0.0f;
    private MediaElement.Metadata mMetaData;
    private double mPlaybackRate = 1.0f;
    private int mReadyState = MediaElement.MEDIA_READY_STATE_HAVE_NOTHING;
    private boolean mPlaying = false;
    private boolean mWasPlayed = false;
    private boolean mEnded = false;
    private double mVolume = 1.0f;
    private boolean mIsMuted = false;
    private boolean mIsUnloaded = false;
    private MediaElement mMedia;
    private CopyOnWriteArrayList<MediaElement.Delegate> mMediaListeners;
    private ResizeDelegate mResizeDelegate;
    private long mLastStateUpdate;

    public Media(@NonNull MediaElement aMediaElement) {
        mMedia = aMediaElement;
        mMediaListeners = new CopyOnWriteArrayList<>();
        aMediaElement.setDelegate(this);
        mLastStateUpdate = 0;
    }

    public void addMediaListener(MediaElement.Delegate aListener) {
        mMediaListeners.add(aListener);
    }

    public void removeMediaListener(MediaElement.Delegate aListener) {
        mMediaListeners.remove(aListener);
    }

    public double getDuration() {
        if (mMetaData != null) {
            return mMetaData.duration;
        }
        return -1.0f;
    }

    public boolean isFullscreen() {
        return mIsFullscreen;
    }

    public double getCurrentTime() {
        return mCurrentTime;
    }

    public MediaElement.Metadata getMetaData() {
        return mMetaData;
    }

    public double getPlaybackRate() {
        return mPlaybackRate;
    }

    public int getReadyState() {
        return mReadyState;
    }

    public boolean isPlaying() {
        return mPlaying;
    }

    public boolean isPlayed() {
        return mWasPlayed;
    }

    public boolean isEnded() {
        return mEnded;
    }

    public double getVolume() {
        return mVolume;
    }

    public boolean isMuted() {
        return mIsMuted;
    }

    public boolean isUnloaded() {
        return mIsUnloaded;
    }

    public MediaElement getMediaElement() {
        return mMedia;
    }

    public void seek(double aTime) {
        mMedia.seek(aTime);
    }

    public void play() {
        mMedia.play();
    }

    public void pause() {
        mMedia.pause();
    }

    public void setVolume(double aVolume) {
        mMedia.setVolume(aVolume);
    }

    public void setMuted(boolean aIsMuted) {
        mMedia.setMuted(aIsMuted);
    }

    public long getLastStateUpdate() {
        return mLastStateUpdate;
    }

    public void unload() {
        mIsUnloaded = true;
        mMediaListeners.clear();
    }

    public int getWidth() {
        return mMetaData != null ? (int)mMetaData.width : 0;
    }

    public int getHeight() {
        return mMetaData != null ? (int)mMetaData.height : 0;
    }

    public interface ResizeDelegate {
        void onResize(int width, int height);
    }

    public void setResizeDelegate(ResizeDelegate aResizeDelegate) {
        mResizeDelegate = aResizeDelegate;
    }

    // Media Element delegate
    @Override
    public void onPlaybackStateChange(MediaElement mediaElement, int playbackState) {
        if (playbackState == MediaElement.MEDIA_STATE_PLAY) {
            mWasPlayed = true;
            mPlaying = true;
        } else if (playbackState == MediaElement.MEDIA_STATE_PAUSE) {
            mPlaying = false;
        } else if (playbackState == MediaElement.MEDIA_STATE_ENDED) {
            mEnded = true;
        } else if (playbackState == MediaElement.MEDIA_STATE_EMPTIED) {
            mEnded = true;
            mPlaying = false;
            mIsUnloaded = true;
        }
        mLastStateUpdate = System.currentTimeMillis();
        mMediaListeners.forEach(listener -> listener.onPlaybackStateChange(mediaElement, playbackState));
    }

    @Override
    public void onReadyStateChange(MediaElement mediaElement, int readyState) {
        mReadyState = readyState;
        mMediaListeners.forEach(listener -> listener.onReadyStateChange(mediaElement, readyState));
    }

    @Override
    public void onMetadataChange(MediaElement mediaElement, MediaElement.Metadata metaData) {
        final int oldWidth = getWidth();
        final int oldHeight = getHeight();
        mMetaData = metaData;
        mMediaListeners.forEach(listener -> listener.onMetadataChange(mediaElement, metaData));

        if (mResizeDelegate!= null && metaData != null) {
            final int w = getWidth();
            final int h = getHeight();
            if (w > 0 && h > 0 && w != oldWidth || h != oldHeight) {
                mResizeDelegate.onResize(w, h);
            }
        }
    }

    @Override
    public void onLoadProgress(MediaElement mediaElement, MediaElement.LoadProgressInfo progressInfo) {
        mMediaListeners.forEach(listener -> listener.onLoadProgress(mediaElement, progressInfo));
    }

    @Override
    public void onVolumeChange(MediaElement mediaElement, double volume, boolean muted) {
        mVolume = volume;
        mIsMuted = muted;
        mMediaListeners.forEach(listener -> listener.onVolumeChange(mediaElement, volume, muted));
    }

    @Override
    public void onTimeChange(MediaElement mediaElement, double time) {
        mCurrentTime = time;
        double duration = getDuration();
        if (duration <= 0 || mCurrentTime < getDuration()) {
            mEnded = false;
        }
        mMediaListeners.forEach(listener -> listener.onTimeChange(mediaElement, time));
    }

    @Override
    public void onPlaybackRateChange(MediaElement mediaElement, double rate) {
        mPlaybackRate = rate;
        mMediaListeners.forEach(listener -> listener.onPlaybackRateChange(mediaElement, rate));
    }

    @Override
    public void onFullscreenChange(MediaElement mediaElement, boolean fullscreen) {
        mIsFullscreen = fullscreen;
        mMediaListeners.forEach(listener -> listener.onFullscreenChange(mediaElement, fullscreen));
    }

    @Override
    public void onError(MediaElement mediaElement, int code) {
        mMediaListeners.forEach(listener -> listener.onError(mediaElement, code));
    }
}
