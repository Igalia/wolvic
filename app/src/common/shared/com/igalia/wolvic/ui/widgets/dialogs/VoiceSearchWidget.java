package com.igalia.wolvic.ui.widgets.dialogs;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.VRBrowserApplication;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.VoiceSearchDialogBinding;
import com.igalia.wolvic.speech.SpeechRecognizer;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.utils.LocaleUtils;

public class VoiceSearchWidget extends UIDialog implements Application.ActivityLifecycleCallbacks {

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

    public interface VoiceSearchDelegate {
        default void OnVoiceSearchResult(String transcription, float confidence) {};
        default void OnPartialVoiceSearchResult(String transcription) {};
        default void OnVoiceSearchError(@SpeechRecognizer.Callback.ErrorType int errorType) {};
    }

    private VoiceSearchDialogBinding mBinding;
    private VoiceSearchDelegate mDelegate;
    private ClipDrawable mVoiceInputClipDrawable;
    private AnimatedVectorDrawable mSearchingAnimation;
    private VRBrowserApplication mApplication;
    private boolean mIsSpeechRecognitionRunning = false;
    private boolean mWasSpeechRecognitionRunning = false;
    private SpeechRecognizer mCurrentSpeechRecognizer;
    private int mVoiceStartString = R.string.voice_search_start;

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

        mSearchingAnimation = (AnimatedVectorDrawable) mBinding.voiceSearchAnimationSearching.getDrawable();
        mBinding.voiceSearchStart.setMovementMethod(new ScrollingMovementMethod());

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

        mBinding.closeButton.setOnClickListener(view -> onDismiss());
    }

    public void setVoiceStartString(int string) {
        mVoiceStartString = string;
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
        mApplication.unregisterActivityLifecycleCallbacks(this);

        if (mCurrentSpeechRecognizer != null) {
            mCurrentSpeechRecognizer.stop();
            mCurrentSpeechRecognizer = null;
        }

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
        updatePlacementTranslationZ();
    }

    @Override
    public void updatePlacementTranslationZ() {
        getPlacement().translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.tray_world_z) -
                WidgetPlacement.getWindowWorldZMeters(getContext());
    }

    public void setPlacement(int aHandle) {
        mWidgetPlacement.parentHandle = aHandle;
    }

    SpeechRecognizer.Callback mResultCallback = new SpeechRecognizer.Callback() {
        @Override
        public void onStartListening() {
            // Handle when the api successfully opened the microphone and started listening
            Log.d(LOGTAG, "===> START_LISTEN");
            mBinding.voiceSearchStart.setText( getContext().getString(mVoiceStartString));
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
            mBinding.voiceSearchStart.setText(getContext().getString(R.string.voice_search_error));
        }

        @Override
        public void onCanceled() {
            // Handle when the voice recognition was canceled
            Log.d(LOGTAG, "===> CANCELED");
        }

        @Override
        public void onError(@ErrorType int errorType, @Nullable String error) {
            Log.e(LOGTAG, "===> ERROR: " + error);
            setResultState(errorType);
            if (mDelegate != null) {
                mDelegate.OnVoiceSearchError(errorType);
            }
        }

    };

    private void ensurePermissionsAndStartVoiceSearch() {
        if (!mWidgetManager.isPermissionGranted(Manifest.permission.RECORD_AUDIO)) {
            mWidgetManager.requestPermission(getContext().getString(R.string.voice_search_tooltip),
                    Manifest.permission.RECORD_AUDIO,
                    WidgetManagerDelegate.OriginatorType.APPLICATION,
                    new WSession.PermissionDelegate.Callback() {

                        @NonNull
                        @Override
                        public String toString() {
                            return "voice permissions callback";
                        }

                        @Override
                        public void grant() {
                            startVoiceSearch();
                        }

                        @Override
                        public void reject() {
                            setPermissionNotGranted();
                            hide(KEEP_WIDGET);
                            stopVoiceSearch();
                        }
                    });
        } else {
            startVoiceSearch();
        }
    }

    private void startVoiceSearch() {
        // Ensure that the recognizer is in the correct state.
        if (mCurrentSpeechRecognizer != null && mCurrentSpeechRecognizer.isActive()) {
            Log.w(LOGTAG, "Voice recognition was already active.");
            stopVoiceSearch();
        }

        setStartListeningState();

        String locale = LocaleUtils.getVoiceSearchLanguageId(getContext());
        boolean storeData = SettingsStore.getInstance(getContext()).isSpeechDataCollectionEnabled();
        if (SessionStore.get().getActiveSession().isPrivateMode()) {
            storeData = false;
        }

        mIsSpeechRecognitionRunning = true;
        mCurrentSpeechRecognizer = mApplication.getSpeechRecognizer();

        SpeechRecognizer.Settings settings = new SpeechRecognizer.Settings();
        settings.locale = locale;
        settings.storeData = storeData;
        settings.productTag = getContext().getString(R.string.voice_app_id);

        mCurrentSpeechRecognizer.start(settings, mResultCallback);
    }

    public void stopVoiceSearch() {
        if (mCurrentSpeechRecognizer != null) {
            try {
                mCurrentSpeechRecognizer.stop();
            } catch (Exception e) {
                Log.w(LOGTAG, "Error stopping voice search: " + e);
            }
        }

        mIsSpeechRecognitionRunning = false;
        mCurrentSpeechRecognizer = null;
    }

    @Override
    public void show(@ShowFlags int aShowFlags) {
        if (mApplication.getSpeechRecognizer() == null) {
            Log.e(LOGTAG, "Speech recognizer not available");
            return;
        }

        if (!mApplication.getSpeechRecognizer().shouldDisplayStoreDataPrompt() ||
                SettingsStore.getInstance(getContext()).isSpeechDataCollectionEnabled() ||
                SettingsStore.getInstance(getContext()).isSpeechDataCollectionReviewed()) {
            mWidgetPlacement.parentHandle = mWidgetManager.getFocusedWindow().getHandle();
            super.show(aShowFlags);

            ensurePermissionsAndStartVoiceSearch();

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
        mBinding.voiceSearchStart.setText(getContext().getString(mVoiceStartString));
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
                case SpeechRecognizer.Callback.SPEECH_ERROR: mBinding.setState(State.SPEECH_ERROR); break;
                case SpeechRecognizer.Callback.ERROR_NETWORK: mBinding.setState(State.ERROR_NETWORK); break;
                case SpeechRecognizer.Callback.ERROR_SERVER: mBinding.setState(State.ERROR_SERVER); break;
                case SpeechRecognizer.Callback.ERROR_TOO_MANY_REQUESTS: mBinding.setState(State.ERROR_TOO_MANY_REQUESTS); break;
                case SpeechRecognizer.Callback.ERROR_LANGUAGE_NOT_SUPPORTED: mBinding.setState(State.ERROR_LANGUAGE_NOT_SUPPORTED); break;
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
            ensurePermissionsAndStartVoiceSearch();
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
