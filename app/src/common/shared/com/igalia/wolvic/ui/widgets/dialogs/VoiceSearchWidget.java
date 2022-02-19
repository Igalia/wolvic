package com.igalia.wolvic.ui.widgets.dialogs;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.databinding.DataBindingUtil;


import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.VRBrowserApplication;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.engine.EngineProvider;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.VoiceSearchDialogBinding;
import com.igalia.wolvic.speech.SpeechRecognizer;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.utils.DeviceType;
import com.igalia.wolvic.utils.LocaleUtils;
import com.igalia.wolvic.utils.ViewUtils;

public class VoiceSearchWidget extends UIDialog implements WidgetManagerDelegate.PermissionListener,
        Application.ActivityLifecycleCallbacks {

    public enum State {
        LISTENING,
        SEARCHING,
        SPEECH_ERROR,
        ERROR_NETWORK,
        ERROR_SERVER,
        ERROR_TOO_MANY_REQUESTS,
        ERROR_LANGUAGE_NOT_SUPPORTED,
        PERMISSIONS
    }

    private static final int VOICE_SEARCH_AUDIO_REQUEST_CODE = 7455;

    public interface VoiceSearchDelegate {
        default void OnVoiceSearchResult(String transcription, float confidence) {};
        default void OnPartialVoiceSearchResult(String transcription) {};
        default void OnVoiceSearchError(@SpeechRecognizer.Callback.ErrorType int errorType) {};
    }

    private VoiceSearchDialogBinding mBinding;
    private SpeechRecognizer mSpeechRecognizer;
    private VoiceSearchDelegate mDelegate;
    private ClipDrawable mVoiceInputClipDrawable;
    private AnimatedVectorDrawable mSearchingAnimation;
    private VRBrowserApplication mApplication;
    private boolean mIsSpeechRecognitionRunning = false;
    private boolean mWasSpeechRecognitionRunning = false;

    public VoiceSearchWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public VoiceSearchWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public VoiceSearchWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        // AnimatedVectorDrawable doesn't work with a Hardware Accelerated canvas, we disable it for this view.
        setIsHardwareAccelerationEnabled(false);

        mApplication = (VRBrowserApplication)aContext.getApplicationContext();

        updateUI();

        mWidgetManager.addPermissionListener(this);

        mSearchingAnimation = (AnimatedVectorDrawable) mBinding.voiceSearchAnimationSearching.getDrawable();
        if (DeviceType.isPicoVR()) {
            ViewUtils.forceAnimationOnUI(mSearchingAnimation);
        }

        mSpeechRecognizer = mApplication.getSpeechRecognizer();

        mApplication.registerActivityLifecycleCallbacks(this);
    }

    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.voice_search_dialog, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity)getContext());

        Drawable mVoiceInputBackgroundDrawable = getResources().getDrawable(R.drawable.ic_voice_search_volume_input_black, getContext().getTheme());
        mVoiceInputClipDrawable = new ClipDrawable(getContext().getDrawable(R.drawable.ic_voice_search_volume_input_clip), Gravity.START, ClipDrawable.HORIZONTAL);
        Drawable[] layers = new Drawable[] {mVoiceInputBackgroundDrawable, mVoiceInputClipDrawable };
        mBinding.voiceSearchAnimationListening.setImageDrawable(new LayerDrawable(layers));
        mVoiceInputClipDrawable.setLevel(0);

        mBinding.closeButton.setOnClickListener(view -> hide(KEEP_WIDGET));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        updateUI();
    }

    public void setDelegate(VoiceSearchDelegate delegate) {
        mDelegate = delegate;
    }

    @Override
    public void releaseWidget() {
        mWidgetManager.removePermissionListener(this);
        mApplication.unregisterActivityLifecycleCallbacks(this);

        mSpeechRecognizer.stop();

        super.releaseWidget();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.prompt_dialog_width);
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.prompt_dialog_height);
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.0f;
        aPlacement.translationY = WidgetPlacement.unitFromMeters(getContext(), R.dimen.settings_world_y) -
                WidgetPlacement.unitFromMeters(getContext(), R.dimen.window_world_y);
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.settings_world_z) -
                WidgetPlacement.unitFromMeters(getContext(), R.dimen.window_world_z);
    }

    public void setPlacementForKeyboard(int aHandle) {
        mWidgetPlacement.parentHandle = aHandle;
        mWidgetPlacement.translationY = 0;
        mWidgetPlacement.translationZ = 0;
    }

    SpeechRecognizer.Callback mResultCallback = new SpeechRecognizer.Callback() {
        @Override
        public void onStartListening() {
            // Handle when the api successfully opened the microphone and started listening
            Log.d(LOGTAG, "===> START_LISTEN");
            mBinding.voiceSearchStart.setText( getContext().getString(R.string.voice_search_start));
        }

        @Override
        public void onMicActivity(int level) {
            // Captures the activity from the microphone
            Log.d(LOGTAG, "===> MIC_ACTIVITY");
            mVoiceInputClipDrawable.setLevel(level);
        }

        @Override
        public void onDecoding() {
            // Handle when the speech object changes to decoding state
            Log.d(LOGTAG, "===> DECODING");
            setDecodingState();
        }

        @Override
        public void onPartialResult(String transcription) {
            // When a partial result is available
            Log.d(LOGTAG, "===> PARTIAL_STT_RESULT");
            if (mDelegate != null) {
                mBinding.voiceSearchStart.setText(transcription);
                mDelegate.OnPartialVoiceSearchResult(transcription);
            }
        }

        @Override
        public void onResult(String transcription, float confidence) {
            // When the api finished processing and returned a hypothesis
            Log.d(LOGTAG, "===> STT_RESULT");
            if (mDelegate != null) {
                mDelegate.OnVoiceSearchResult(transcription, confidence);
            }
            hide(KEEP_WIDGET);
        }

        @Override
        public void onNoVoice() {
            // Handle when the api didn't detect any voice
            Log.d(LOGTAG, "===> NO_VOICE");
        }

        @Override
        public void onError(@ErrorType int errorType, @Nullable String error) {
            Log.d(LOGTAG, "===> ERROR: " + error);
            setResultState(errorType);
            if (mDelegate != null) {
                mDelegate.OnVoiceSearchError(errorType);
            }
        }

    };

    public void startVoiceSearch() {
        if (ActivityCompat.checkSelfPermission(getContext().getApplicationContext(), Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity)getContext(), new String[]{Manifest.permission.RECORD_AUDIO},
                    VOICE_SEARCH_AUDIO_REQUEST_CODE);
        } else {
            String locale = LocaleUtils.getVoiceSearchLanguageTag(getContext());
            boolean storeData = SettingsStore.getInstance(getContext()).isSpeechDataCollectionEnabled();
            if (SessionStore.get().getActiveSession().isPrivateMode()) {
                storeData = false;
            }

            mIsSpeechRecognitionRunning = true;

            SpeechRecognizer.Settings settings = new SpeechRecognizer.Settings();
            settings.locale = locale;
            settings.storeData = storeData;
            settings.productTag = getContext().getString(R.string.voice_app_id);

            mSpeechRecognizer.start(settings,
                    EngineProvider.INSTANCE.getDefaultGeckoWebExecutor(getContext()),
                    mResultCallback);
        }
    }

    public void stopVoiceSearch() {
        try {
            mSpeechRecognizer.stop();

        } catch (Exception e) {
            Log.d(LOGTAG, e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Unknown voice error");
            e.printStackTrace();
        }

        mIsSpeechRecognitionRunning = false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        boolean granted = false;
        if (requestCode == VOICE_SEARCH_AUDIO_REQUEST_CODE) {
            for (int result: grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }

            if (granted) {
                show(REQUEST_FOCUS);

            } else {
                super.show(REQUEST_FOCUS);
                setPermissionNotGranted();
            }
        }
    }

    @Override
    public void show(@ShowFlags int aShowFlags) {
        if (!mSpeechRecognizer.shouldDisplayStoreDataPrompt() ||
                SettingsStore.getInstance(getContext()).isSpeechDataCollectionEnabled() ||
                SettingsStore.getInstance(getContext()).isSpeechDataCollectionReviewed()) {
            mWidgetPlacement.parentHandle = mWidgetManager.getFocusedWindow().getHandle();
            super.show(aShowFlags);

            setStartListeningState();

            startVoiceSearch();

        } else {
            mWidgetManager.getFocusedWindow().showDialog(
                    getResources().getString(R.string.voice_samples_collect_data_dialog_title, getResources().getString(R.string.app_name)),
                    R.string.voice_samples_collect_dialog_description2,
                    new int[]{
                            R.string.voice_samples_collect_dialog_do_not_allow,
                            R.string.voice_samples_collect_dialog_allow},
                    (index, isChecked) -> {
                        SettingsStore.getInstance(getContext()).setSpeechDataCollectionReviewed(true);
                        if (index == PromptDialogWidget.POSITIVE) {
                            SettingsStore.getInstance(getContext()).setSpeechDataCollectionEnabled(true);
                        }
                        new Handler(Looper.getMainLooper()).post(() -> show(aShowFlags));
                    },
                    () -> mWidgetManager.openNewTabForeground(getResources().getString(R.string.private_policy_url)));
        }
    }

    @Override
    public void hide(@HideFlags int aHideFlags) {
        super.hide(aHideFlags);
        mBinding.voiceSearchStart.setText( getContext().getString(R.string.voice_search_start));
        stopVoiceSearch();
        mBinding.setState(State.LISTENING);
    }

    private void setStartListeningState() {
        mBinding.setState(State.LISTENING);
        mSearchingAnimation.stop();
        mBinding.executePendingBindings();
    }

    private void setDecodingState() {
        mBinding.setState(State.SEARCHING);
        mSearchingAnimation.start();
        mBinding.executePendingBindings();
    }

    private void setResultState(@SpeechRecognizer.Callback.ErrorType int errorType) {
        stopVoiceSearch();

        postDelayed(() -> {
            switch (errorType) {
                case SpeechRecognizer.Callback.SPEECH_ERROR: mBinding.setState(State.SPEECH_ERROR);
                case SpeechRecognizer.Callback.ERROR_NETWORK: mBinding.setState(State.ERROR_NETWORK);
                case SpeechRecognizer.Callback.ERROR_SERVER: mBinding.setState(State.ERROR_SERVER);
                case SpeechRecognizer.Callback.ERROR_TOO_MANY_REQUESTS: mBinding.setState(State.ERROR_TOO_MANY_REQUESTS);
                case SpeechRecognizer.Callback.ERROR_LANGUAGE_NOT_SUPPORTED: mBinding.setState(State.ERROR_LANGUAGE_NOT_SUPPORTED);
                default: break;
            }
            mSearchingAnimation.stop();
            mBinding.executePendingBindings();
        }, 100);
    }

    private void setPermissionNotGranted() {
        mBinding.setState(State.PERMISSIONS);
        mSearchingAnimation.stop();
        mBinding.executePendingBindings();
    }

    // ActivityLifeCycle

    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {

    }

    @Override
    public void onActivityStarted(Activity activity) {

    }

    @Override
    public void onActivityResumed(Activity activity) {
        if (mWasSpeechRecognitionRunning) {
            startVoiceSearch();
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        mWasSpeechRecognitionRunning = mIsSpeechRecognitionRunning;
        if (mIsSpeechRecognitionRunning) {
            stopVoiceSearch();
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {

    }
}
