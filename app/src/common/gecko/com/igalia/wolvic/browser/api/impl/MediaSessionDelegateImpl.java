package com.igalia.wolvic.browser.api.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WMediaSession;
import com.igalia.wolvic.browser.api.WSession;

import org.mozilla.geckoview.GeckoSession;

/* package */  class MediaSessionDelegateImpl implements org.mozilla.geckoview.MediaSession.Delegate {
    private WSession mSession;
    private WMediaSession.Delegate mDelegate;
    WMediaSessionImpl mMediaSession;
    private org.mozilla.geckoview.MediaSession mGeckoMediaSession;

    public MediaSessionDelegateImpl(WSession mSession, WMediaSession.Delegate delegate) {
        this.mSession = mSession;
        this.mDelegate = delegate;
        mMediaSession = new WMediaSessionImpl();
    }

    @Override
    public void onActivated(@NonNull GeckoSession session, @NonNull org.mozilla.geckoview.MediaSession mediaSession) {
        mGeckoMediaSession = mediaSession;
        mDelegate.onActivated(mSession, mMediaSession);
    }

    @Override
    public void onDeactivated(@NonNull GeckoSession session, @NonNull org.mozilla.geckoview.MediaSession mediaSession) {
        mGeckoMediaSession = mediaSession;
        mDelegate.onDeactivated(mSession, mMediaSession);
    }

    @Override
    public void onMetadata(@NonNull GeckoSession session, @NonNull org.mozilla.geckoview.MediaSession mediaSession, final @NonNull org.mozilla.geckoview.MediaSession.Metadata meta) {
        mGeckoMediaSession = mediaSession;

        mDelegate.onMetadata(mSession, mMediaSession, new WMediaSession.Metadata(
                meta.title, meta.artist, meta.album, Utils.fromGeckoImage(meta.artwork)
        ));
    }

    @Override
    public void onFeatures(@NonNull GeckoSession session, @NonNull org.mozilla.geckoview.MediaSession mediaSession, long features) {
        mGeckoMediaSession = mediaSession;
        mDelegate.onFeatures(mSession, mMediaSession, features);
    }

    @Override
    public void onPlay(@NonNull GeckoSession session, @NonNull org.mozilla.geckoview.MediaSession mediaSession) {
        mGeckoMediaSession = mediaSession;
        mDelegate.onPlay(mSession, mMediaSession);
    }

    @Override
    public void onPause(@NonNull GeckoSession session, @NonNull org.mozilla.geckoview.MediaSession mediaSession) {
        mGeckoMediaSession = mediaSession;
        mDelegate.onPause(mSession, mMediaSession);
    }

    @Override
    public void onStop(@NonNull GeckoSession session, @NonNull org.mozilla.geckoview.MediaSession mediaSession) {
        mGeckoMediaSession = mediaSession;
        mDelegate.onStop(mSession, mMediaSession);
    }

    @Override
    public void onPositionState(@NonNull GeckoSession session, @NonNull org.mozilla.geckoview.MediaSession mediaSession, @NonNull org.mozilla.geckoview.MediaSession.PositionState state) {
        mGeckoMediaSession = mediaSession;
        mDelegate.onPositionState(mSession, mMediaSession, new WMediaSession.PositionState(
                state.duration, state.position, state.playbackRate
        ));
    }

    @Override
    public void onFullscreen(@NonNull GeckoSession session, @NonNull org.mozilla.geckoview.MediaSession mediaSession, boolean enabled, @Nullable org.mozilla.geckoview.MediaSession.ElementMetadata meta) {
        mGeckoMediaSession = mediaSession;
        mDelegate.onFullscreen(mSession, mMediaSession, enabled, new WMediaSession.ElementMetadata(
                meta.source, meta.duration, meta.width, meta.height, meta.audioTrackCount, meta.videoTrackCount
        ));
    }

    /* package */ class WMediaSessionImpl implements WMediaSession {

        @Override
        public boolean isActive() {
            return mGeckoMediaSession.isActive();
        }

        @Override
        public void pause() {
            mGeckoMediaSession.pause();
        }

        @Override
        public void stop() {
            mGeckoMediaSession.stop();
        }

        @Override
        public void play() {
            mGeckoMediaSession.play();
        }

        @Override
        public void seekTo(double time, boolean fast) {
            mGeckoMediaSession.seekTo(time, fast);
        }

        @Override
        public void seekForward() {
            mGeckoMediaSession.seekForward();;
        }

        @Override
        public void seekBackward() {
            mGeckoMediaSession.seekBackward();
        }

        @Override
        public void nextTrack() {
            mGeckoMediaSession.nextTrack();
        }

        @Override
        public void previousTrack() {
            mGeckoMediaSession.previousTrack();
        }

        @Override
        public void skipAd() {
            mGeckoMediaSession.skipAd();
        }

        @Override
        public void muteAudio(boolean mute) {
            mGeckoMediaSession.muteAudio(mute);
        }
    }
}
