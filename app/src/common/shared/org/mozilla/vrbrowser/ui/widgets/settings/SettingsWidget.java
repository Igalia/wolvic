/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.text.Html;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;

import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.vrbrowser.BuildConfig;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserActivity;
import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.Accounts;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.SettingsBinding;
import org.mozilla.vrbrowser.telemetry.GleanMetricsService;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.ui.widgets.dialogs.RestartDialogWidget;
import org.mozilla.vrbrowser.ui.widgets.dialogs.UIDialog;
import org.mozilla.vrbrowser.utils.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import mozilla.components.concept.sync.AccountObserver;
import mozilla.components.concept.sync.AuthType;
import mozilla.components.concept.sync.OAuthAccount;
import mozilla.components.concept.sync.Profile;

public class SettingsWidget extends UIDialog implements SettingsView.Delegate {

    public enum SettingDialog {
        MAIN, LANGUAGE, DISPLAY, PRIVACY, DEVELOPER, FXA, ENVIRONMENT, CONTROLLER
    }

    private SettingsBinding mBinding;
    private AudioEngine mAudio;
    private SettingsView mCurrentView;
    private int mViewMarginH;
    private int mViewMarginV;
    private RestartDialogWidget mRestartDialog;
    private Accounts mAccounts;
    private Executor mUIThreadExecutor;

    class VersionGestureListener extends GestureDetector.SimpleOnGestureListener {

        private boolean mIsHash;

        @Override
        public boolean onDown (MotionEvent e) {
            mBinding.buildText.setText(mIsHash ? StringUtils.versionCodeToDate(getContext(), BuildConfig.VERSION_CODE) : BuildConfig.GIT_HASH);

            mIsHash = !mIsHash;

            return true;
        }
    }

    public SettingsWidget(Context aContext) {
        super(aContext);
        initialize();
    }

