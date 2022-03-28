/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.Media;
import com.igalia.wolvic.browser.api.WMediaSession;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.databinding.MediaControlsBinding;
import com.igalia.wolvic.ui.views.MediaSeekBar;
import com.igalia.wolvic.ui.views.VolumeControl;
import com.igalia.wolvic.ui.widgets.menus.VideoProjectionMenuWidget;

public class MediaControlsWidget extends UIWidget implements WMediaSession.Delegate {

    private MediaControlsBinding mBinding;
    private Media mMedia;
    private Runnable mBackHandler;
    private boolean mPlayOnSeekEnd;
    private Rect mOffsetViewBounds;
    private VideoProjectionMenuWidget mProjectionMenu;
    static long VOLUME_SLIDER_CHECK_DELAY = 1000;
    private Handler mVolumeCtrlHandler = new Handler();
    private boolean mHideVolumeSlider = false;
    private Runnable mVolumeCtrlRunnable;

    public MediaControlsWidget(Context aContext) {
        super(aContext);
        initialize();
    }

    public MediaControlsWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize();
    }

    public MediaControlsWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize();
    }

    private void initialize() {
        updateUI();
    }

    private void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());
        mBinding = DataBindingUtil.inflate(inflater, R.layout.media_controls, this, true);
        mBinding.setPlaying(true);
        mBinding.setMuted(false);

        mOffsetViewBounds = new Rect();

        mVolumeCtrlRunnable = () -> {
            if ((mHideVolumeSlider) && (mBinding.volumeControl.getVisibility() == View.VISIBLE)) {
                mBinding.volumeControl.setVisibility(View.INVISIBLE);
                stopVolumeCtrlHandler();
            }
        };

        mBinding.mediaPlayButton.setOnClickListener(v -> {
            if (mMedia.isEnded()) {
                mMedia.seek(0);
                mMedia.play();
            } else if (mMedia.isPlaying()) {
                mMedia.pause();
            } else {
                mMedia.play();
            }

            mBinding.mediaPlayButton.requestFocusFromTouch();
        });

        mBinding.mediaSeekBackwardButton.setOnClickListener(v -> {
            mMedia.seek(Math.max(0, mMedia.getCurrentTime() - 10.0f));
            mBinding.mediaSeekBackwardButton.requestFocusFromTouch();
        });

        mBinding.mediaSeekForwardButton.setOnClickListener(v -> {
            double t = mMedia.getCurrentTime() + 30;
            if (mMedia.getDuration() > 0) {
                t = Math.min(mMedia.getDuration(), t);
            }
            mMedia.seek(t);
            mBinding.mediaSeekForwardButton.requestFocusFromTouch();
        });

        mBinding.mediaProjectionButton.setOnClickListener(v -> {
            WidgetPlacement placement = mProjectionMenu.getPlacement();
            placement.parentHandle = this.getHandle();
            placement.worldWidth = 0.5f;
            placement.parentAnchorX = 0.65f;
            placement.parentAnchorY = 0.4f;
            placement.cylinder = false;
            if (mProjectionMenu.isVisible()) {
                mProjectionMenu.hide(KEEP_WIDGET);

            } else {
                mProjectionMenu.show(REQUEST_FOCUS);
            }
            mWidgetManager.updateWidget(mProjectionMenu);
        });

        mBinding.mediaVolumeButton.setOnClickListener(v -> {
            if (mMedia.isMuted()) {
                mMedia.setMuted(false);
            } else {
                mMedia.setMuted(true);
                mBinding.volumeControl.setVolume(0);
            }
            mBinding.mediaVolumeButton.requestFocusFromTouch();
        });

        mBinding.mediaBackButton.setOnClickListener(v -> {
            if (mBackHandler != null) {
                mBackHandler.run();
            }
            mBinding.mediaBackButton.requestFocusFromTouch();
        });

        mBinding.mediaControlSeekBar.setDelegate(new MediaSeekBar.Delegate() {
            @Override
            public void onSeekDragStart() {
                mPlayOnSeekEnd = mMedia.isPlaying();
                mBinding.mediaControlSeekLabel.setVisibility(View.VISIBLE);
                mMedia.pause();
                mBinding.mediaControlSeekBar.requestFocusFromTouch();
            }

            @Override
            public void onSeek(double aTargetTime) {
                mMedia.seek(aTargetTime);
            }

            @Override
            public void onSeekDragEnd() {
                if (mPlayOnSeekEnd) {
                    mMedia.play();
                }
                mBinding.mediaControlSeekLabel.setVisibility(View.GONE);
            }

            @Override
            public void onSeekHoverStart() {
                mBinding.mediaControlSeekLabel.setVisibility(View.VISIBLE);
            }

            @Override
            public void onSeekHoverEnd() {
                mBinding.mediaControlSeekLabel.setVisibility(View.GONE);
            }

            @Override
            public void onSeekPreview(String aText, double aRatio) {
                mBinding.mediaControlSeekLabel.setText(aText);
                View childView = mBinding.mediaControlSeekBar.getSeekBarView();
                childView.getDrawingRect(mOffsetViewBounds);
                MediaControlsWidget.this.offsetDescendantRectToMyCoords(childView, mOffsetViewBounds);

                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mBinding.mediaControlSeekLabel.getLayoutParams();
                params.setMarginStart(mOffsetViewBounds.left + (int) (aRatio * mOffsetViewBounds.width()) - mBinding.mediaControlSeekLabel.getMeasuredWidth() / 2);
                mBinding.mediaControlSeekLabel.setLayoutParams(params);
            }
        });


        mBinding.volumeControl.setDelegate(new VolumeControl.Delegate() {

            @Override
            public void onVolumeChange(double aVolume) {
                mMedia.setVolume(aVolume);
                if (mMedia.isMuted()) {
                    mMedia.setMuted(false);
                }
                mBinding.volumeControl.requestFocusFromTouch();
            }

            @Override
            public void onSeekBarActionCancelled() {
                mHideVolumeSlider = true;
                startVolumeCtrlHandler();
            }
        });


        this.setOnHoverListener((v, event) -> {
            if (mMedia == null) {
                return false;
            }
           /*this handles the case where the user
              holds the volume slider up/down past the volume control
              control then hovers, which wont be picked up by the
              volume control hover listener.  in this case the widget itself
              needs to handle this case
            */
            if ((event.getX() < 0) || (event.getY() < 0)) {
                mHideVolumeSlider = true;
                startVolumeCtrlHandler();
            }

            return false;
        });
        mBinding.mediaVolumeButton.setOnHoverListener((v, event) -> {
            float startY = v.getY();
            float maxY = startY + v.getHeight();
            //for this we only hide on the left side of volume button or outside y area of button
            if ((event.getX() <= 0) || (event.getX() >= v.getWidth()) || (!(event.getY() > startY && event.getY() < maxY))) {
                mHideVolumeSlider = true;
                startVolumeCtrlHandler();
            } else {
                mBinding.volumeControl.setVisibility(View.VISIBLE);
                mHideVolumeSlider = false;
                stopVolumeCtrlHandler();
            }
            return false;
        });

        mBinding.volumeControl.setOnHoverListener((v, event) -> {
            float startY = 0;
            float maxY = startY + v.getHeight();
            if ((event.getX() > 0 && event.getX() < v.getWidth()) && (event.getY() > startY && event.getY() < maxY)) {
                mHideVolumeSlider = false;
                stopVolumeCtrlHandler();
            }
            //for this we only hide on the right side of volume button or outside y area of button
            else if ((event.getX() <= 0) || (event.getX() >= v.getWidth()) || (!(event.getY() > startY && event.getY() < maxY))) {
                mHideVolumeSlider = true;
                startVolumeCtrlHandler();
            }
            return false;
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        updateUI();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        Context context = getContext();
        aPlacement.worldWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.media_controls_world_width);
        aPlacement.width = WidgetPlacement.dpDimension(context, R.dimen.media_controls_container_width);
        aPlacement.height = WidgetPlacement.dpDimension(context, R.dimen.media_controls_container_height);
        aPlacement.translationY = WidgetPlacement.unitFromMeters(getContext(), R.dimen.settings_world_y) -
                WidgetPlacement.unitFromMeters(getContext(), R.dimen.window_world_y);
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(context, R.dimen.media_controls_world_z);
        aPlacement.anchorX = 0.45f;
        aPlacement.anchorY = 0.5f;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.0f;
        aPlacement.cylinder = false;
    }

    public void setParentWidget(int aHandle) {
        mWidgetPlacement.parentHandle = aHandle;
    }

    public void setProjectionMenuWidget(VideoProjectionMenuWidget aWidget) {
        mProjectionMenu = aWidget;
    }

    public void setBackHandler(Runnable aRunnable) {
        mBackHandler = aRunnable;
    }

    @Override
    public void releaseWidget() {
        super.releaseWidget();
    }

    public void setMedia(Media aMedia) {
        if (mMedia != null && mMedia == aMedia) {
            return;
        }
        if (mMedia != null) {
            mMedia.removeMediaListener(this);
        }
        mMedia = aMedia;
        boolean enabled = mMedia != null;
        mBinding.mediaPlayButton.setEnabled(enabled);
        mBinding.mediaVolumeButton.setEnabled(enabled);
        mBinding.mediaSeekForwardButton.setEnabled(enabled);
        mBinding.mediaSeekBackwardButton.setEnabled(enabled);
        mBinding.mediaControlSeekBar.setEnabled(enabled);

        if (mMedia == null) {
            return;
        }

        mBinding.mediaControlSeekBar.setCurrentTime(mMedia.getCurrentTime());
        mBinding.setPlaying(mMedia.isPlaying());
        mBinding.mediaControlSeekBar.setSeekable(mMedia.canSeek());
        mBinding.mediaVolumeButton.setEnabled(false);

        mMedia.addMediaListener(this);
    }

    public void setProjectionSelectorEnabled(boolean aEnabled) {
        mBinding.mediaProjectionButton.setEnabled(aEnabled);
    }

    private void startVolumeCtrlHandler() {
        mVolumeCtrlHandler.postDelayed(mVolumeCtrlRunnable, VOLUME_SLIDER_CHECK_DELAY);
    }

    public void stopVolumeCtrlHandler() {
        mVolumeCtrlHandler.removeCallbacks(mVolumeCtrlRunnable);
    }

    @Override
    public void onActivated(@NonNull WSession session, @NonNull WMediaSession mediaSession) {

    }

    @Override
    public void onDeactivated(@NonNull WSession session, @NonNull WMediaSession mediaSession) {

    }

    @Override
    public void onMetadata(@NonNull WSession session, @NonNull WMediaSession mediaSession, @NonNull WMediaSession.Metadata meta) {

    }

    @Override
    public void onFeatures(@NonNull WSession session, @NonNull WMediaSession mediaSession, long features) {
        mBinding.mediaControlSeekBar.setSeekable(mMedia.canSeek());
    }

    @Override
    public void onPlay(@NonNull WSession session, @NonNull WMediaSession mediaSession) {
        mBinding.setPlaying(true);
    }

    @Override
    public void onPause(@NonNull WSession session, @NonNull WMediaSession mediaSession) {
        mBinding.setPlaying(false);
    }

    @Override
    public void onStop(@NonNull WSession session, @NonNull WMediaSession mediaSession) {

    }

    @Override
    public void onPositionState(@NonNull WSession session, @NonNull WMediaSession mediaSession, @NonNull WMediaSession.PositionState state) {
        mBinding.mediaControlSeekBar.setDuration(mMedia.getDuration());
        mBinding.mediaControlSeekBar.setCurrentTime(state.position);
    }

    @Override
    public void onFullscreen(@NonNull WSession session, @NonNull WMediaSession mediaSession, boolean enabled, @Nullable WMediaSession.ElementMetadata meta) {

    }

    /*

    @Override
    public void onVolumeChange(MediaElement mediaElement, double volume, boolean muted) {
        if (!mBinding.mediaVolumeButton.isEnabled()) {
            return;
        }
        mBinding.setMuted(muted);
        mBinding.volumeControl.setVolume(volume);
        mBinding.volumeControl.setMuted(muted);
    }

    @Override
    public void onLoadProgress(MediaElement mediaElement, MediaElement.LoadProgressInfo progressInfo) {
        if (progressInfo.buffered != null) {
            mBinding.mediaControlSeekBar.setBuffered(progressInfo.buffered[progressInfo.buffered.length - 1].end);
        }
    }

     */
}
