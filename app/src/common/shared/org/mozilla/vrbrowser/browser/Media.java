package org.mozilla.vrbrowser.browser;

import org.mozilla.geckoview.MediaElement;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Media implements MediaElement.Delegate {
    private static final String LOGTAG = "VRB";
    private boolean mIsFullscreen = false;
    private double mCurrentTime  = 0.0f;
    private MediaElement.Metadata mMetaData;
    private double mPlaybackRate = 1.0f;
    private int mReadyState = MediaElement.MEDIA_READY_STATE_HAVE_NOTHING;
    private boolean mPlaying = false;
    private boolean mEnded = false;
    private double mVolume = 1.0f;
    private boolean mIsMuted = false;
    private boolean mIsUnloaded = false;
    private org.mozilla.geckoview.MediaElement mMedia;
    private MediaElement.Delegate mDelegate;
    private ResizeDelegate mResizeDelegate;

    public Media(@NonNull MediaElement aMediaElement) {
        mMedia = aMediaElement;
        aMediaElement.setDelegate(this);
    }

    public void setDelegate(@Nullable MediaElement.Delegate aDelegate) {
        mDelegate = aDelegate;
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

    public void unload() {
        mIsUnloaded = true;
        mDelegate = null;
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
            mPlaying = true;
        } else if (playbackState == MediaElement.MEDIA_STATE_PAUSE) {
            mPlaying = false;
        } else if (playbackState == MediaElement.MEDIA_STATE_ENDED) {
            mEnded = true;
        }
        if (mDelegate != null) {
            mDelegate.onPlaybackStateChange(mediaElement, playbackState);
        }
    }

    @Override
    public void onReadyStateChange(MediaElement mediaElement, int readyState) {
        mReadyState = readyState;
        if (mDelegate != null) {
            mDelegate.onReadyStateChange(mediaElement, readyState);
        }
    }

    @Override
    public void onMetadataChange(MediaElement mediaElement, MediaElement.Metadata metaData) {
        final int oldWidth = getWidth();
        final int oldHeight = getHeight();
        mMetaData = metaData;
        if (mDelegate != null) {
            mDelegate.onMetadataChange(mediaElement, metaData);
        }

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
        if (mDelegate != null) {
            mDelegate.onLoadProgress(mediaElement, progressInfo);
        }
    }

    @Override
    public void onVolumeChange(MediaElement mediaElement, double volume, boolean muted) {
        mVolume = volume;
        mIsMuted = muted;
        if (mDelegate != null) {
            mDelegate.onVolumeChange(mediaElement, volume, muted);
        }
    }

    @Override
    public void onTimeChange(MediaElement mediaElement, double time) {
        mCurrentTime = time;
        double duration = getDuration();
        if (duration <= 0 || mCurrentTime < getDuration()) {
            mEnded = false;
        }
        if (mDelegate != null) {
            mDelegate.onTimeChange(mediaElement, time);
        }
    }

    @Override
    public void onPlaybackRateChange(MediaElement mediaElement, double rate) {
        mPlaybackRate = rate;
        if (mDelegate != null) {
            mDelegate.onPlaybackRateChange(mediaElement, rate);
        }
    }

    @Override
    public void onFullscreenChange(MediaElement mediaElement, boolean fullscreen) {
        mIsFullscreen = fullscreen;
        if (mDelegate != null) {
            mDelegate.onFullscreenChange(mediaElement, fullscreen);
        }
    }

    @Override
    public void onError(MediaElement mediaElement, int code) {
        if (mDelegate != null) {
            mDelegate.onError(mediaElement, code);
        }
    }
}
