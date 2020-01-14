/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.databinding.DataBindingUtil;

import org.mozilla.geckoview.MediaElement;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.Media;
import org.mozilla.vrbrowser.databinding.TitleBarBinding;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

public class TitleBarWidget extends UIWidget implements WidgetManagerDelegate.UpdateListener {

    public interface Delegate {
        void onTitleClicked(@NonNull TitleBarWidget titleBar);
        void onMediaPlayClicked(@NonNull TitleBarWidget titleBar);
        void onMediaPauseClicked(@NonNull TitleBarWidget titleBar);
    }

    private TitleBarBinding mBinding;
    private WindowWidget mAttachedWindow;
    private boolean mVisible = false;
    private Media mMedia;
    private boolean mWidgetAdded = false;

    public TitleBarWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public TitleBarWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public TitleBarWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(@NonNull Context aContext) {
        LayoutInflater inflater = LayoutInflater.from(aContext);

        mBinding = DataBindingUtil.inflate(inflater, R.layout.title_bar, this, true);
        mBinding.setWidget(this);
        mBinding.executePendingBindings();

        mWidgetManager.addUpdateListener(this);
    }

    public void setDelegate(Delegate delegate) {
        mBinding.setDelegate(delegate);
    }

    public @Nullable
    WindowWidget getAttachedWindow() {
        return mAttachedWindow;
    }

    @Override
    public void releaseWidget() {
        detachFromWindow();

        mWidgetManager.removeUpdateListener(this);

        mAttachedWindow = null;
        super.releaseWidget();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.width = WidgetPlacement.dpDimension(getContext(), R.dimen.title_bar_width);
        float ratio = WidgetPlacement.floatDimension(getContext(), R.dimen.window_world_width) /
                      WidgetPlacement.dpDimension(getContext(), R.dimen.navigation_bar_width);

        aPlacement.worldWidth = aPlacement.width * ratio;
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.navigation_bar_height);
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 1.0f;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.0f;
        aPlacement.translationY = -35;
        aPlacement.opaque = false;
        aPlacement.cylinder = true;
        aPlacement.visible = true;
    }

    @Override
    public void detachFromWindow() {
        mAttachedWindow = null;
    }

    @Override
    public void attachToWindow(@NonNull WindowWidget aWindow) {
        if (aWindow == mAttachedWindow) {
            return;
        }
        detachFromWindow();

        mWidgetPlacement.parentHandle = aWindow.getHandle();
        mAttachedWindow = aWindow;

        setPrivateMode(aWindow.getSession().isPrivateMode());
    }

    @Override
    public void setVisible(boolean aIsVisible) {
        if (mVisible == aIsVisible || mWidgetManager == null) {
            return;
        }
        mVisible = aIsVisible;
        getPlacement().visible = aIsVisible;
        if (!mWidgetAdded) {
            mWidgetManager.addWidget(this);
            mWidgetAdded = true;
        } else {
            mWidgetManager.updateWidget(this);
        }
    }

    private void setPrivateMode(boolean aPrivateMode) {
        mBinding.titleBar.setBackground(getContext().getDrawable(aPrivateMode ? R.drawable.title_bar_background_private : R.drawable.title_bar_background));
        mBinding.mediaButton.setPrivateMode(aPrivateMode);
    }

    public void setURL(@StringRes int id) {
        setURL(getResources().getString(id));
    }

    public void setURL(String urlString) {
        if (urlString == null) {
            return;
        }

        if (URLUtil.isValidUrl(urlString)) {
            try {
                URI uri = URI.create(urlString);
                URL url = new URL(
                        uri.getScheme() != null ? uri.getScheme() : "",
                        uri.getAuthority() != null ? uri.getAuthority() : "",
                        "");
                mBinding.url.setText(url.toString());

            } catch (MalformedURLException | IllegalArgumentException e) {
                mBinding.url.setText("");
            }

        } else {
            mBinding.url.setText(urlString);
        }
    }

    public void setIsInsecure(boolean aIsInsecure) {
        if (mAttachedWindow != null && mAttachedWindow.getSession() != null &&
                mAttachedWindow.getSession().getCurrentUri() != null &&
                !(mAttachedWindow.getSession().getCurrentUri().startsWith("data") &&
                mAttachedWindow.getSession().isPrivateMode())) {
            mBinding.insecureIcon.setVisibility(aIsInsecure ? View.VISIBLE : View.GONE);
        }
    }

    public void setInsecureVisibility(int visibility) {
        mBinding.insecureIcon.setVisibility(visibility);
    }

    public void mediaAvailabilityChanged(boolean available) {
        if (mMedia != null) {
            mMedia.removeMediaListener(mMediaDelegate);
        }
        if (available && mAttachedWindow != null && mAttachedWindow.getSession() != null) {
            mMedia = mAttachedWindow.getSession().getFullScreenVideo();
            if (mMedia != null) {
                mMedia.addMediaListener(mMediaDelegate);
                if (mMedia.isPlayed()) {
                    mBinding.setIsMediaAvailable(true);
                    mBinding.setIsMediaPlaying(true);
                }
            }
        } else {
            mMedia = null;
            mBinding.setIsMediaAvailable(false);
        }
    }

    public void updateMediaStatus() {
        if (mMedia != null) {
            mBinding.setIsMediaAvailable(mMedia.isPlayed());
            mBinding.setIsMediaPlaying(mMedia.isPlaying());
        }
    }

    MediaElement.Delegate mMediaDelegate = new MediaElement.Delegate() {
        @Override
        public void onPlaybackStateChange(@NonNull MediaElement mediaElement, int state) {
            switch(state) {
                case MediaElement.MEDIA_STATE_PLAY:
                case MediaElement.MEDIA_STATE_PLAYING:
                    mBinding.setIsMediaAvailable(true);
                    mBinding.setIsMediaPlaying(true);
                    break;
                case MediaElement.MEDIA_STATE_PAUSE:
                    mBinding.setIsMediaAvailable(true);
                    mBinding.setIsMediaPlaying(false);
            }
        }
    };

    // WidgetManagerDelegate.UpdateListener
    @Override
    public void onWidgetUpdate(Widget aWidget) {
        if (aWidget == mWidgetManager.getFocusedWindow()) {
            mWidgetManager.updateWidget(this);
        }
    }

}
