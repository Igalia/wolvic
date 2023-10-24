package com.igalia.wolvic.browser.api.impl;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WImage;
import com.igalia.wolvic.browser.api.WMediaSession;
import com.igalia.wolvic.browser.api.WResult;

import org.chromium.base.task.PostTask;
import org.chromium.base.task.TaskTraits;
import org.chromium.content_public.browser.MediaSession;
import org.chromium.content_public.browser.MediaSessionObserver;
import org.chromium.content_public.browser.WebContents;
import org.chromium.components.browser_ui.media.MediaNotificationImageUtils;
import org.chromium.components.browser_ui.media.MediaImageCallback;
import org.chromium.components.browser_ui.media.MediaImageManager;
import org.chromium.services.media_session.MediaImage;
import org.chromium.services.media_session.MediaMetadata;
import org.chromium.services.media_session.MediaPosition;

import java.util.List;

import org.chromium.media_session.mojom.MediaSessionAction;

public class TabMediaSessionObserver extends MediaSessionObserver implements MediaImageCallback {
    /** the time interval for updating position of progress with the seconds unit. */
    private static final int UPDATE_POSITION_TIME_MS = 500;

    private @NonNull SessionImpl mSession;
    private WMediaSessionImpl mMediaSession = new WMediaSessionImpl();
    private MediaImageManager mMediaImageManager;
    private MediaMetadata mMetadata;
    private WImage mMediaImage;
    private MediaPosition mMediaPosition;
    private boolean mIsActive = false;
    private boolean mIsSuspended = false;
    private boolean mRunUpdatingPositionTask = false;

    public TabMediaSessionObserver(@NonNull WebContents webContents, @NonNull SessionImpl session) {
        super(MediaSession.fromWebContents(webContents));

        mSession = session;
        mMediaImageManager =
                new MediaImageManager(MediaNotificationImageUtils.MINIMAL_MEDIA_IMAGE_SIZE_PX,
                        MediaNotificationImageUtils.getIdealMediaImageSize());
        mMediaImageManager.setWebContents(webContents);
    }

    @Override
    public void mediaSessionDestroyed() {
        mMediaSession = null;
        stopUpdatingPosition();
    }

    @Override
    public void mediaSessionStateChanged(boolean isControllable, boolean isSuspended, boolean isActive) {
        assert mMediaSession != null;

        // If the Session is being closed just ignore the change notification.
        WMediaSession.Delegate delegate = mSession.getMediaSessionDelegate();
        if (delegate == null)
            return;

        if (isActive != mIsActive) {
            mIsActive = isActive;
            if (mIsActive) {
                delegate.onActivated(mSession, mMediaSession);
            } else {
                stopUpdatingPosition();
                delegate.onStop(mSession, mMediaSession);
                delegate.onDeactivated(mSession, mMediaSession);
            }
        }
        if (isSuspended != mIsSuspended) {
            mIsSuspended = isSuspended;
            if (!mIsSuspended) {
                startUpdatingPosition();
                delegate.onPlay(mSession, mMediaSession);
            } else {
                stopUpdatingPosition();
                delegate.onPause(mSession, mMediaSession);
            }
        }
    }

    @Override
    public void mediaSessionMetadataChanged(MediaMetadata metadata) {
        assert mMediaSession != null;

        mMetadata = metadata;
        updateMetaData();
    }

    @Override
    public void mediaSessionArtworkChanged(List<MediaImage> images) {
        if (images == null || images.size() == 0) {
            mMediaImage = null;
            return;
        }

        mMediaImageManager.downloadImage(images, TabMediaSessionObserver.this);
    }

    @Override
    public void mediaSessionPositionChanged(@Nullable MediaPosition position) {
        mMediaPosition = position;
        if (mMediaPosition == null) {
            stopUpdatingPosition();
            return;
        }

        if (mIsActive && !mIsSuspended)
            startUpdatingPosition();
    }

    public void onMediaFullscreen(boolean isFullscreen) {
        assert mMediaSession != null;
        if (mSession.getMediaSessionDelegate() != null)
            mSession.getMediaSessionDelegate().onFullscreen(
                    mSession, mMediaSession, isFullscreen, null);
    }

