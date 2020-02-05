/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ObservableBoolean;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserActivity;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.BookmarksStore;
import org.mozilla.vrbrowser.browser.SessionChangeListener;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.TrayBinding;
import org.mozilla.vrbrowser.ui.viewmodel.WindowViewModel;
import org.mozilla.vrbrowser.ui.views.UIButton;
import org.mozilla.vrbrowser.ui.widgets.settings.SettingsWidget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TrayWidget extends UIWidget implements SessionChangeListener, WidgetManagerDelegate.UpdateListener {

    private static final int ICON_ANIMATION_DURATION = 200;

    private static final int TAB_ADDED_NOTIFICATION_ID = 0;
    private static final int TAB_SENT_NOTIFICATION_ID = 1;
    private static final int BOOKMARK_ADDED_NOTIFICATION_ID = 2;

    private WindowViewModel mViewModel;
    private TrayBinding mBinding;
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
    private boolean mAddWindowVisible;

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
        updateUI();

        mTrayListeners = new ArrayList<>();

        mMinPadding = WidgetPlacement.pixelDimension(getContext(), R.dimen.tray_icon_padding_min);
        mMaxPadding = WidgetPlacement.pixelDimension(getContext(), R.dimen.tray_icon_padding_max);

        mAudio = AudioEngine.fromContext(aContext);

        mIsLastSessionPrivate = false;

        mWidgetManager.addUpdateListener(this);
    }

    public void updateUI() {
        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.tray, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity)getContext());

        mBinding.privateButton.setOnHoverListener(mButtonScaleHoverListener);
        mBinding.privateButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            notifyPrivateBrowsingClicked();
            view.requestFocusFromTouch();
        });
        mBinding.privateButton.setCurvedTooltip(false);

        mBinding.settingsButton.setOnHoverListener(mButtonScaleHoverListener);
        mBinding.settingsButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            toggleSettingsDialog();
            if (isDialogOpened(mSettingsDialogHandle)) {
                view.requestFocusFromTouch();
            }
        });
        mBinding.settingsButton.setCurvedTooltip(false);

        mBinding.bookmarksButton.setOnHoverListener(mButtonScaleHoverListener);
        mBinding.bookmarksButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            notifyBookmarksClicked();
            view.requestFocusFromTouch();
        });
        mBinding.bookmarksButton.setCurvedTooltip(false);

        mBinding.historyButton.setOnHoverListener(mButtonScaleHoverListener);
        mBinding.historyButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            notifyHistoryClicked();
            view.requestFocusFromTouch();
        });
        mBinding.historyButton.setCurvedTooltip(false);

        mBinding.tabsButton.setOnHoverListener(mButtonScaleHoverListener);
        mBinding.tabsButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            view.requestFocusFromTouch();
            notifyTabsClicked();
        });
        mBinding.tabsButton.setCurvedTooltip(false);

        mBinding.addwindowButton.setOnHoverListener(mButtonScaleHoverListener);
        mBinding.addwindowButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            view.requestFocusFromTouch();

            notifyAddWindowClicked();
        });
        mBinding.addwindowButton.setCurvedTooltip(false);

        updateState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        updateUI();
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
        hideNotifications();

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
        hideNotifications();
        
        if (mSession != null) {
            mSession.removeSessionChangeListener(this);
            mSession = null;
        }
        if (mAttachedWindow != null) {
            SessionStore.get().getBookmarkStore().addListener(mBookmarksListener);
        }
        mWidgetPlacement.parentHandle = -1;

        if (mViewModel != null) {
            mViewModel.getIsBookmraksVisible().removeObserver(mBookmarksVisibleObserver);
            mViewModel.getIsHistoryVisible().removeObserver(mHistoryVisibleObserver);
            mViewModel = null;
        }
    }

    Observer<ObservableBoolean> mBookmarksVisibleObserver = isBookmarksVisible -> {
        if (isBookmarksVisible.get()) {
            mBinding.bookmarksButton.setTooltip(getResources().getString(R.string.close_bookmarks_tooltip));
            mBinding.bookmarksButton.setActiveMode(true);

        } else {
            mBinding.bookmarksButton.setTooltip(getResources().getString(R.string.open_bookmarks_tooltip));
            mBinding.bookmarksButton.setActiveMode(false);
        }
    };

    Observer<ObservableBoolean> mHistoryVisibleObserver = isHistoryVisible -> {
        if (isHistoryVisible.get()) {
            mBinding.historyButton.setTooltip(getResources().getString(R.string.close_history_tooltip));
            mBinding.historyButton.setActiveMode(true);

        } else {
            mBinding.historyButton.setTooltip(getResources().getString(R.string.open_history_tooltip));
            mBinding.historyButton.setActiveMode(false);
        }
    };

    @Override
    public void attachToWindow(@NonNull WindowWidget aWindow) {
        if (mAttachedWindow == aWindow) {
            return;
        }
        detachFromWindow();

        mAttachedWindow = aWindow;
        mWidgetPlacement.parentHandle = aWindow.getHandle();

        // ModelView creation and observers setup
        mViewModel = new ViewModelProvider(
                (VRBrowserActivity)getContext(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(((VRBrowserActivity) getContext()).getApplication()))
                .get(String.valueOf(mAttachedWindow.hashCode()), WindowViewModel.class);

        mBinding.setViewmodel(mViewModel);

        mViewModel.getIsBookmraksVisible().observe((VRBrowserActivity)getContext(), mBookmarksVisibleObserver);
        mViewModel.getIsHistoryVisible().observe((VRBrowserActivity)getContext(), mHistoryVisibleObserver);

        SessionStore.get().getBookmarkStore().addListener(mBookmarksListener);

        mSession = aWindow.getSession();
        if (mSession != null) {
            mSession.addSessionChangeListener(this);
            handleSessionState(false);
        }
    }

    // Session.SessionChangeListener

    @Override
    public void onCurrentSessionChange(GeckoSession aOldSession, GeckoSession aSession) {
        handleSessionState(false);
    }

    private void updateState() {
        handleSessionState(true);
        setAddWindowVisible(mAddWindowVisible);
    }

    private void handleSessionState(boolean refresh) {
        if (mSession != null) {
            boolean isPrivateMode = mSession.isPrivateMode();

            if (isPrivateMode != mIsLastSessionPrivate || refresh) {
                mBinding.privateButton.setPrivateMode(isPrivateMode);
                if (isPrivateMode) {
                    if (!refresh) {
                        mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);
                    }
                    mBinding.privateButton.setImageResource(R.drawable.ic_icon_tray_private_browsing_on_v2);
                    mBinding.privateButton.setTooltip(getResources().getString(R.string.private_browsing_exit_tooltip));

                } else {
                    if (!refresh) {
                        mWidgetManager.popWorldBrightness(this);
                    }
                    mBinding.privateButton.setImageResource(R.drawable.ic_icon_tray_private_browsing_v2);
                    mBinding.privateButton.setTooltip(getResources().getString(R.string.private_browsing_enter_tooltip));
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
            widget.hide(KEEP_WIDGET);
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
        mAddWindowVisible = aVisible;

        mBinding.addwindowButton.setVisibility(aVisible ? View.VISIBLE : View.GONE);
        if (aVisible) {
            mBinding.tabsButton.updateBackgrounds(R.drawable.tray_background_unchecked_middle,
                    R.drawable.tray_background_middle_private,
                    R.drawable.tray_background_checked_middle);
        } else {
            mBinding.tabsButton.updateBackgrounds(R.drawable.tray_background_unchecked_start,
                    R.drawable.tray_background_start_private,
                    R.drawable.tray_background_checked_start);
        }
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
        showNotification(TAB_ADDED_NOTIFICATION_ID, mBinding.tabsButton, R.string.tab_added_notification);
    }

    public void showTabSentNotification() {
        showNotification(TAB_SENT_NOTIFICATION_ID, mBinding.tabsButton, R.string.tab_sent_notification);
    }

    public void showBookmarkAddedNotification() {
        showNotification(BOOKMARK_ADDED_NOTIFICATION_ID, mBinding.bookmarksButton, R.string.bookmarks_saved_notification);
    }

    private void showNotification(int notificationId, UIButton button, int stringRes) {
        NotificationManager.Notification notification = new NotificationManager.Builder(this)
                .withView(button)
                .withDensity(R.dimen.tray_tooltip_density)
                .withString(stringRes)
                .withPosition(NotificationManager.Notification.TOP)
                .withZTranslation(25.0f).build();
        NotificationManager.show(notificationId, notification);
    }

    private void hideNotifications() {
        NotificationManager.hideAll();
    }

    private BookmarksStore.BookmarkListener mBookmarksListener = new BookmarksStore.BookmarkListener() {
        @Override
        public void onBookmarksUpdated() {
            // Nothing to do
        }

        @Override
        public void onBookmarkAdded() {
            mWidgetManager.getWindows().showBookmarkAddedNotification();
        }
    };
}
