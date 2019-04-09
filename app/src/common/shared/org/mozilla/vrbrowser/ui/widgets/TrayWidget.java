/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.SessionStore;
import org.mozilla.vrbrowser.ui.views.UIButton;
import org.mozilla.vrbrowser.ui.widgets.dialogs.SettingsWidget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TrayWidget extends UIWidget implements SessionStore.SessionChangeListener, BookmarkListener {

    private static final int ICON_ANIMATION_DURATION = 200;

    private UIButton mHelpButton;
    private UIButton mSettingsButton;
    private UIButton mPrivateButton;
    private UIButton mBookmarksButton;
    private AudioEngine mAudio;
    private int mSettingsDialogHandle = -1;
    private boolean mIsLastSessionPrivate;
    private List<TrayListener> mTrayListeners;
    private int mMinPadding;
    private int mMaxPadding;

    public TrayWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public TrayWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public TrayWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.tray, this);

        mTrayListeners = new ArrayList<>();

        mMinPadding = WidgetPlacement.pixelDimension(getContext(), R.dimen.tray_icon_padding_min);
        mMaxPadding = WidgetPlacement.pixelDimension(getContext(), R.dimen.tray_icon_padding_max);

        mHelpButton = findViewById(R.id.helpButton);
        mHelpButton.setOnHoverListener(mButtonScaleHoverListener);
        mHelpButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            onHelpButtonClicked();
            view.requestFocusFromTouch();
        });

        mPrivateButton = findViewById(R.id.privateButton);
        mPrivateButton.setOnHoverListener(mButtonScaleHoverListener);
        mPrivateButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            notifyPrivateBrowsingClicked();
            view.requestFocusFromTouch();

            SessionStore.get().switchPrivateMode();
        });

        mSettingsButton = findViewById(R.id.settingsButton);
        mSettingsButton.setOnHoverListener(mButtonScaleHoverListener);
        mSettingsButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            toggleSettingsDialog();
            if (isDialogOpened(mSettingsDialogHandle))
                view.requestFocusFromTouch();
        });

        mBookmarksButton = findViewById(R.id.bookmarksButton);
        mBookmarksButton.setOnHoverListener(mButtonScaleHoverListener);
        mBookmarksButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            notifyBookmarksClicked();
            view.requestFocusFromTouch();
        });

        mAudio = AudioEngine.fromContext(aContext);

        mIsLastSessionPrivate = false;

        SessionStore.get().addSessionChangeListener(this);

        handleSessionState();
    }

    private OnHoverListener mButtonScaleHoverListener = (view, motionEvent) -> {
        int ev = motionEvent.getActionMasked();
        switch (ev) {
            case MotionEvent.ACTION_HOVER_ENTER:
                animateViewPadding(view, mMaxPadding, mMinPadding, ICON_ANIMATION_DURATION);
                return false;

            case MotionEvent.ACTION_HOVER_EXIT:
                animateViewPadding(view, mMinPadding, mMaxPadding, ICON_ANIMATION_DURATION);
                return false;
        }

        return false;
    };

    private void animateViewPadding(View view, int paddingStart, int paddingEnd, int duration) {
        UIButton button = (UIButton)view;
        if (!button.isActive() && !button.isPrivate()) {
            ValueAnimator animation = ValueAnimator.ofInt(paddingStart, paddingEnd);
            animation.setDuration(duration);
            animation.setInterpolator(new AccelerateDecelerateInterpolator());
            animation.addUpdateListener(valueAnimator -> {
                int newPadding = Integer.parseInt(valueAnimator.getAnimatedValue().toString());
                view.setPadding(newPadding, newPadding, newPadding, newPadding);
            });
            animation.addListener(new Animator.AnimatorListener() {

                @Override
                public void onAnimationStart(Animator animator) {
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    UIButton button = (UIButton)view;
                    if(button.isActive() || button.isPrivate()) {
                        view.setPadding(mMinPadding, mMinPadding, mMinPadding, mMinPadding);
                    }
                }

                @Override
                public void onAnimationCancel(Animator animator) {

                }

                @Override
                public void onAnimationRepeat(Animator animator) {

                }
            });
            animation.start();
        }
    }

    public void addListeners(TrayListener... listeners) {
        mTrayListeners.addAll(Arrays.asList(listeners));
    }

    public void removeAllListeners() {
        mTrayListeners.clear();
    }

    private void notifyBookmarksClicked() {
        mTrayListeners.forEach(trayListener -> trayListener.onBookmarksClicked());
    }

    private void notifyPrivateBrowsingClicked() {
        mTrayListeners.forEach(trayListener -> trayListener.onPrivateBrowsingClicked());
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        Context context = getContext();
        aPlacement.width = WidgetPlacement.dpDimension(context, R.dimen.tray_width);
        aPlacement.height = WidgetPlacement.dpDimension(context, R.dimen.tray_height);
        aPlacement.worldWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.tray_world_width);
        aPlacement.translationY = WidgetPlacement.unitFromMeters(context, R.dimen.tray_world_y);
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(context, R.dimen.tray_world_z);
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.5f;
        aPlacement.rotationAxisX = 1.0f;
        aPlacement.rotation = (float)Math.toRadians(-45);
        aPlacement.opaque = false;
        aPlacement.cylinder = false;
        aPlacement.textureScale = 1.0f;
    }

    @Override
    public void releaseWidget() {
        SessionStore.get().removeSessionChangeListener(this);

        super.releaseWidget();
    }

    // SessionStore.SessionChangeListener

    @Override
    public void onNewSession(GeckoSession aSession, int aId) {

    }

    @Override
    public void onRemoveSession(GeckoSession aSession, int aId) {

    }

    @Override
    public void onCurrentSessionChange(GeckoSession aSession, int aId) {
        handleSessionState();
    }

    private void handleSessionState() {
        boolean isPrivateMode  = SessionStore.get().isCurrentSessionPrivate();

        if (isPrivateMode != mIsLastSessionPrivate) {
            mPrivateButton.setPrivateMode(isPrivateMode);
            if (isPrivateMode) {
                mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);
                mPrivateButton.setImageResource(R.drawable.ic_tray_private_on);

            } else {
                mWidgetManager.popWorldBrightness(this);
                mPrivateButton.setImageResource(R.drawable.ic_tray_private);
            }
        }

        mIsLastSessionPrivate = isPrivateMode;
    }

    private void toggleSettingsDialog() {
        UIWidget widget = getChild(mSettingsDialogHandle);
        if (widget == null) {
            widget = createChild(SettingsWidget.class, false);
            mSettingsDialogHandle = widget.getHandle();
        }

        if (widget.isVisible()) {
            widget.hide(REMOVE_WIDGET);
        } else {
            widget.show();
        }
    }

    @Override
    public void show() {
        if (!mWidgetPlacement.visible) {
            mWidgetPlacement.visible = true;
            mWidgetManager.addWidget(this);
        }
    }

    @Override
    public void hide(@HideFlags int aHideFlags) {
        if (mWidgetPlacement.visible) {
            mWidgetPlacement.visible = false;
            if (aHideFlags == REMOVE_WIDGET) {
                mWidgetManager.removeWidget(this);
            } else {
                mWidgetManager.updateWidget(this);
            }
        }
    }

    public boolean isDialogOpened(int aHandle) {
        UIWidget widget = getChild(aHandle);
        if (widget != null) {
            return widget.isVisible();
        }
        return false;
    }

    private void onHelpButtonClicked() {
        GeckoSession session = SessionStore.get().getCurrentSession();
        if (session == null) {
            int sessionId = SessionStore.get().createSession();
            SessionStore.get().setCurrentSession(sessionId);
        }

        SessionStore.get().loadUri(getContext().getString(R.string.help_url));
    }

    // BookmarkListener

    @Override
    public void onBookmarksShown() {
        mBookmarksButton.setActiveMode(true);
    }

    @Override
    public void onBookmarksHidden() {
        mBookmarksButton.setActiveMode(false);
    }

}
