package com.igalia.wolvic.browser;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WMediaSession;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.utils.SystemUtils;

import java.util.concurrent.CopyOnWriteArrayList;

public class Media implements WMediaSession.Delegate {
    private static final String LOGTAG = SystemUtils.createLogtag(Media.class);
    private boolean mIsFullscreen = false;
    private @Nullable
    WMediaSession mMediaSession;
    private double mCurrentTime  = 0.0f;
    private @Nullable WMediaSession.Metadata mMetaData;
    private @Nullable WMediaSession.ElementMetadata mElement;
    private double mPlaybackRate = 1.0f;
    private double mDuration = -1.0f;
    private boolean mPlaying = false;
    private boolean mEnded = false;
    private double mVolume = 1.0f;
    private boolean mIsMuted = false;
    private CopyOnWriteArrayList<WMediaSession.Delegate> mMediaListeners;
    private ResizeDelegate mResizeDelegate;
    private VideoAvailabilityListener mAvailabilityDelegate;
    private long mFeatures = 0;

    public Media() {
        mMediaListeners = new CopyOnWriteArrayList<>();
    }

    public void addMediaListener(WMediaSession.Delegate aListener) {
        mMediaListeners.add(aListener);
    }

    public void removeMediaListener(WMediaSession.Delegate aListener) {
        mMediaListeners.remove(aListener);
    }

    public boolean isActive() {
        return mMediaSession != null && mMediaSession.isActive();
    }

    public double getDuration() {
        return mDuration;
    }

    public boolean isFullscreen() {
        return mIsFullscreen;
    }

    public boolean canPlay() {
        return (mFeatures & WMediaSession.Feature.PLAY) != 0;
    }

    public boolean canPause() {
        return (mFeatures & WMediaSession.Feature.PAUSE) != 0;
    }

    public boolean canSeek() {
        return (mFeatures & WMediaSession.Feature.SEEK_TO) != 0;
    }

    public boolean canSkipAd() {  return (mFeatures & WMediaSession.Feature.SKIP_AD) != 0; };

    public double getCurrentTime() {
        return mCurrentTime;
    }

    public WMediaSession.Metadata getMetaData() {
        return mMetaData;
    }

    public double getPlaybackRate() {
        return mPlaybackRate;
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

    public void seek(double aTime) {
       if (mMediaSession != null) {
           mMediaSession.seekTo(aTime, true);
       }
    }

    public void seekForward() {
        if (mMediaSession != null) {
            mMediaSession.seekForward();
        }
    }

    public void seekBackward() {
        if (mMediaSession != null) {
            mMediaSession.seekBackward();
        }
    }

    public void play() {
        if (mMediaSession != null) {
            mMediaSession.play();
        }
    }

    public void pause() {
        if (mMediaSession != null) {
            mMediaSession.pause();
        }
    }

    public void setVolume(double aVolume) {
        // TODO: mMediaSession doesn't seem to have a way to set volume. Should we change system volume instead?
    }

    public boolean canCtrlVolume() {
        return mMediaSession != null && mMediaSession.canCtrlVolume();
    }

    public void setMuted(boolean aIsMuted) {
        if (mMediaSession != null) {
            mMediaSession.muteAudio(aIsMuted);
            mIsMuted = aIsMuted;
        }
    }

    public long getWidth() {
        return mElement != null ? mElement.width : 0;
    }

    public long getHeight() {
        return mElement != null ? mElement.height : 0;
    }

    public void skipAd() {
        if (canSkipAd() && mMediaSession != null) {
            mMediaSession.skipAd();
        }
    }

    // MediaSession

    @Override
    public void onActivated(@NonNull WSession session, @NonNull WMediaSession mediaSession) {
        mMediaSession = mediaSession;
        mMediaListeners.forEach(listener -> listener.onActivated(session, mediaSession));
        if (mAvailabilityDelegate != null) {
            mAvailabilityDelegate.onVideoAvailabilityChanged(this, true);
        }
    }

    @Override
    public void onDeactivated(@NonNull WSession session, @NonNull WMediaSession mediaSession) {
        if (mMediaSession == mediaSession) {
            mMediaSession = null;
        }
        mMediaListeners.forEach(listener -> listener.onDeactivated(session, mediaSession));
        if (mAvailabilityDelegate != null) {
            mAvailabilityDelegate.onVideoAvailabilityChanged(this, false);
        }
    }

    @Override
    public void onMetadata(@NonNull WSession session, @NonNull WMediaSession mediaSession, @NonNull WMediaSession.Metadata meta) {
        if (mMediaSession == mediaSession) {
            mMetaData = meta;
        }
        mMediaListeners.forEach(listener -> listener.onMetadata(session, mediaSession, meta));
    }

    @Override
    public void onFeatures(@NonNull WSession session, @NonNull WMediaSession mediaSession, long features) {
        mFeatures = features;
        mMediaListeners.forEach(listener -> listener.onFeatures(session, mediaSession, features));
        if (canSkipAd()) {
            skipAd();
        }
    }

    @Override
    public void onPlay(@NonNull WSession session, @NonNull WMediaSession mediaSession) {
        mPlaying = true;
        mMediaListeners.forEach(listener -> listener.onPlay(session, mediaSession));
    }

    @Override
    public void onPause(@NonNull WSession session, @NonNull WMediaSession mediaSession) {
        mPlaying = false;
        mMediaListeners.forEach(listener -> listener.onPause(session, mediaSession));
    }

    @Override
    public void onStop(@NonNull WSession session, @NonNull WMediaSession mediaSession) {
        mPlaying = false;
        mMediaListeners.forEach(listener -> listener.onStop(session, mediaSession));
    }

    @Override
    public void onPositionState(@NonNull WSession session, @NonNull WMediaSession mediaSession, @NonNull WMediaSession.PositionState state) {
        mCurrentTime = state.position;
        mPlaybackRate = state.playbackRate;
        mDuration = state.duration;
        mEnded = state.position >= state.duration;
        mMediaListeners.forEach(listener -> listener.onPositionState(session, mediaSession, state));
    }

    @Override
    public void onFullscreen(@NonNull WSession session, @NonNull WMediaSession mediaSession, boolean enabled, @Nullable WMediaSession.ElementMetadata meta) {
        long oldWidth = getWidth();
        long oldHeight = getHeight();
        mIsFullscreen = enabled;
        if (meta != null) {
            mElement = meta;
            mDuration = meta.duration;
        }
        if (mResizeDelegate!= null && meta != null) {
            final long w = getWidth();
            final long h = getHeight();
            if (w > 0 && h > 0 && w != oldWidth || h != oldHeight) {
                mResizeDelegate.onResize((int)w, (int)h);
            }
        }
        mMediaListeners.forEach(listener -> listener.onFullscreen(session, mediaSession, enabled, meta));
    }

    public interface ResizeDelegate {
        void onResize(int width, int height);
    }

    public void setResizeDelegate(ResizeDelegate aResizeDelegate) {
        mResizeDelegate = aResizeDelegate;
    }

    public void setAvailabilityDelegate(VideoAvailabilityListener aDelegate) {
        mAvailabilityDelegate = aDelegate;
    }
}
