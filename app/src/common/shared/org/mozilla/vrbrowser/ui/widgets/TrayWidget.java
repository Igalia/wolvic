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

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserActivity;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.BookmarksStore;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.TrayBinding;
import org.mozilla.vrbrowser.downloads.Download;
import org.mozilla.vrbrowser.downloads.DownloadsManager;
import org.mozilla.vrbrowser.ui.viewmodel.TrayViewModel;
import org.mozilla.vrbrowser.ui.viewmodel.WindowViewModel;
import org.mozilla.vrbrowser.ui.views.UIButton;
import org.mozilla.vrbrowser.ui.widgets.settings.SettingsView;
import org.mozilla.vrbrowser.ui.widgets.settings.SettingsWidget;
import org.mozilla.vrbrowser.utils.ViewUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TrayWidget extends UIWidget implements WidgetManagerDelegate.UpdateListener, DownloadsManager.DownloadsListener {

    private static final int ICON_ANIMATION_DURATION = 200;

    private static final int TAB_ADDED_NOTIFICATION_ID = 0;
    private static final int TAB_SENT_NOTIFICATION_ID = 1;
    private static final int BOOKMARK_ADDED_NOTIFICATION_ID = 2;
    private static final int DOWNLOAD_COMPLETED_NOTIFICATION_ID = 3;

    private WindowViewModel mViewModel;
    private TrayViewModel mTrayViewModel;
    private TrayBinding mBinding;
    private AudioEngine mAudio;
    private SettingsWidget mSettingsWidget;
    private List<TrayListener> mTrayListeners;
    private int mMinPadding;
    private int mMaxPadding;
    private Session mSession;
    private WindowWidget mAttachedWindow;
    private boolean mIsWindowAttached;

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
        // Downloads icon progress clipping doesn't work if HW acceleration is enabled.
        setIsHardwareAccelerationEnabled(false);

        mTrayViewModel = new ViewModelProvider(
                (VRBrowserActivity)getContext(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(((VRBrowserActivity) getContext()).getApplication()))
                .get(TrayViewModel.class);
        mTrayViewModel.getIsVisible().observe((VRBrowserActivity) getContext(), mIsVisibleObserver);

        updateUI();

        mIsWindowAttached = false;

        mTrayListeners = new ArrayList<>();

        mMinPadding = WidgetPlacement.pixelDimension(getContext(), R.dimen.tray_icon_padding_min);
        mMaxPadding = WidgetPlacement.pixelDimension(getContext(), R.dimen.tray_icon_padding_max);

        mAudio = AudioEngine.fromContext(aContext);

        mWidgetManager.addUpdateListener(this);
        mWidgetManager.getServicesProvider().getDownloadsManager().addListener(this);
    }

    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.tray, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity)getContext());
        mBinding.setTraymodel(mTrayViewModel);
        mBinding.setViewmodel(mViewModel);

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
            if (mSettingsWidget.isVisible()) {
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

        mBinding.downloadsButton.setOnHoverListener(mButtonScaleHoverListener);
        mBinding.downloadsButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            notifyDownloadsClicked();
            view.requestFocusFromTouch();
        });
        mBinding.downloadsButton.setCurvedTooltip(false);
    }

    Observer<ObservableBoolean> mIsVisibleObserver = aVisible -> {
        if (aVisible.get()) {
            this.show(REQUEST_FOCUS);

        } else {
            this.hide(UIWidget.KEEP_WIDGET);
        }

        mWidgetManager.updateWidget(TrayWidget.this);
    };

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        updateUI();

        mTrayViewModel.refresh();
    }

    private OnHoverListener mButtonScaleHoverListener = (view, motionEvent) -> {
        UIButton button = (UIButton)view;
        if (button.isActive() || button.isPrivate()) {
            return false;
        }

        int ev = motionEvent.getActionMasked();
        switch (ev) {
            case MotionEvent.ACTION_HOVER_ENTER:
                if (!view.isPressed() && ViewUtils.isInsideView(view, (int)motionEvent.getRawX(), (int)motionEvent.getRawY())) {
                    animateViewPadding(view, mMaxPadding, mMinPadding, ICON_ANIMATION_DURATION);
                }
                return false;

            case MotionEvent.ACTION_HOVER_EXIT:
                if (!ViewUtils.isInsideView(view, (int)motionEvent.getRawX(), (int)motionEvent.getRawY())) {
                    animateViewPadding(view, mMinPadding, mMaxPadding, ICON_ANIMATION_DURATION);
                }
                return false;
        }

        return false;
    };

    private void animateViewPadding(View view, int paddingStart, int paddingEnd, int duration) {
        if (view.isPressed() || !mIsWindowAttached) {
            view.setPadding(paddingEnd, paddingEnd, paddingEnd, paddingEnd);
            return;
        }

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

    public void addListeners(TrayListener... listeners) {
        mTrayListeners.addAll(Arrays.asList(listeners));
    }

    public void removeListeners(TrayListener... listeners) {
        mTrayListeners.removeAll(Arrays.asList(listeners));
    }

    private void notifyBookmarksClicked() {
        hideNotifications();
        mTrayListeners.forEach(TrayListener::onBookmarksClicked);
    }

    private void notifyHistoryClicked() {
        hideNotifications();
        mTrayListeners.forEach(TrayListener::onHistoryClicked);
    }

    private void notifyTabsClicked() {
        hideNotifications();
        mTrayListeners.forEach(TrayListener::onTabsClicked);
    }

    private void notifyPrivateBrowsingClicked() {
        hideNotifications();
        mTrayListeners.forEach(TrayListener::onPrivateBrowsingClicked);
    }

    private void notifyAddWindowClicked() {
        hideNotifications();
        mTrayListeners.forEach(TrayListener::onAddWindowClicked);
    }

    private void notifyDownloadsClicked() {
        hideNotifications();
        mTrayListeners.forEach(TrayListener::onDownloadsClicked);
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
        aPlacement.cylinder = false;
        aPlacement.textureScale = 1.0f;
    }

    @Override
    public void releaseWidget() {
        mWidgetManager.removeUpdateListener(this);
        mWidgetManager.getServicesProvider().getDownloadsManager().removeListener(this);
        mTrayListeners.clear();

        if (mTrayViewModel != null) {
            mTrayViewModel.getIsVisible().removeObserver(mIsVisibleObserver);
            mTrayViewModel = null;
        }

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
            mSession = null;
        }
        if (mAttachedWindow != null) {
            SessionStore.get().getBookmarkStore().addListener(mBookmarksListener);
        }
        mWidgetPlacement.parentHandle = -1;

        if (mViewModel != null) {
            mViewModel.getIsBookmarksVisible().removeObserver(mIsBookmarksVisible);
            mViewModel.getIsHistoryVisible().removeObserver(mIsHistoryVisible);
            mViewModel.getIsDownloadsVisible().removeObserver(mIsDownloadsVisible);
            mViewModel = null;
        }

        mIsWindowAttached = false;
    }

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
        mViewModel.getIsBookmarksVisible().observe((VRBrowserActivity)getContext(), mIsBookmarksVisible);
        mViewModel.getIsHistoryVisible().observe((VRBrowserActivity)getContext(), mIsHistoryVisible);
        mViewModel.getIsDownloadsVisible().observe((VRBrowserActivity)getContext(), mIsDownloadsVisible);

        mBinding.setViewmodel(mViewModel);

        SessionStore.get().getBookmarkStore().addListener(mBookmarksListener);

        mIsWindowAttached = true;
    }

    private Observer<ObservableBoolean> mIsBookmarksVisible = aBoolean -> {
        if (mBinding.bookmarksButton.isHovered()) {
            return;
        }
        if (aBoolean.get()) {
            animateViewPadding(mBinding.bookmarksButton, mMaxPadding, mMinPadding, ICON_ANIMATION_DURATION);
        } else {
            animateViewPadding(mBinding.bookmarksButton, mMinPadding, mMaxPadding, ICON_ANIMATION_DURATION);
        }
    };

    private Observer<ObservableBoolean> mIsHistoryVisible = aBoolean -> {
        if (mBinding.historyButton.isHovered()) {
            return;
        }
        if (aBoolean.get()) {
            animateViewPadding(mBinding.historyButton, mMaxPadding, mMinPadding, ICON_ANIMATION_DURATION);

        } else {
            animateViewPadding(mBinding.historyButton, mMinPadding, mMaxPadding, ICON_ANIMATION_DURATION);
        }
    };

    private Observer<ObservableBoolean> mIsDownloadsVisible = aBoolean -> {
        if (mBinding.downloadsButton.isHovered()) {
            return;
        }
        if (aBoolean.get()) {
            animateViewPadding(mBinding.downloadsButton, mMaxPadding, mMinPadding, ICON_ANIMATION_DURATION);
        } else {
            animateViewPadding(mBinding.downloadsButton, mMinPadding, mMaxPadding, ICON_ANIMATION_DURATION);
        }
    };

    public void toggleSettingsDialog() {
        toggleSettingsDialog(SettingsView.SettingViewType.MAIN);
    }

    public void toggleSettingsDialog(@NonNull SettingsView.SettingViewType settingDialog) {
        if (mSettingsWidget == null) {
            mSettingsWidget = new SettingsWidget(getContext());
        }
        mSettingsWidget.attachToWindow(mAttachedWindow);

        if (mSettingsWidget.isVisible()) {
            mSettingsWidget.hide(KEEP_WIDGET);

        } else {
            mSettingsWidget.show(REQUEST_FOCUS, settingDialog);
        }
    }

    public void showSettingsDialog(@NonNull SettingsView.SettingViewType settingDialog) {
        if (mSettingsWidget == null) {
            mSettingsWidget = new SettingsWidget(getContext());
        }
        mSettingsWidget.attachToWindow(mAttachedWindow);

        mSettingsWidget.show(REQUEST_FOCUS, settingDialog);
    }

    public void setAddWindowVisible(boolean aVisible) {
        mTrayViewModel.setIsMaxWindows(!aVisible);
    }

    // WidgetManagerDelegate.UpdateListener

    @Override
    public void onWidgetUpdate(Widget aWidget) {
        if (!aWidget.getClass().equals(KeyboardWidget.class)) {
            return;
        }

        mTrayViewModel.setIsKeyboardVisible(aWidget.isVisible());
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

    public void showDownloadCompletedNotification(String filename) {
        showNotification(DOWNLOAD_COMPLETED_NOTIFICATION_ID,
                mBinding.downloadsButton,
                getContext().getString(R.string.download_completed_notification, filename));
    }

    private void showNotification(int notificationId, UIButton button, int stringRes) {
        showNotification(notificationId, button, getContext().getString(stringRes));
    }

    private void showNotification(int notificationId, UIButton button, String string) {
        if (isVisible()) {
            NotificationManager.Notification notification = new NotificationManager.Builder(this)
                    .withView(button)
                    .withDensity(R.dimen.tray_tooltip_density)
                    .withString(string)
                    .withPosition(NotificationManager.Notification.TOP)
                    .withZTranslation(25.0f).build();
            NotificationManager.show(notificationId, notification);
        }
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

    // DownloadsManager.DownloadsListener

    @Override
    public void onDownloadsUpdate(@NonNull List<Download> downloads) {
        long inProgressNum = downloads.stream().filter(item ->
                item.getStatus() == Download.RUNNING ||
                        item.getStatus() == Download.PAUSED ||
                        item.getStatus() == Download.PENDING).count();
        mTrayViewModel.setDownloadsNumber((int)inProgressNum);
        if (inProgressNum == 0) {
            mBinding.downloadsButton.setLevel(0);

        } else {
            long size = downloads.stream()
                    .filter(item -> item.getStatus() == Download.RUNNING)
                    .mapToLong(Download::getSizeBytes)
                    .sum();
            long downloaded = downloads.stream().filter(item -> item.getStatus() == Download.RUNNING)
                    .mapToLong(Download::getDownloadedBytes)
                    .sum();
            if (size > 0) {
                long percent = downloaded*100/size;
                mBinding.downloadsButton.setLevel((int)percent*100);
            }
        }
    }

    @Override
    public void onDownloadCompleted(@NonNull Download download) {
        showDownloadCompletedNotification(download.getFilename());
    }
}
