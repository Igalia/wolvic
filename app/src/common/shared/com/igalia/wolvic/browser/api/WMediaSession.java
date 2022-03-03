package com.igalia.wolvic.browser.api;

import androidx.annotation.LongDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


/**
 * The MediaSession API provides media controls and events for a ISession. This includes support
 * for the DOM Media Session API and regular HTML media content.
 *
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/MediaSession">Media Session
 *     API</a>
 */
@UiThread
public interface WMediaSession {
    /**
     * Get whether the media session is active. Only active media sessions can be controlled.
     *
     * <p>Changes in the active state are notified via {@link WMediaSession.Delegate#onActivated} and {@link
     * WMediaSession.Delegate#onDeactivated} respectively.
     *
     * @see WMediaSession.Delegate#onActivated
     * @see WMediaSession.Delegate#onDeactivated
     * @return True if this media session is active, false otherwise.
     */
    boolean isActive();

    /** Pause playback for the media session. */
    void pause();

    /** Stop playback for the media session. */
    void stop();

    /** Start playback for the media session. */
    void play();

    /**
     * Seek to a specific time. Prefer using fast seeking when calling this in a sequence. Don't use
     * fast seeking for the last or only call in a sequence.
     *
     * @param time The time in seconds to move the playback time to.
     * @param fast Whether fast seeking should be used.
     */
    void seekTo(final double time, final boolean fast);

    /** Seek forward by a sensible number of seconds. */
    void seekForward();

    /** Seek backward by a sensible number of seconds. */
    void seekBackward();

    /**
     * Select and play the next track. Move playback to the next item in the playlist when supported.
     */
    void nextTrack();

    /**
     * Select and play the previous track. Move playback to the previous item in the playlist when
     * supported.
     */
    void previousTrack();

    /** Skip the advertisement that is currently playing. */
    void skipAd();

    /**
     * Set whether audio should be muted. Muting audio is supported by default and does not require
     * the media session to be active.
     *
     * @param mute True if audio for this media session should be muted.
     */
    void muteAudio(final boolean mute);

    /** Implement this delegate to receive media session events. */
    @UiThread
    interface Delegate {
        /**
         * Notify that the given media session has become active. It is always the first event
         * dispatched for a new or previously deactivated media session.
         *
         * @param session The associated ISession.
         * @param mediaSession The media session for the given ISession.
         */
        default void onActivated(
                @NonNull final WSession session, @NonNull final WMediaSession mediaSession) {}

        /**
         * Notify that the given media session has become inactive. Inactive media sessions can not be
         * controlled.
         *
         * <p>TODO: Add settings links to control behavior.
         *
         * @param session The associated ISession.
         * @param mediaSession The media session for the given ISession.
         */
        default void onDeactivated(
                @NonNull final WSession session, @NonNull final WMediaSession mediaSession) {}

        /**
         * Notify on updated metadata. Metadata may be provided by content via the DOM API or by
         * Browser when not availble.
         *
         * @param session The associated ISession.
         * @param mediaSession The media session for the given ISession.
         * @param meta The updated metadata.
         */
        default void onMetadata(
                @NonNull final WSession session,
                @NonNull final WMediaSession mediaSession,
                @NonNull final WMediaSession.Metadata meta) {}

        /**
         * Notify on updated supported features. Unsupported actions will have no effect.
         *
         * @param session The associated ISession.
         * @param mediaSession The media session for the given ISession.
         * @param features A combination of {@link WMediaSession.Feature}.
         */
        default void onFeatures(
                @NonNull final WSession session,
                @NonNull final WMediaSession mediaSession,
                @MSFeature final long features) {}

        /**
         * Notify that playback has started for the given media session.
         *
         * @param session The associated ISession.
         * @param mediaSession The media session for the given ISession.
         */
        default void onPlay(
                @NonNull final WSession session, @NonNull final WMediaSession mediaSession) {}

        /**
         * Notify that playback has paused for the given media session.
         *
         * @param session The associated ISession.
         * @param mediaSession The media session for the given ISession.
         */
        default void onPause(
                @NonNull final WSession session, @NonNull final WMediaSession mediaSession) {}

        /**
         * Notify that playback has stopped for the given media session.
         *
         * @param session The associated ISession.
         * @param mediaSession The media session for the given ISession.
         */
        default void onStop(
                @NonNull final WSession session, @NonNull final WMediaSession mediaSession) {}

        /**
         * Notify on updated position state.
         *
         * @param session The associated ISession.
         * @param mediaSession The media session for the given ISession.
         * @param state An instance of {@link WMediaSession.PositionState}.
         */
        default void onPositionState(
                @NonNull final WSession session,
                @NonNull final WMediaSession mediaSession,
                @NonNull final WMediaSession.PositionState state) {}

        /**
         * Notify on changed fullscreen state.
         *
         * @param session The associated ISession.
         * @param mediaSession The media session for the given ISession.
         * @param enabled True when this media session in in fullscreen mode.
         * @param meta An instance of {@link WMediaSession.ElementMetadata}, if enabled.
         */
        default void onFullscreen(
                @NonNull final WSession session,
                @NonNull final WMediaSession mediaSession,
                final boolean enabled,
                @Nullable final WMediaSession.ElementMetadata meta) {}
    }

