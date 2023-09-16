/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
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
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;

import com.igalia.wolvic.BuildConfig;
import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.VRBrowserApplication;
import com.igalia.wolvic.audio.AudioEngine;
import com.igalia.wolvic.browser.Accounts;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.databinding.SettingsBinding;
import com.igalia.wolvic.db.SitePermission;
import com.igalia.wolvic.telemetry.TelemetryService;
import com.igalia.wolvic.ui.viewmodel.SettingsViewModel;
import com.igalia.wolvic.ui.widgets.UIWidget;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.ui.widgets.WindowWidget;
import com.igalia.wolvic.ui.widgets.dialogs.ClearUserDataDialogWidget;
import com.igalia.wolvic.ui.widgets.dialogs.RestartDialogWidget;
import com.igalia.wolvic.ui.widgets.dialogs.UIDialog;
import com.igalia.wolvic.utils.RemoteProperties;
import com.igalia.wolvic.utils.StringUtils;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import mozilla.components.Build;
import mozilla.components.concept.storage.Login;
import mozilla.components.concept.sync.AccountObserver;
import mozilla.components.concept.sync.AuthFlowError;
import mozilla.components.concept.sync.AuthType;
import mozilla.components.concept.sync.OAuthAccount;
import mozilla.components.concept.sync.Profile;

public class SettingsWidget extends UIDialog implements SettingsView.Delegate {

    private SettingsBinding mBinding;
    private AudioEngine mAudio;
    private SettingsView mCurrentView;
    private int mViewMarginH;
    private int mViewMarginV;
    private RestartDialogWidget mRestartDialog;
    private ClearUserDataDialogWidget mClearUserDataDialog;
    private Accounts mAccounts;
    private Executor mUIThreadExecutor;
    private SettingsView.SettingViewType mOpenDialog;
    private SettingsViewModel mSettingsViewModel;

    class VersionGestureListener extends GestureDetector.SimpleOnGestureListener {

        private boolean mIsHash;

        @Override
        public boolean onDown (MotionEvent e) {
            mBinding.buildText.setText(mIsHash ?
                    StringUtils.versionCodeToDate(getContext(), BuildConfig.VERSION_CODE) :
                    BuildConfig.GIT_HASH + " (AC " + Build.version + ")");

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
        mSettingsViewModel = new ViewModelProvider(
                (VRBrowserActivity)getContext(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(((VRBrowserActivity) getContext()).getApplication()))
                .get(SettingsViewModel.class);

        updateUI();

        mOpenDialog = SettingsView.SettingViewType.MAIN;

        mAccounts = ((VRBrowserApplication)getContext().getApplicationContext()).getAccounts();
        mAccounts.addAccountListener(mAccountObserver);

        mUIThreadExecutor = ((VRBrowserApplication)getContext().getApplicationContext()).getExecutors().mainThread();

        mAudio = AudioEngine.fromContext(getContext());

        mViewMarginH = mWidgetPlacement.width - WidgetPlacement.dpDimension(getContext(), R.dimen.options_width);
        mViewMarginH = WidgetPlacement.convertDpToPixel(getContext(), mViewMarginH);
        mViewMarginV = mWidgetPlacement.height - WidgetPlacement.dpDimension(getContext(), R.dimen.options_height);
        mViewMarginV = WidgetPlacement.convertDpToPixel(getContext(), mViewMarginV);
    }

    @SuppressLint("ClickableViewAccessibility")
    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.settings, this, true);
        mBinding.setSettingsmodel(mSettingsViewModel);

        mBinding.backButton.setOnClickListener(v -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            onDismiss();
        });

        mBinding.languageButton.setOnClickListener(view -> {
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

            showView(SettingsView.SettingViewType.ENVIRONMENT);
        });