    /* package */ class WMediaSessionImpl implements WMediaSession {

        @Override
        public boolean isActive() {
            if (getMediaSession() == null)
                return false;
            return mIsActive;
        }

        @Override
        public void pause() {
            if (getMediaSession() == null)
                return;
            getMediaSession().suspend();
        }

        @Override
        public void stop() {
            if (getMediaSession() == null)
                return;
            getMediaSession().stop();
        }

        @Override
        public void play() {
            if (getMediaSession() == null)
                return;
            getMediaSession().resume();
        }

        @Override
        public void seekTo(double time, boolean fast) {
            if (getMediaSession() == null)
                return;
            long seekTime = Double.valueOf(time * 1000).longValue();
            if (fast)
                getMediaSession().scrubTo(seekTime);
            else
                getMediaSession().seekTo(seekTime);
        }

        @Override
        public void seekForward() {
            if (getMediaSession() == null)
                return;
            getMediaSession().didReceiveAction(MediaSessionAction.SEEK_FORWARD);
        }

        @Override
        public void seekBackward() {
            if (getMediaSession() == null)
                return;
            getMediaSession().didReceiveAction(MediaSessionAction.SEEK_BACKWARD);
        }

        @Override
        public void nextTrack() {
            if (getMediaSession() == null)
                return;
                getMediaSession().didReceiveAction(MediaSessionAction.NEXT_TRACK);
        }

        @Override
        public void previousTrack() {
            if (getMediaSession() == null)
                return;
            getMediaSession().didReceiveAction(MediaSessionAction.PREVIOUS_TRACK);
        }

        @Override
        public void skipAd() {
            if (getMediaSession() == null)
                return;
            getMediaSession().didReceiveAction(MediaSessionAction.SKIP_AD);
        }

        @Override
        public void muteAudio(boolean mute) {
            if (getMediaSession() == null)
                return;
            getMediaSession().setMute(mute);
        }

        @Override
        public boolean canCtrlVolume() {
            // TODO: Check if the media session in Chromium supports volume control.
            return false;
        }
    }

    private void updatePosition() {
        if (mMediaPosition == null || !mIsActive || mSession.getMediaSessionDelegate() == null)
            return;

        assert mMediaSession != null;

        long now = SystemClock.elapsedRealtime();
        long rebasedPosition = mMediaPosition.getPosition()
                + (long) ((now - mMediaPosition.getLastUpdatedTime())
                * mMediaPosition.getPlaybackRate());
        mSession.getMediaSessionDelegate().onPositionState(
                mSession, mMediaSession,
                new WMediaSession.PositionState(mMediaPosition.getDuration() / 1000,
                        rebasedPosition / 1000,
                        mMediaPosition.getPlaybackRate()));

        if (mRunUpdatingPositionTask)
            startUpdatingPosition();
    }

    private void startUpdatingPosition() {
        mRunUpdatingPositionTask = true;
        PostTask.postDelayedTask(TaskTraits.UI_DEFAULT, () -> updatePosition(), UPDATE_POSITION_TIME_MS);
    }

    private void stopUpdatingPosition() {
        mRunUpdatingPositionTask = false;
    }

    @Override
    public void onImageDownloaded(Bitmap image) {
        mMediaImage = fromDownloadedBitmap(image);
        updateMetaData();
    }

    private WImage fromDownloadedBitmap(final @Nullable Bitmap image) {
        return size -> WResult.fromValue(scaleBitmap(image, size));
    }

    private Bitmap scaleBitmap(Bitmap image, int size) {
        if (image == null) return null;

        Matrix m = new Matrix();
        int dominantLength = Math.max(image.getWidth(), image.getHeight());

        // Move the center to (0,0).
        m.postTranslate(image.getWidth() / -2.0f, image.getHeight() / -2.0f);
        // Scale to desired size.
        float scale = 1.0f * size / dominantLength;
        m.postScale(scale, scale);
        // Move to the desired place.
        m.postTranslate(size / 2.0f, size / 2.0f);

        // Draw the image.
        Bitmap paddedBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(paddedBitmap);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(image, m, paint);
        return paddedBitmap;
    }

    private void updateMetaData() {
        assert mMediaSession != null;
        if (mSession.getMediaSessionDelegate() == null)
            return;

        WMediaSession.Metadata metadata;
        if (mMetadata != null) {
            metadata = new WMediaSession.Metadata(
                    mMetadata.getTitle(), mMetadata.getArtist(), mMetadata.getAlbum(), mMediaImage);
        } else {
            metadata = new WMediaSession.Metadata(null, null, null, mMediaImage);
        }
        mSession.getMediaSessionDelegate().onMetadata(mSession, mMediaSession, metadata);
    }
}