    public SettingsWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize();
    }

    public SettingsWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initialize() {
        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.settings, this, true);

        mAccounts = ((VRBrowserApplication)getContext().getApplicationContext()).getAccounts();
        mAccounts.addAccountListener(mAccountObserver);

        mUIThreadExecutor = ((VRBrowserApplication)getContext().getApplicationContext()).getExecutors().mainThread();

        mBinding.backButton.setOnClickListener(v -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            onDismiss();
        });

        mBinding.reportIssueButton.setOnClickListener(v -> {
            onSettingsReportClick();
            onDismiss();
        });

        mBinding.languageButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            onLanguageOptionsClick();
        });

        mBinding.privacyButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            onSettingsPrivacyClick();
        });

        mBinding.displayButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            onDisplayOptionsClick();
        });

        mBinding.environmentButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            showEnvironmentOptionsDialog();
        });

        try {
            PackageInfo pInfo = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0);
            String app_name = getResources().getString(R.string.app_name);
            String[] app_name_parts = app_name.split(" ");
            mBinding.versionText.setText(Html.fromHtml("<b>" + app_name_parts[0] + "</b>" +
                    " " + app_name_parts[1] + " " +
                    " <b>" + pInfo.versionName + "</b>",
                    Html.FROM_HTML_MODE_LEGACY));

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        mBinding.buildText.setText(StringUtils.versionCodeToDate(getContext(), BuildConfig.VERSION_CODE));

        final GestureDetector gd = new GestureDetector(getContext(), new VersionGestureListener());
        mBinding.settingsMasthead.setOnTouchListener((view, motionEvent) -> {
            if (gd.onTouchEvent(motionEvent)) {
                return true;
            }
            return view.performClick();
        });

        mBinding.surveyLink.setOnClickListener(v -> {
            mWidgetManager.openNewTabForeground(getResources().getString(R.string.survey_link));
            exitWholeSettings();
        });

        mBinding.helpButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            mWidgetManager.openNewTabForeground(getContext().getString(R.string.help_url));
            onDismiss();
        });

        mBinding.fxaButton.setOnClickListener(view ->
                manageAccount()
        );

        mBinding.developerOptionsButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            onDeveloperOptionsClick();
        });

        mBinding.controllerOptionsButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            showControllerOptionsDialog();
        });

        mAudio = AudioEngine.fromContext(getContext());

        mViewMarginH = mWidgetPlacement.width - WidgetPlacement.dpDimension(getContext(), R.dimen.options_width);
        mViewMarginH = WidgetPlacement.convertDpToPixel(getContext(), mViewMarginH);
        mViewMarginV = mWidgetPlacement.height - WidgetPlacement.dpDimension(getContext(), R.dimen.options_height);
        mViewMarginV = WidgetPlacement.convertDpToPixel(getContext(), mViewMarginV);
    }

    @Override
    public void releaseWidget() {
        mAccounts.removeAccountListener(mAccountObserver);

        super.releaseWidget();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.settings_width);
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.settings_height);
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.0f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.translationY = WidgetPlacement.unitFromMeters(getContext(), R.dimen.settings_world_y) -
                                  WidgetPlacement.unitFromMeters(getContext(), R.dimen.window_world_y);
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.settings_world_z) -
                                  WidgetPlacement.unitFromMeters(getContext(), R.dimen.window_world_z);
    }

    private void onSettingsPrivacyClick() {
        showView(new PrivacyOptionsView(getContext(), mWidgetManager));
    }

    private void onSettingsReportClick() {
        Session session = SessionStore.get().getActiveSession();
        String url = session.getCurrentUri();

        try {
            if (url == null) {
                // In case the user had no active sessions when reporting, just leave the URL field empty.
                url = "";
            } else if (url.startsWith("jar:") || url.startsWith("resource:") || url.startsWith("about:") || url.startsWith("data:")) {
                url = "";
            } else if (session.isHomeUri(url)) {
                // Use the original URL (without any hash).
                url = session.getHomeUri();
            }

            url = URLEncoder.encode(url, "UTF-8");

        } catch (UnsupportedEncodingException e) {
            Log.e(LOGTAG, "Cannot encode URL");
        }

        mWidgetManager.openNewTabForeground(getContext().getString(R.string.private_report_url, url));

        onDismiss();
    }

    private void manageAccount() {
        switch(mAccounts.getAccountStatus()) {
            case SIGNED_OUT:
            case NEEDS_RECONNECT:
                if (mAccounts.getAccountStatus() == Accounts.AccountStatus.SIGNED_IN) {
                    mAccounts.logoutAsync();

                } else {
                    hide(REMOVE_WIDGET);

                    CompletableFuture<String> result = mAccounts.authUrlAsync();
                    if (result != null) {
                        result.thenAcceptAsync((url) -> {
                            if (url == null) {
                                mAccounts.logoutAsync();

                            } else {
                                mAccounts.setLoginOrigin(Accounts.LoginOrigin.SETTINGS);
                                mWidgetManager.openNewTabForeground(url);
                                WidgetManagerDelegate widgetManager = ((VRBrowserActivity)getContext());
                                widgetManager.getFocusedWindow().getSession().setUaMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
                                GleanMetricsService.Tabs.openedCounter(GleanMetricsService.Tabs.TabSource.FXA_LOGIN);
                            }

                        }, mUIThreadExecutor).exceptionally(throwable -> {
                            Log.d(LOGTAG, "Error getting the authentication URL: " + throwable.getLocalizedMessage());
                            throwable.printStackTrace();
                            return null;
                        });
                    }
                }
                break;

            case SIGNED_IN:
                post(this::showFXAOptionsDialog);
                break;
        }
    }

    private void updateCurrentAccountState() {
        switch(mAccounts.getAccountStatus()) {
            case NEEDS_RECONNECT:
                mBinding.fxaButton.setText(R.string.settings_fxa_account_reconnect);
                break;

            case SIGNED_IN:
                mBinding.fxaButton.setText(R.string.settings_fxa_account_manage);
                updateProfile(mAccounts.accountProfile());
                break;

            case SIGNED_OUT:
                mBinding.fxaButton.setText(R.string.settings_fxa_account_sign_in);
                updateProfile(mAccounts.accountProfile());
                break;
        }
    }

    private AccountObserver mAccountObserver = new AccountObserver() {

        @Override
        public void onAuthenticated(@NonNull OAuthAccount oAuthAccount, @NonNull AuthType authType) {

        }

        @Override
        public void onProfileUpdated(@NonNull Profile profile) {
            updateProfile(profile);
        }

        @Override
        public void onLoggedOut() {
            post(() -> mBinding.fxaButton.setText(R.string.settings_fxa_account_sign_in));
        }

        @Override
        public void onAuthenticationProblems() {
            post(() -> mBinding.fxaButton.setText(R.string.settings_fxa_account_reconnect));
        }
    };

    private void updateProfile(Profile profile) {
        BitmapDrawable profilePicture = mAccounts.getProfilePicture();
        if (profile != null && profilePicture != null) {
            mBinding.fxaButton.setImageDrawable(profilePicture);

        } else {
            mBinding.fxaButton.setImageDrawable(getContext().getDrawable(R.drawable.ic_icon_settings_account));
        }
    }

    private void onDeveloperOptionsClick() {
        showDeveloperOptionsDialog();
    }

    private void onLanguageOptionsClick() {
        showLanguageOptionsDialog();
    }

    private void onDisplayOptionsClick() {
        showDisplayOptionsDialog();
    }

    public void showView(SettingsView aView) {
        if (mCurrentView != null) {
            mCurrentView.onHidden();
            this.removeView(mCurrentView);
        }
        mCurrentView = aView;
        if (mCurrentView != null) {
            Point viewDimensions = mCurrentView.getDimensions();
            mViewMarginH = mWidgetPlacement.width - viewDimensions.x;
            mViewMarginH = WidgetPlacement.convertDpToPixel(getContext(), mViewMarginH);
            mViewMarginV = mWidgetPlacement.height - viewDimensions.y;
            mViewMarginV = WidgetPlacement.convertDpToPixel(getContext(), mViewMarginV);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            params.leftMargin = params.rightMargin = mViewMarginH / 2;
            params.topMargin = params.bottomMargin = mViewMarginV / 2;
            mCurrentView.onShown();
            this.addView(mCurrentView, params);
            mCurrentView.setDelegate(this);
            mBinding.optionsLayout.setVisibility(View.GONE);

        } else {
            mBinding.optionsLayout.setVisibility(View.VISIBLE);
            updateCurrentAccountState();
        }
    }

    private void showPrivacyOptionsDialog() {
        showView(new PrivacyOptionsView(getContext(), mWidgetManager));
    }

    private void showDeveloperOptionsDialog() {
        showView(new DeveloperOptionsView(getContext(), mWidgetManager));
    }

    private void showControllerOptionsDialog() {
        showView(new ControllerOptionsView(getContext(), mWidgetManager));
    }

    private void showLanguageOptionsDialog() {
        LanguageOptionsView view = new LanguageOptionsView(getContext(), mWidgetManager);
        view.setDelegate(this);
        showView(view);
    }

    private void showDisplayOptionsDialog() {
        showView(new DisplayOptionsView(getContext(), mWidgetManager));
    }

    private void showEnvironmentOptionsDialog() {
        showView(new EnvironmentOptionsView(getContext(), mWidgetManager));
    }

    private void showFXAOptionsDialog() {
        showView(new FxAAccountOptionsView(getContext(), mWidgetManager));
    }

    public void show(@ShowFlags int aShowFlags, @NonNull SettingDialog settingDialog) {
        if (!isVisible()) {
            show(aShowFlags);
        }

        switch (settingDialog) {
            case LANGUAGE:
                showLanguageOptionsDialog();
                break;
            case DISPLAY:
                showDisplayOptionsDialog();
                break;
            case PRIVACY:
                showPrivacyOptionsDialog();
                break;
            case DEVELOPER:
                showDeveloperOptionsDialog();
                break;
            case FXA:
                showFXAOptionsDialog();
                break;
            case ENVIRONMENT:
                showEnvironmentOptionsDialog();
                break;
            case CONTROLLER:
                showControllerOptionsDialog();
                break;
        }
    }

    @Override
    public void show(@ShowFlags int aShowFlags) {
        super.show(aShowFlags);

        updateCurrentAccountState();
    }

    // SettingsView.Delegate
    @Override
    public void onDismiss() {
        if (mCurrentView != null) {
            if (!mCurrentView.isEditing()) {
                if (isLanguagesSubView(mCurrentView)) {
                    showLanguageOptionsDialog();

                } else if (isPrivacySubView(mCurrentView)) {
                    showPrivacyOptionsDialog();

                } else {
                    showView(null);
                }
            }
        } else {
            super.onDismiss();
        }
    }

    @Override
    public void exitWholeSettings() {
        showView(null);
        hide(UIWidget.REMOVE_WIDGET);
    }

    @Override
    public void showRestartDialog() {
        if (mRestartDialog == null) {
            mRestartDialog = new RestartDialogWidget(getContext());
        }

        mRestartDialog.show(REQUEST_FOCUS);
    }

    @Override
    public void showAlert(String aTitle, String aMessage) {
        mWidgetManager.getFocusedWindow().showAlert(aTitle, aMessage, null);
    }

    private boolean isLanguagesSubView(View view) {
        if (view instanceof DisplayLanguageOptionsView ||
                view instanceof ContentLanguageOptionsView ||
                view instanceof  VoiceSearchLanguageOptionsView) {
            return true;
        }

        return false;
    }

    private boolean isPrivacySubView(View view) {
        if (view instanceof PopUpExceptionsOptionsView) {
            return true;
        }

        return false;
    }

}
