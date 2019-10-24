/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.ui.views.UIButton;
import org.mozilla.vrbrowser.ui.views.UITextButton;

public class TopBarWidget extends UIWidget implements WidgetManagerDelegate.UpdateListener {

    private UIButton mCloseButton;
    private UIButton mMoveLeftButton;
    private UIButton mMoveRightButton;
    private UITextButton mClearButton;
    private AudioEngine mAudio;
    private WindowWidget mAttachedWindow;
    private TopBarWidget.Delegate mDelegate;
    private LinearLayout mMultiWindowControlsContainer;
    private boolean mVisible = false;
    private boolean mWidgetAdded = false;

    public TopBarWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public TopBarWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public TopBarWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    public interface Delegate {
        void onCloseClicked(TopBarWidget aWidget);
        void onMoveLeftClicked(TopBarWidget aWidget);
        void onMoveRightClicked(TopBarWidget aWidget);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.top_bar, this);

        mMultiWindowControlsContainer = findViewById(R.id.multiWindowControlsContainer);

        mCloseButton = findViewById(R.id.closeWindowButton);
        mCloseButton.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            if (mDelegate != null) {
                mDelegate.onCloseClicked(TopBarWidget.this);
            }
        });

        mMoveLeftButton = findViewById(R.id.moveWindowLeftButton);
        mMoveLeftButton.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            if (mDelegate != null) {
                mDelegate.onMoveLeftClicked(TopBarWidget.this);
            }
        });

        mMoveRightButton = findViewById(R.id.moveWindowRightButton);
        mMoveRightButton.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            if (mDelegate != null) {
                mDelegate.onMoveRightClicked(TopBarWidget.this);
            }
        });

        mClearButton = findViewById(R.id.clearButton);
        mClearButton.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            if (mDelegate != null) {
                mDelegate.onCloseClicked(TopBarWidget.this);
            }
        });

        mAudio = AudioEngine.fromContext(aContext);

        mWidgetManager.addUpdateListener(this);
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        Context context = getContext();
        aPlacement.width = WidgetPlacement.dpDimension(context, R.dimen.top_bar_width);
        aPlacement.height = WidgetPlacement.dpDimension(context, R.dimen.top_bar_height);
        aPlacement.worldWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.window_world_width) * aPlacement.width/getWorldWidth();
        aPlacement.translationY = WidgetPlacement.dpDimension(context, R.dimen.top_bar_window_margin);
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.0f;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 1.0f;
        aPlacement.opaque = false;
    }

    @Override
    public void releaseWidget() {
        mWidgetManager.removeUpdateListener(this);

        super.releaseWidget();
    }

    @Override
    public void detachFromWindow() {
        mAttachedWindow = null;
    }

    @Override
    public void attachToWindow(@NonNull WindowWidget aWindow) {
        if (mAttachedWindow == aWindow) {
            return;
        }
        mWidgetPlacement.parentHandle = aWindow.getHandle();
        mAttachedWindow = aWindow;

        setPrivateMode(aWindow.getSession().isPrivateMode());

    }

    public @Nullable WindowWidget getAttachedWindow() {
        return mAttachedWindow;
    }

    private void setPrivateMode(boolean aPrivateMode) {
        mCloseButton.setPrivateMode(aPrivateMode);
        mMoveLeftButton.setPrivateMode(aPrivateMode);
        mMoveRightButton.setPrivateMode(aPrivateMode);
        mCloseButton.setBackground(getContext().getDrawable(aPrivateMode ? R.drawable.fullscreen_button_private : R.drawable.fullscreen_button));
        mMoveLeftButton.setBackground(getContext().getDrawable(aPrivateMode ? R.drawable.fullscreen_button_private_first : R.drawable.fullscreen_button_first));
        mMoveRightButton.setBackground(getContext().getDrawable(aPrivateMode ? R.drawable.fullscreen_button_private_last : R.drawable.fullscreen_button_last));
    }

    @Override
    public void setVisible(boolean aIsVisible) {
        if (mVisible == aIsVisible ||  mWidgetManager == null) {
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

    public void setClearMode(boolean showClear) {
        mMultiWindowControlsContainer.setVisibility(showClear ? GONE : VISIBLE);
        mClearButton.setVisibility(showClear ? VISIBLE : GONE);
    }

    public void setDelegate(TopBarWidget.Delegate aDelegate) {
        mDelegate = aDelegate;
    }

    public void setMoveLeftButtonEnabled(boolean aEnabled) {
        mMoveLeftButton.setEnabled(aEnabled);
    }

    public void setMoveRightButtonEnabled(boolean aEnabled) {
        mMoveRightButton.setEnabled(aEnabled);
    }

    // WidgetManagerDelegate.UpdateListener

    @Override
    public void onWidgetUpdate(Widget aWidget) {
        if (aWidget != mAttachedWindow) {
            return;
        }
    }

}
