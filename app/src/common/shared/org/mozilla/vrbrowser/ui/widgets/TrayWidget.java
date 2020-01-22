/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.mozilla.gecko.util.ThreadUtils;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.BookmarksStore;
import org.mozilla.vrbrowser.browser.SessionChangeListener;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.ui.views.UIButton;
import org.mozilla.vrbrowser.ui.widgets.settings.SettingsWidget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TrayWidget extends UIWidget implements SessionChangeListener, WindowWidget.BookmarksViewDelegate,
        WindowWidget.HistoryViewDelegate, WidgetManagerDelegate.UpdateListener {

    private static final int ICON_ANIMATION_DURATION = 200;
    private static final int LIBRARY_NOTIFICATION_DURATION = 3000;

    private UIButton mAddWindowButton;
    private UIButton mSettingsButton;
    private UIButton mPrivateButton;
    private UIButton mBookmarksButton;
    private UIButton mHistoryButton;
    private UIButton mTabsButton;
    private AudioEngine mAudio;
    private int mSettingsDialogHandle = -1;
    private boolean mIsLastSessionPrivate;
    private List<TrayListener> mTrayListeners;
    private int mMinPadding;
    private int mMaxPadding;
    private boolean mKeyboardVisible;
    private boolean mTrayVisible = true;
    private Session mSession;
    private WindowWidget mAttachedWindow;
    private TooltipWidget mLibraryNotification;

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

        mPrivateButton = findViewById(R.id.privateButton);
        mPrivateButton.setOnHoverListener(mButtonScaleHoverListener);
        mPrivateButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            notifyPrivateBrowsingClicked();
            view.requestFocusFromTouch();
        });
        mPrivateButton.setCurvedTooltip(false);

        mSettingsButton = findViewById(R.id.settingsButton);
        mSettingsButton.setOnHoverListener(mButtonScaleHoverListener);
        mSettingsButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            toggleSettingsDialog();
            if (isDialogOpened(mSettingsDialogHandle)) {
                view.requestFocusFromTouch();
            }
        });
        mSettingsButton.setCurvedTooltip(false);

        mBookmarksButton = findViewById(R.id.bookmarksButton);
        mBookmarksButton.setOnHoverListener(mButtonScaleHoverListener);
        mBookmarksButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            notifyBookmarksClicked();
            view.requestFocusFromTouch();
        });
        mBookmarksButton.setCurvedTooltip(false);

        mHistoryButton = findViewById(R.id.historyButton);
        mHistoryButton.setOnHoverListener(mButtonScaleHoverListener);
        mHistoryButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            notifyHistoryClicked();
            view.requestFocusFromTouch();
        });
        mHistoryButton.setCurvedTooltip(false);

        mTabsButton = findViewById(R.id.tabsButton);
        mTabsButton.setOnHoverListener(mButtonScaleHoverListener);
        mTabsButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            view.requestFocusFromTouch();
            notifyTabsClicked();
        });
        mHistoryButton.setCurvedTooltip(false);

        mAddWindowButton = findViewById(R.id.addwindowButton);
        mAddWindowButton.setOnHoverListener(mButtonScaleHoverListener);
        mAddWindowButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            view.requestFocusFromTouch();

            notifyAddWindowClicked();
        });
        mAddWindowButton.setCurvedTooltip(false);

        mAudio = AudioEngine.fromContext(aContext);

        mIsLastSessionPrivate = false;

        mWidgetManager.addUpdateListener(this);
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
                try {
                    int newPadding = Integer.parseInt(valueAnimator.getAnimatedValue().toString());
                    view.setPadding(newPadding, newPadding, newPadding, newPadding);
                }
                catch (NumberFormatException ex) {
                    Log.e(LOGTAG, "Error parsing tray animation value: " + valueAnimator.getAnimatedValue().toString());
                }
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

    public void removeListeners(TrayListener... listeners) {
        mTrayListeners.removeAll(Arrays.asList(listeners));
    }

    private void notifyBookmarksClicked() {
        mTrayListeners.forEach(TrayListener::onBookmarksClicked);
    }

    private void notifyHistoryClicked() {
        mTrayListeners.forEach(TrayListener::onHistoryClicked);
    }

    private void notifyTabsClicked() {
        mTrayListeners.forEach(TrayListener::onTabsClicked);
    }

    private void notifyPrivateBrowsingClicked() {
        mTrayListeners.forEach(TrayListener::onPrivateBrowsingClicked);
    }

    private void notifyAddWindowClicked() {
        mTrayListeners.forEach(TrayListener::onAddWindowClicked);
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        Context context = getContext();
        aPlacement.width = WidgetPlacement.dpDimension(context, R.dimen.tray_width);
        aPlacement.height = WidgetPlacement.dpDimension(context, R.dimen.tray_height);
        aPlacement.worldWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.tray_world_width);
        aPlacement.translationY = WidgetPlacement.unitFromMeters(context, R.dimen.tray_world_y) -
                                  WidgetPlacement.unitFromMeters(context, R.dimen.window_world_y);
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(context, R.dimen.tray_world_z) -
                                  WidgetPlacement.unitFromMeters(context, R.dimen.window_world_z);
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.0f;
        aPlacement.rotationAxisX = 1.0f;
        aPlacement.rotation = (float)Math.toRadians(-45);
        aPlacement.opaque = false;
        aPlacement.cylinder = false;
        aPlacement.textureScale = 1.0f;
    }

    @Override
    public void releaseWidget() {
        if (mSession != null) {
            mSession.removeSessionChangeListener(this);
        }

        mWidgetManager.removeUpdateListener(this);
        mTrayListeners.clear();

        super.releaseWidget();
    }

    @Override
    public void show(@ShowFlags int aShowFlags) {
        if (!mWidgetPlacement.visible) {
            mWidgetPlacement.visible = true;
            mWidgetManager.addWidget(this);
        }
    }

    @Override
    public void hide(@HideFlags int aHideFlags) {
        hideNotification(mBookmarksButton);
        hideNotification(mTabsButton);

        if (mWidgetPlacement.visible) {
            mWidgetPlacement.visible = false;
            if (aHideFlags == REMOVE_WIDGET) {
                mWidgetManager.removeWidget(this);
            } else {
                mWidgetManager.updateWidget(this);
            }
        }
    }

    @Override
    public void detachFromWindow() {
        hideNotification(mBookmarksButton);
        hideNotification(mTabsButton);
        
        if (mSession != null) {
            mSession.removeSessionChangeListener(this);
            mSession = null;
        }
        if (mAttachedWindow != null) {
            SessionStore.get().getBookmarkStore().addListener(mBookmarksListener);
            mAttachedWindow.removeBookmarksViewListener(this);
            mAttachedWindow.removeHistoryViewListener(this);
        }
        mWidgetPlacement.parentHandle = -1;

    }

    @Override
    public void attachToWindow(@NonNull WindowWidget aWindow) {
        if (mAttachedWindow == aWindow) {
            return;
        }
        detachFromWindow();

        mAttachedWindow = aWindow;
        mWidgetPlacement.parentHandle = aWindow.getHandle();
        mAttachedWindow.addBookmarksViewListener(this);
        mAttachedWindow.addHistoryViewListener(this);

        SessionStore.get().getBookmarkStore().addListener(mBookmarksListener);

        mSession = aWindow.getSession();
        if (mSession != null) {
            mSession.addSessionChangeListener(this);
            handleSessionState();
        }

        if (mAttachedWindow.isBookmarksVisible()) {
            onBookmarksShown(aWindow);
        } else {
            onBookmarksHidden(aWindow);
        }

        if (mAttachedWindow.isHistoryVisible()) {
            onHistoryViewShown(aWindow);
        } else {
            onHistoryViewHidden(aWindow);
        }
    }

    // Session.SessionChangeListener

    @Override
    public void onCurrentSessionChange(GeckoSession aOldSession, GeckoSession aSession) {
        handleSessionState();
    }

    private void handleSessionState() {
        if (mSession != null) {
            boolean isPrivateMode = mSession.isPrivateMode();

            if (isPrivateMode != mIsLastSessionPrivate) {
                mPrivateButton.setPrivateMode(isPrivateMode);
                if (isPrivateMode) {
                    mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);
                    mPrivateButton.setImageResource(R.drawable.ic_icon_tray_private_browsing_on_v2);
                    mPrivateButton.setTooltip(getResources().getString(R.string.private_browsing_exit_tooltip));

                } else {
                    mWidgetManager.popWorldBrightness(this);
                    mPrivateButton.setImageResource(R.drawable.ic_icon_tray_private_browsing_v2);
                    mPrivateButton.setTooltip(getResources().getString(R.string.private_browsing_enter_tooltip));
                }
            }

            mIsLastSessionPrivate = isPrivateMode;
        }
    }

    public void toggleSettingsDialog() {
        toggleSettingsDialog(SettingsWidget.SettingDialog.MAIN);
    }

    public void toggleSettingsDialog(@NonNull SettingsWidget.SettingDialog settingDialog) {
        UIWidget widget = getChild(mSettingsDialogHandle);
        if (widget == null) {
            widget = createChild(SettingsWidget.class, false);
            mSettingsDialogHandle = widget.getHandle();
        }

        if (mAttachedWindow != null) {
            widget.getPlacement().parentHandle = mAttachedWindow.getHandle();
        }
        if (widget.isVisible()) {
            widget.hide(REMOVE_WIDGET);
        } else {
            ((SettingsWidget)widget).show(REQUEST_FOCUS, settingDialog);
        }
    }

    public void showSettingsDialog(@NonNull SettingsWidget.SettingDialog settingDialog) {
        UIWidget widget = getChild(mSettingsDialogHandle);
        if (widget == null) {
            widget = createChild(SettingsWidget.class, false);
            mSettingsDialogHandle = widget.getHandle();
        }

        if (mAttachedWindow != null) {
            widget.getPlacement().parentHandle = mAttachedWindow.getHandle();
        }

        ((SettingsWidget)widget).show(REQUEST_FOCUS, settingDialog);
    }

    public void setTrayVisible(boolean aVisible) {
        if (mTrayVisible != aVisible) {
            mTrayVisible = aVisible;
            updateVisibility();

        } else {
            mWidgetManager.updateWidget(this);
        }
    }

    private void updateVisibility() {
        if (mTrayVisible && !mKeyboardVisible) {
            this.show(REQUEST_FOCUS);
        } else {
            this.hide(UIWidget.KEEP_WIDGET);
        }
    }

    public boolean isDialogOpened(int aHandle) {
        UIWidget widget = getChild(aHandle);
        if (widget != null) {
            return widget.isVisible();
        }
        return false;
    }

    public void setAddWindowVisible(boolean aVisible) {
        mAddWindowButton.setVisibility(aVisible ? View.VISIBLE : View.GONE);
        if (aVisible) {
            mTabsButton.updateBackgrounds(R.drawable.tray_background_unchecked_middle,
                    R.drawable.tray_background_middle_private,
                    R.drawable.tray_background_checked_middle);
        } else {
            mTabsButton.updateBackgrounds(R.drawable.tray_background_unchecked_start,
                    R.drawable.tray_background_start_private,
                    R.drawable.tray_background_checked_start);
        }
    }

    // BookmarksViewListener

    @Override
    public void onBookmarksShown(WindowWidget aWindow) {
        mBookmarksButton.setTooltip(getResources().getString(R.string.close_bookmarks_tooltip));
        mBookmarksButton.setActiveMode(true);
    }

    @Override
    public void onBookmarksHidden(WindowWidget aWindow) {
        mBookmarksButton.setTooltip(getResources().getString(R.string.open_bookmarks_tooltip));
        mBookmarksButton.setActiveMode(false);
    }

    // HistoryViewListener

    @Override
    public void onHistoryViewShown(WindowWidget aWindow) {
        mHistoryButton.setTooltip(getResources().getString(R.string.close_history_tooltip));
        mHistoryButton.setActiveMode(true);
    }

    @Override
    public void onHistoryViewHidden(WindowWidget aWindow) {
        mHistoryButton.setTooltip(getResources().getString(R.string.open_history_tooltip));
        mHistoryButton.setActiveMode(false);
    }

    // WidgetManagerDelegate.UpdateListener

    @Override
    public void onWidgetUpdate(Widget aWidget) {
        if (!aWidget.getClass().equals(KeyboardWidget.class)) {
            return;
        }

        boolean keyboardVisible = aWidget.isVisible();
        if (mKeyboardVisible != keyboardVisible) {
            mKeyboardVisible = keyboardVisible;
            updateVisibility();
        }
    }

    public void showTabAddedNotification() {
        mTabsButton.setNotificationMode(true);
        ThreadUtils.postToUiThread(() -> showNotification(mTabsButton, R.string.tab_added_notification));
    }

    public void showTabSentNotification() {
        mTabsButton.setNotificationMode(true);
        ThreadUtils.postToUiThread(() -> showNotification(mTabsButton, R.string.tab_sent_notification));
    }

    public void showNotification(String text) {
        mSettingsButton.setNotificationMode(true);
        ThreadUtils.postToUiThread(() -> showNotification(mSettingsButton, text));
    }

    private BookmarksStore.BookmarkListener mBookmarksListener = new BookmarksStore.BookmarkListener() {
        @Override
        public void onBookmarksUpdated() {
            // Nothing to do
        }

        @Override
        public void onBookmarkAdded() {
            mBookmarksButton.setNotificationMode(true);
            ThreadUtils.postToUiThread(() -> showNotification(mBookmarksButton, R.string.bookmarks_saved_notification));
        }
    };

    private void showNotification(UIButton button, @StringRes int stringRes) {
        showNotification(button, getResources().getString(stringRes));
    }

    private void showNotification(UIButton button, String string) {
        if (mLibraryNotification != null && mLibraryNotification.isVisible()) {
            return;
        }

        Rect offsetViewBounds = new Rect();
        getDrawingRect(offsetViewBounds);
        offsetDescendantRectToMyCoords(button, offsetViewBounds);

        float ratio = WidgetPlacement.viewToWidgetRatio(getContext(), TrayWidget.this);

        mLibraryNotification = new TooltipWidget(getContext(), R.layout.library_notification);
        mLibraryNotification.getPlacement().parentHandle = getHandle();
        mLibraryNotification.getPlacement().anchorY = 0.0f;
        mLibraryNotification.getPlacement().translationX = (offsetViewBounds.left + button.getWidth() / 2.0f) * ratio;
        mLibraryNotification.getPlacement().translationY = ((offsetViewBounds.top - 60) * ratio);
        mLibraryNotification.getPlacement().translationZ = 25.0f;
        mLibraryNotification.getPlacement().density = WidgetPlacement.floatDimension(getContext(), R.dimen.tray_tooltip_density);
        mLibraryNotification.setText(string);
        mLibraryNotification.setCurvedMode(false);
        mLibraryNotification.show(UIWidget.CLEAR_FOCUS);

        ThreadUtils.postDelayedToUiThread(() -> hideNotification(button), LIBRARY_NOTIFICATION_DURATION);
    }

    private void hideNotification(UIButton button) {
        if (mLibraryNotification != null) {
            mLibraryNotification.hide(UIWidget.REMOVE_WIDGET);
            mLibraryNotification = null;
        }
        button.setNotificationMode(false);
    }
}