        try {
            PackageInfo pInfo;
            if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.S_V2) {
                pInfo = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), PackageManager.PackageInfoFlags.of(0));
            } else {
                pInfo = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0);
            }
            String app_name = getResources().getString(R.string.app_name);
            mBinding.versionText.setText(Html.fromHtml("<b>" + app_name + "</b>" +
                            " <b>" + pInfo.versionName + "</b>",
                    Html.FROM_HTML_MODE_LEGACY));

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        mBinding.buildText.setText("versionCode " + BuildConfig.VERSION_CODE);

        final GestureDetector gd = new GestureDetector(getContext(), new VersionGestureListener());
        mBinding.settingsMasthead.setOnTouchListener((view, motionEvent) -> {
            if (gd.onTouchEvent(motionEvent)) {
                return true;
            }
            return view.performClick();
        });

        mBinding.surveyLink.setOnClickListener(v -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

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

        mBinding.fxaButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            manageAccount();
        });

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

            showView(SettingsView.SettingViewType.CONTROLLER);
        });

        mBinding.whatsNewButton.setOnClickListener(v -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            SettingsStore.getInstance(getContext()).setRemotePropsVersionName(BuildConfig.VERSION_NAME);
            RemoteProperties props = mSettingsViewModel.getProps().getValue().get(BuildConfig.VERSION_NAME);
            if (props != null) {
                mWidgetManager.openNewTabForeground(props.getWhatsNewUrl());
            }
            onDismiss();
        });

        mCurrentView = null;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        showView(mOpenDialog);
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
        updatePlacementTranslationZ();
    }

    @Override
    public void updatePlacementTranslationZ() {
        getPlacement().translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.settings_world_z) -
                                      WidgetPlacement.getWindowWorldZMeters(getContext());
    }

    @Override
    public void attachToWindow(@NonNull WindowWidget window) {
        mWidgetPlacement.parentHandle = window.getHandle();
    }

    private void onSettingsPrivacyClick() {
        showView(SettingsView.SettingViewType.PRIVACY);
    }

    private void manageAccount() {
        switch(mAccounts.getAccountStatus()) {
            case SIGNED_OUT:
            case NEEDS_RECONNECT:
                if (mAccounts.getAccountStatus() == Accounts.AccountStatus.SIGNED_IN) {
                    mAccounts.logoutAsync();

                } else {
                    hide(KEEP_WIDGET);

                    CompletableFuture<String> result = mAccounts.authUrlAsync();
                    if (result != null) {
                        result.thenAcceptAsync((url) -> {
                            if (url == null) {
                                mAccounts.logoutAsync();

                            } else {
                                mWidgetManager.openNewTabForeground(url);
                                Session currentSession = mWidgetManager.getFocusedWindow().getSession();
                                String sessionId = currentSession != null ? currentSession.getId() : null;

                                mAccounts.setOrigin(Accounts.LoginOrigin.SETTINGS, sessionId);

                                TelemetryService.Tabs.openedCounter(TelemetryService.Tabs.TabSource.FXA_LOGIN);
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
                post(() -> showView(SettingsView.SettingViewType.FXA));
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
        public void onReady(@Nullable OAuthAccount oAuthAccount) {

        }

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

        @Override
        public void onFlowError(@NotNull AuthFlowError authFlowError) {
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
        showView(SettingsView.SettingViewType.DEVELOPER);
    }

    private void onLanguageOptionsClick() {
        showView(SettingsView.SettingViewType.LANGUAGE);
    }

    private void onDisplayOptionsClick() {
        showView(SettingsView.SettingViewType.DISPLAY);
    }

    @Override
    public void showView(SettingsView.SettingViewType aType) {
        showView(aType, null);
    }

    @Override
    public void showView(SettingsView.SettingViewType aType, @Nullable Object extras) {
        switch (aType) {
            case MAIN:
                showView((SettingsView) null);
                break;
            case LANGUAGE:
                showView(new LanguageOptionsView(getContext(), mWidgetManager));
                break;
            case LANGUAGE_DISPLAY:
                showView(new DisplayLanguageOptionsView(getContext(), mWidgetManager));
                break;
            case LANGUAGE_CONTENT:
                showView(new ContentLanguageOptionsView(getContext(), mWidgetManager));
                break;
            case LANGUAGE_VOICE_SERVICE:
                showView(new VoiceSearchServiceOptionsView(getContext(), mWidgetManager));
                break;
            case LANGUAGE_VOICE:
                showView(new VoiceSearchLanguageOptionsView(getContext(), mWidgetManager));
                break;
            case DISPLAY:
                showView(new DisplayOptionsView(getContext(), mWidgetManager));
                break;
            case PRIVACY:
                showView(new PrivacyOptionsView(getContext(), mWidgetManager));
                break;
            case POPUP_EXCEPTIONS:
                showView(new SitePermissionsOptionsView(getContext(), mWidgetManager, SitePermission.SITE_PERMISSION_POPUP));
                break;
            case WEBXR_EXCEPTIONS:
                showView(new SitePermissionsOptionsView(getContext(), mWidgetManager, SitePermission.SITE_PERMISSION_WEBXR));
                break;
            case DEVELOPER:
                showView(new DeveloperOptionsView(getContext(), mWidgetManager));
                break;
            case FXA:
                showView(new FxAAccountOptionsView(getContext(), mWidgetManager));
                break;
            case ENVIRONMENT:
                showView(new EnvironmentOptionsView(getContext(), mWidgetManager));
                break;
            case CONTROLLER:
                showView(new ControllerOptionsView(getContext(), mWidgetManager));
                break;
            case TRACKING_EXCEPTION:
                showView(new TrackingPermissionsOptionsView(getContext(), mWidgetManager));
                break;
            case LOGINS_AND_PASSWORDS:
                showView(new LoginAndPasswordsOptionsView(getContext(), mWidgetManager));
                break;
            case LOGIN_EXCEPTIONS:
                showView(new SitePermissionsOptionsView(getContext(), mWidgetManager, SitePermission.SITE_PERMISSION_AUTOFILL));
                break;
            case SAVED_LOGINS:
                showView(new SavedLoginsOptionsView(getContext(), mWidgetManager));
                break;
            case LOGIN_EDIT:
                if (extras != null) {
                    showView(new LoginEditOptionsView(getContext(), mWidgetManager, (Login)extras));
                }
                break;
            case SEARCH_ENGINE:
                showView(new SearchEngineView(getContext(), mWidgetManager));
                break;
            case TERMS_OF_SERVICE:
                showView(new LegalDocumentView(getContext(), mWidgetManager, LegalDocumentView.LegalDocument.TERMS_OF_SERVICE));
                break;
            case PRIVACY_POLICY:
                showView(new LegalDocumentView(getContext(), mWidgetManager, LegalDocumentView.LegalDocument.PRIVACY_POLICY));
                break;
        }
    }

    private void showView(SettingsView aView) {
        if (mCurrentView != null) {
            mCurrentView.onHidden();
            this.removeView(mCurrentView);
        }
        mCurrentView = aView;
        if (mCurrentView != null) {
            mOpenDialog = aView.getType();
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
            updateUI();
            mBinding.optionsLayout.setVisibility(View.VISIBLE);
            updateCurrentAccountState();
        }
    }

    public void show(@ShowFlags int aShowFlags, @NonNull SettingsView.SettingViewType settingDialog) {
        if (!isVisible()) {
            show(aShowFlags);
        }

        showView(settingDialog);
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
                    showView(SettingsView.SettingViewType.LANGUAGE);

                } else if (isPrivacySubView(mCurrentView)) {
                    showView(SettingsView.SettingViewType.PRIVACY);

                } else if (isLoginsSubview(mCurrentView)) {
                    showView(SettingsView.SettingViewType.LOGINS_AND_PASSWORDS);

                } else if (isSavedLoginsSubview(mCurrentView)) {
                    showView(SettingsView.SettingViewType.SAVED_LOGINS);

                } else {
                    showView(SettingsView.SettingViewType.MAIN);
                }
            }
        } else {
            super.onDismiss();
        }
    }

    @Override
    public void exitWholeSettings() {
        showView(SettingsView.SettingViewType.MAIN);
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
    public void showClearUserDataDialog() {
        if (mClearUserDataDialog == null) {
            mClearUserDataDialog = new ClearUserDataDialogWidget(getContext());
        }

        mClearUserDataDialog.show(REQUEST_FOCUS);
    }

    @Override
    public void showAlert(String aTitle, String aMessage) {
        mWidgetManager.getFocusedWindow().showAlert(aTitle, aMessage, null);
    }

    private boolean isLanguagesSubView(View view) {
        return view instanceof DisplayLanguageOptionsView ||
                view instanceof ContentLanguageOptionsView ||
                view instanceof VoiceSearchLanguageOptionsView;
    }

    private boolean isPrivacySubView(View view) {
        return (view instanceof SitePermissionsOptionsView &&
                ((SitePermissionsOptionsView)view).getType() != SettingsView.SettingViewType.LOGIN_EXCEPTIONS) ||
                view instanceof LoginAndPasswordsOptionsView;
    }

    private boolean isLoginsSubview(View view) {
        return (view instanceof SitePermissionsOptionsView &&
                ((SitePermissionsOptionsView)view).getType() == SettingsView.SettingViewType.LOGIN_EXCEPTIONS) ||
                view instanceof SavedLoginsOptionsView;
    }

    private boolean isSavedLoginsSubview(View view) {
        return view instanceof LoginEditOptionsView;
    }

}
