/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.VectorDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.text.format.DateFormat;
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

import com.igalia.wolvic.BuildConfig;
import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.VRBrowserApplication;
import com.igalia.wolvic.audio.AudioEngine;
import com.igalia.wolvic.browser.BookmarksStore;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.TrayBinding;
import com.igalia.wolvic.downloads.Download;
import com.igalia.wolvic.downloads.DownloadsManager;
import com.igalia.wolvic.ui.viewmodel.TrayViewModel;
import com.igalia.wolvic.ui.viewmodel.WindowViewModel;
import com.igalia.wolvic.ui.views.UIButton;
import com.igalia.wolvic.ui.widgets.settings.SettingsView;
import com.igalia.wolvic.ui.widgets.settings.SettingsWidget;
import com.igalia.wolvic.utils.ConnectivityReceiver;
import com.igalia.wolvic.utils.DeviceType;
import com.igalia.wolvic.utils.LocaleUtils;
import com.igalia.wolvic.utils.ViewUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class TrayWidget extends UIWidget implements WidgetManagerDelegate.UpdateListener, DownloadsManager.DownloadsListener, ConnectivityReceiver.Delegate {

    private static final int ICON_ANIMATION_DURATION = 200;

    private static final int TAB_ADDED_NOTIFICATION_ID = 0;
    private static final int TAB_SENT_NOTIFICATION_ID = 1;
    private static final int BOOKMARK_ADDED_NOTIFICATION_ID = 2;
    private static final int DOWNLOAD_COMPLETED_NOTIFICATION_ID = 3;
    private static final int WIFI_NOTIFICATION_ID = 4;
    private static final int LEFT_CONTROLLER_NOTIFICATION_ID = 5;
    private static final int RIGHT_CONTROLLER_NOTIFICATION_ID = 6;
    private static final int HEADSET_NOTIFICATION_ID = 7;
    private static final int TIME_NOTIFICATION_ID = 8;
    private static final int WEB_APP_ADDED_NOTIFICATION_ID = 9;

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
    private BroadcastReceiver mBroadcastReceiver;
    private int mLastWifiLevel = -1;
    private String mWifiSSID;
    private int mHeadsetBatteryLevel;
    private int mLeftControllerBatteryLevel;
    private int mRightControllerBatteryLevel;
    private ConnectivityReceiver mConnectivityReceived;

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

        mTrayViewModel.setHeadsetBatteryLevel(R.drawable.ic_icon_statusbar_indicator_10);
        updateUI();

        mIsWindowAttached = false;

        mTrayListeners = new ArrayList<>();

        mMinPadding = WidgetPlacement.pixelDimension(getContext(), R.dimen.tray_icon_padding_min);
        mMaxPadding = WidgetPlacement.pixelDimension(getContext(), R.dimen.tray_icon_padding_max);

        mAudio = AudioEngine.fromContext(aContext);

        mWidgetManager.addUpdateListener(this);
        mWidgetManager.getServicesProvider().getDownloadsManager().addListener(this);

        mConnectivityReceived = ((VRBrowserApplication)getContext().getApplicationContext()).getConnectivityReceiver();
        mConnectivityReceived.addListener(this);

        mWifiSSID = getContext().getString(R.string.tray_wifi_no_connection);

        updateTime();
        OnConnectivityChanged(ConnectivityReceiver.isNetworkAvailable(getContext()));

        if (DeviceType.getType() == DeviceType.OculusQuest) {
            mTrayViewModel.setLeftControllerIcon(R.drawable.ic_icon_statusbar_leftcontroller);
            mTrayViewModel.setRightControllerIcon(R.drawable.ic_icon_statusbar_rightcontroller);
        }
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
            if (isImmersive()) {
                return;
            }
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            notifyPrivateBrowsingClicked();
            view.requestFocusFromTouch();
        });

        mBinding.settingsButton.setOnHoverListener(mButtonScaleHoverListener);
        mBinding.settingsButton.setOnClickListener(view -> {
            if (isImmersive()) {
                return;
            }
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            toggleSettingsDialog();
            if (mSettingsWidget.isVisible()) {
                view.requestFocusFromTouch();
            }
        });

        mBinding.tabsButton.setOnHoverListener(mButtonScaleHoverListener);
        mBinding.tabsButton.setOnClickListener(view -> {
            if (isImmersive()) {
                return;
            }
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            view.requestFocusFromTouch();
            notifyTabsClicked();
        });

        mBinding.addwindowButton.setOnHoverListener(mButtonScaleHoverListener);
        mBinding.addwindowButton.setOnClickListener(view -> {
            if (isImmersive()) {
                return;
            }
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            view.requestFocusFromTouch();

            notifyAddWindowClicked();
        });

        mBinding.libraryButton.setOnHoverListener(mButtonScaleHoverListener);
        mBinding.libraryButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            notifyLibraryClicked();
            view.requestFocusFromTouch();
        });

        mBinding.wifi.setOnHoverListener((view, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                NotificationManager.Notification notification = new NotificationManager.Builder(TrayWidget.this)
                        .withView(mBinding.wifi)
                        .withDensity(R.dimen.tray_tooltip_density)
                        .withLayout(R.layout.tooltip)
                        .withString(mWifiSSID)
                        .withAutoHide(false)
                        .withMargin(-15.0f)
                        .withPosition(NotificationManager.Notification.TOP).build();
                NotificationManager.show(WIFI_NOTIFICATION_ID, notification);

            } else if (motionEvent.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                NotificationManager.hide(WIFI_NOTIFICATION_ID);
            }

            return false;
        });

        mBinding.leftController.setOnHoverListener((view, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                NotificationManager.Notification notification = new NotificationManager.Builder(TrayWidget.this)
                        .withView(mBinding.leftController)
                        .withDensity(R.dimen.tray_tooltip_density)
                        .withLayout(R.layout.tooltip)
                        .withString(getContext().getString(
                                R.string.tray_status_left_controller,
                                String.format(
                                        LocaleUtils.getDisplayLanguage(
                                                getContext()).getLocale(),
                                        "%d%%",
                                        mLeftControllerBatteryLevel
                                )
                        ))
                        .withAutoHide(false)
                        .withMargin(-15.0f)
                        .withPosition(NotificationManager.Notification.TOP).build();
                NotificationManager.show(LEFT_CONTROLLER_NOTIFICATION_ID, notification);

            } else if (motionEvent.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                NotificationManager.hide(LEFT_CONTROLLER_NOTIFICATION_ID);
            }

            return false;
        });

        mBinding.rightController.setOnHoverListener((view, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                NotificationManager.Notification notification = new NotificationManager.Builder(TrayWidget.this)
                        .withView(mBinding.rightController)
                        .withDensity(R.dimen.tray_tooltip_density)
                        .withLayout(R.layout.tooltip)
                        .withString(getContext().getString(
                                R.string.tray_status_right_controller,
                                String.format(
                                        LocaleUtils.getDisplayLanguage(
                                                getContext()).getLocale(),
                                        "%d%%",
                                        mRightControllerBatteryLevel
                                )
                        ))
                        .withAutoHide(false)
                        .withMargin(-15.0f)
                        .withPosition(NotificationManager.Notification.TOP).build();
                NotificationManager.show(RIGHT_CONTROLLER_NOTIFICATION_ID, notification);

            } else if (motionEvent.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                NotificationManager.hide(RIGHT_CONTROLLER_NOTIFICATION_ID);
            }

            return false;
        });

        mBinding.headset.setOnHoverListener((view, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                NotificationManager.Notification notification = new NotificationManager.Builder(TrayWidget.this)
                        .withView(mBinding.headset)
                        .withDensity(R.dimen.tray_tooltip_density)
                        .withLayout(R.layout.tooltip)
                        .withString(getContext().getString(
                                R.string.tray_status_headset,
                                String.format(
                                        LocaleUtils.getDisplayLanguage(
                                                getContext()).getLocale(),
                                        "%d%%",
                                        mHeadsetBatteryLevel
                                )
                        ))
                        .withAutoHide(false)
                        .withMargin(-15.0f)
                        .withPosition(NotificationManager.Notification.TOP).build();
                NotificationManager.show(HEADSET_NOTIFICATION_ID, notification);

            } else if (motionEvent.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                NotificationManager.hide(HEADSET_NOTIFICATION_ID);
            }

            return false;
        });

        mBinding.time.setOnHoverListener((view, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                NotificationManager.Notification notification = new NotificationManager.Builder(TrayWidget.this)
                        .withView(mBinding.time)
                        .withDensity(R.dimen.tray_tooltip_density)
                        .withLayout(R.layout.tooltip)
                        .withString(getFormattedDate())
                        .withAutoHide(false)
                        .withMargin(-15.0f)
                        .withPosition(NotificationManager.Notification.TOP).build();
                NotificationManager.show(TIME_NOTIFICATION_ID, notification);

            } else if (motionEvent.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                NotificationManager.hide(TIME_NOTIFICATION_ID);
            }

            return false;
        });

        mBinding.leftController.setVisibility(mLeftControllerBatteryLevel < 0 ? View.GONE : View.VISIBLE);
        mBinding.rightController.setVisibility(mRightControllerBatteryLevel < 0 ? View.GONE : View.VISIBLE);

        updateTime();
        updateWifi();
    }

    public void start(Context context) {
        if (mBroadcastReceiver == null) {
            mBroadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    String action = intent.getAction();
                    if ((action != null) && action.compareTo(Intent.ACTION_TIME_TICK) == 0) {
                        updateTime();
                    }
                }
            };
        }
        context.registerReceiver(mBroadcastReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
    }

    public void stop(Context context) {
        if (mBroadcastReceiver != null) {
            context.unregisterReceiver(mBroadcastReceiver);
        }
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

    private void notifyLibraryClicked() {
        hideNotifications();
        mTrayListeners.forEach(TrayListener::onLibraryClicked);
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
        aPlacement.textureScale *= aPlacement.worldWidth;
    }

    @Override
    public void onResume() {
        super.onResume();

        updateTime();
    }

    @Override
    public void releaseWidget() {
        mWidgetManager.removeUpdateListener(this);
        mWidgetManager.getServicesProvider().getDownloadsManager().removeListener(this);
        mWidgetManager.getServicesProvider().getConnectivityReceiver().removeListener(this);
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
            mViewModel.getIsLibraryVisible().removeObserver(mIsLibraryVisible);
            mViewModel.getIsPrivateSession().removeObserver(mIsPrivateSession);
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
        mViewModel.getIsLibraryVisible().observe((VRBrowserActivity)getContext(), mIsLibraryVisible);
        mViewModel.getIsPrivateSession().observe((VRBrowserActivity)getContext(), mIsPrivateSession);

        mBinding.setViewmodel(mViewModel);

        SessionStore.get().getBookmarkStore().addListener(mBookmarksListener);

        mIsWindowAttached = true;
    }

    private Observer<ObservableBoolean> mIsLibraryVisible = aBoolean -> {
        if (mBinding.libraryButton.isHovered()) {
            return;
        }
        if (aBoolean.get()) {
            animateViewPadding(mBinding.libraryButton, mMaxPadding, mMinPadding, ICON_ANIMATION_DURATION);
        } else {
            animateViewPadding(mBinding.libraryButton, mMinPadding, mMaxPadding, ICON_ANIMATION_DURATION);
        }
    };

    private Observer<ObservableBoolean> mIsPrivateSession = aBoolean -> {
        if (mBinding.privateButton.isHovered() || mViewModel.getIsPrivateSession().getValue().get() == aBoolean.get()) {
            return;
        }
        if (aBoolean.get()) {
            animateViewPadding(mBinding.privateButton, mMaxPadding, mMinPadding, ICON_ANIMATION_DURATION);
        } else {
            animateViewPadding(mBinding.privateButton, mMinPadding, mMaxPadding, ICON_ANIMATION_DURATION);
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
        showNotification(BOOKMARK_ADDED_NOTIFICATION_ID, mBinding.libraryButton, R.string.bookmarks_saved_notification);
    }

    public void showWebAppAddedNotification() {
        showNotification(WEB_APP_ADDED_NOTIFICATION_ID, mBinding.libraryButton, R.string.web_apps_saved_notification);
    }

    public void showDownloadCompletedNotification(String filename) {
        showNotification(DOWNLOAD_COMPLETED_NOTIFICATION_ID,
                mBinding.libraryButton,
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
                    .withMargin(-75.0f)
                    .withZTranslation(20.0f).build();
            NotificationManager.show(notificationId, notification);
        }
    }

    private void hideNotifications() {
        NotificationManager.hideAll();
    }

    private boolean isImmersive() {
        if (mWidgetManager != null && mWidgetManager.isWebXRPresenting()) {
            return true;
        }

        if (mViewModel != null) {
            return mViewModel.getIsFullscreen().getValue().get();
        }
        return false;
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
        long inProgressNum = downloads.stream().filter(item -> item.inProgress()).count();
        mTrayViewModel.setDownloadsNumber((int)inProgressNum);
        if (inProgressNum == 0) {
            mBinding.libraryButton.setLevel(0);

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
                mBinding.libraryButton.setLevel((int)percent*100);
            }
        }
    }

    @Override
    public void onDownloadCompleted(@NonNull Download download) {
        showDownloadCompletedNotification(download.getFilename());
    }

    private void updateTime() {
        Date currentTime = Calendar.getInstance().getTime();
        String androidDateTime = DateFormat.getTimeFormat(getContext()).format(currentTime);
        String AmPm = "";
        SimpleDateFormat format = new SimpleDateFormat("HH:mm", LocaleUtils.getDisplayLanguage(getContext()).getLocale());
        if (!Character.isDigit(androidDateTime.charAt(androidDateTime.length() - 1))) {
            if (androidDateTime.contains(format.getDateFormatSymbols().getAmPmStrings()[Calendar.AM])) {
                AmPm = " " + format.getDateFormatSymbols().getAmPmStrings()[Calendar.AM];
            } else {
                AmPm = " " + format.getDateFormatSymbols().getAmPmStrings()[Calendar.PM];
            }
            androidDateTime = androidDateTime.replace(AmPm, "");
        }
        mTrayViewModel.setTime(androidDateTime);
        mTrayViewModel.setPm(AmPm);
    }

    @Override
    public void OnConnectivityChanged(boolean connected) {
        mTrayViewModel.setWifiConnected(connected);
        if (!connected) {
            mLastWifiLevel = -1;
            mWifiSSID = getContext().getString(R.string.tray_wifi_no_connection);
        }
    }


    private boolean updateWifiIcon(final int level) {
        try {
            Drawable icon = mBinding.wifiIcon.getDrawable();
            if (icon == null) {
                return false;
            }
            LayerDrawable layerDrawable = (LayerDrawable)icon;

            VectorDrawable drawable = (VectorDrawable) layerDrawable.findDrawableByLayerId(R.id.wifi_layer1);
            if (drawable != null) {
                drawable.setAlpha(level >= 0 ? 255 : 0);
            }
            drawable = (VectorDrawable) layerDrawable.findDrawableByLayerId(R.id.wifi_layer2);
            if (drawable != null) {
                drawable.setAlpha(level >= 1 ? 255 : 0);
            }
            drawable = (VectorDrawable) layerDrawable.findDrawableByLayerId(R.id.wifi_layer3);
            if (drawable != null) {
                drawable.setAlpha(level >= 2 ? 255 : 0);
            }
            drawable = (VectorDrawable) layerDrawable.findDrawableByLayerId(R.id.wifi_layer4);
            if (drawable != null) {
                drawable.setAlpha(level >= 3 ? 255 : 0);
            }

            return true;
        } catch (Exception e) {
            Log.e(LOGTAG, "Failed to update wifi icon");
        }

        return false;
    }

    private int getWifiSignalStrength(@NonNull WifiManager wifiManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ConnectivityManager cm = getContext().getSystemService(ConnectivityManager.class);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities netCaps = cm.getNetworkCapabilities(n);
            return netCaps != null ? netCaps.getSignalStrength() : 0;
        } else {
            return wifiManager.getConnectionInfo().getRssi();
        }
    }
    
    private void updateWifi() {
        // We are collecting sensitive data here, so we should ensure the user granted permissions.
        if (!(SettingsStore.getInstance(getContext()).isTermsServiceAccepted() &&
                SettingsStore.getInstance(getContext()).isPrivacyPolicyAccepted())) {
            return;
        }

        if ((mTrayViewModel.getWifiConnected().getValue() != null) && mTrayViewModel.getWifiConnected().getValue().get()) {
            WifiManager wifiManager = (WifiManager) getContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                int level;
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                    level = wifiManager.calculateSignalLevel(getWifiSignalStrength(wifiManager));
                } else {
                    level = WifiManager.calculateSignalLevel(getWifiSignalStrength(wifiManager), 4);
                }
                if (level != mLastWifiLevel && updateWifiIcon(level)) {
                    mLastWifiLevel = level;
                }
                // Getting the SSID, even if it's just to show it to the user, is considered
                // "recollection of personal information" by Huawei store in Mainland China so avoid
                // getting it.
                if (BuildConfig.FLAVOR_store.toLowerCase().contains("mainlandchina") && DeviceType.isHVRBuild()) {
                    mWifiSSID = getContext().getString(R.string.tray_wifi_unavailable_ssid);
                } else {
                    WifiInfo currentWifi = wifiManager.getConnectionInfo();
                    if (currentWifi != null) {
                        mWifiSSID = currentWifi.getSSID().replaceAll("\"", "");

                    } else {
                        mWifiSSID = getContext().getString(R.string.tray_wifi_no_connection);
                    }
                }
            }
        }
    }

    private int toBatteryLevel(final int level) {
        if (level > 75) {
            return R.drawable.ic_icon_statusbar_indicator;
        } else if (level > 50) {
            return R.drawable.ic_icon_statusbar_indicator_75;
        } else if (level > 25) {
            return R.drawable.ic_icon_statusbar_indicator_50;
        } else if (level > 10) {
            return R.drawable.ic_icon_statusbar_indicator_25;
        }
        return R.drawable.ic_icon_statusbar_indicator_10;
    }

    public void setBatteryLevels(final int headset, final boolean isCharging, final int leftController, final int rightController) {
        updateWifi();
        if (DeviceType.getType() == DeviceType.OculusQuest) {
            mTrayViewModel.setLeftControllerIcon(R.drawable.ic_icon_statusbar_leftcontroller);
            mTrayViewModel.setRightControllerIcon(R.drawable.ic_icon_statusbar_rightcontroller);
        }
        mTrayViewModel.setHeadsetIcon(isCharging ? R.drawable.ic_icon_statusbar_headset_charging : R.drawable.ic_icon_statusbar_headset_normal);
        mTrayViewModel.setHeadsetBatteryLevel(toBatteryLevel(headset));

        mHeadsetBatteryLevel = headset;
        mLeftControllerBatteryLevel = leftController;
        mRightControllerBatteryLevel = rightController;

        if (leftController < 0) {
            mBinding.leftController.setVisibility(View.GONE);
        } else {
            mBinding.leftController.setVisibility(View.VISIBLE);
            mTrayViewModel.setLeftControllerBatteryLevel(toBatteryLevel(leftController));
        }
        if (rightController < 0) {
            mBinding.rightController.setVisibility(View.GONE);
        } else {
            mBinding.rightController.setVisibility(View.VISIBLE);
            mTrayViewModel.setRightControllerBatteryLevel(toBatteryLevel(rightController));
        }
    }

    @NonNull
    private String getFormattedDate() {
        java.text.DateFormat format = SimpleDateFormat.getDateInstance(
                SimpleDateFormat.FULL, LocaleUtils.getDisplayLanguage(getContext()).getLocale());
        return format.format(new Date());
    }
}