    /** The representation of a media element's metadata. */
    class ElementMetadata {
        /** The media source URI. */
        public final @Nullable String source;

        /** The duration of the media in seconds. 0.0 if unknown. */
        public final double duration;

        /** The width of the video in device pixels. 0 if unknown. */
        public final long width;

        /** The height of the video in device pixels. 0 if unknown. */
        public final long height;

        /** The number of audio tracks contained in this element. */
        public final int audioTrackCount;

        /** The number of video tracks contained in this element. */
        public final int videoTrackCount;

        /**
         * ElementMetadata constructor.
         *
         * @param source The media URI.
         * @param duration The media duration in seconds.
         * @param width The video width in device pixels.
         * @param height The video height in device pixels.
         * @param audioTrackCount The audio track count.
         * @param videoTrackCount The video track count.
         */
        public ElementMetadata(
                @Nullable final String source,
                final double duration,
                final long width,
                final long height,
                final int audioTrackCount,
                final int videoTrackCount) {
            this.source = source;
            this.duration = duration;
            this.width = width;
            this.height = height;
            this.audioTrackCount = audioTrackCount;
            this.videoTrackCount = videoTrackCount;
        }
    }

    /** The representation of a media session's metadata. */
    class Metadata {
        /** The media title. May be backfilled based on the document's title. May be null or empty. */
        public final @Nullable String title;

        /** The media artist name. May be null or empty. */
        public final @Nullable String artist;

        /** The media album title. May be null or empty. */
        public final @Nullable String album;

        /** The media artwork image. May be null. */
        public final @Nullable
        WImage artwork;

        /**
         * Metadata constructor.
         *
         * @param title The media title string.
         * @param artist The media artist string.
         * @param album The media album string.
         * @param artwork The media artwork {@link WImage}.
         */
        public Metadata(
                final @Nullable String title,
                final @Nullable String artist,
                final @Nullable String album,
                final @Nullable WImage artwork) {
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.artwork = artwork;
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder("Metadata {");
            builder
                    .append(", title=")
                    .append(title)
                    .append(", artist=")
                    .append(artist)
                    .append(", album=")
                    .append(album)
                    .append(", artwork=")
                    .append(artwork)
                    .append("}");
            return builder.toString();
        }
    }

    /** Holds the details of the media session's playback state. */
    class PositionState {
        /** The duration of the media in seconds. */
        public final double duration;

        /** The last reported media playback position in seconds. */
        public final double position;

        /**
         * The media playback rate coefficient. The rate is positive for forward and negative for
         * backward playback.
         */
        public final double playbackRate;

        /**
         * PositionState constructor.
         *
         * @param duration The media duration in seconds.
         * @param position The current media playback position in seconds.
         * @param playbackRate The playback rate coefficient.
         */
        public PositionState(
                final double duration, final double position, final double playbackRate) {
            this.duration = duration;
            this.position = position;
            this.playbackRate = playbackRate;
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder("PositionState {");
            builder
                    .append("duration=")
                    .append(duration)
                    .append(", position=")
                    .append(position)
                    .append(", playbackRate=")
                    .append(playbackRate)
                    .append("}");
            return builder.toString();
        }
    }

    @Retention(RetentionPolicy.SOURCE)
    @LongDef(
            flag = true,
            value = {
                    WMediaSession.Feature.NONE,
                    WMediaSession.Feature.PLAY,
                    WMediaSession.Feature.PAUSE,
                    WMediaSession.Feature.STOP,
                    WMediaSession.Feature.SEEK_TO,
                    WMediaSession.Feature.SEEK_FORWARD,
                    WMediaSession.Feature.SEEK_BACKWARD,
                    WMediaSession.Feature.SKIP_AD,
                    WMediaSession.Feature.NEXT_TRACK,
                    WMediaSession.Feature.PREVIOUS_TRACK,
                    // Feature.SET_VIDEO_SURFACE
            })
            /* package */ @interface MSFeature {}

    /** Flags for supported media session features. */
    class Feature {
        public static final long NONE = 0;

        /** Playback supported. */
        public static final long PLAY = 1 << 0;

        /** Pausing supported. */
        public static final long PAUSE = 1 << 1;

        /** Stopping supported. */
        public static final long STOP = 1 << 2;

        /** Absolute seeking supported. */
        public static final long SEEK_TO = 1 << 3;

        /** Relative seeking supported (forward). */
        public static final long SEEK_FORWARD = 1 << 4;

        /** Relative seeking supported (backward). */
        public static final long SEEK_BACKWARD = 1 << 5;

        /** Skipping advertisements supported. */
        public static final long SKIP_AD = 1 << 6;

        /** Next track selection supported. */
        public static final long NEXT_TRACK = 1 << 7;

        /** Previous track selection supported. */
        public static final long PREVIOUS_TRACK = 1 << 8;

        /** Focusing supported. */
        public static final long FOCUS = 1 << 9;
    }
}
