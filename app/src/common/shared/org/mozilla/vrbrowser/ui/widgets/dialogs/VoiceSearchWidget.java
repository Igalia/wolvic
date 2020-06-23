package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.app.DownloadManager;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.databinding.DataBindingUtil;

import com.mozilla.speechlibrary.SpeechResultCallback;
import com.mozilla.speechlibrary.SpeechService;
import com.mozilla.speechlibrary.SpeechServiceSettings;
import com.mozilla.speechlibrary.stt.STTResult;
import com.mozilla.speechlibrary.utils.ModelUtils;
import com.mozilla.speechlibrary.utils.zip.UnzipCallback;
import com.mozilla.speechlibrary.utils.zip.UnzipTask;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserActivity;
import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.engine.EngineProvider;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.VoiceSearchDialogBinding;
import org.mozilla.vrbrowser.downloads.Download;
import org.mozilla.vrbrowser.downloads.DownloadJob;
import org.mozilla.vrbrowser.downloads.DownloadsManager;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.ui.widgets.Windows;
import org.mozilla.vrbrowser.utils.DeviceType;
import org.mozilla.vrbrowser.utils.LocaleUtils;
import org.mozilla.vrbrowser.utils.ViewUtils;

import java.io.File;

public class VoiceSearchWidget extends UIDialog implements
        WidgetManagerDelegate.PermissionListener,
        Application.ActivityLifecycleCallbacks,
        DownloadsManager.DownloadsListener,
        UnzipCallback {

    public enum State {
        LISTENING,
        SEARCHING,
        SPEECH_ERROR,
        MODEL_NOT_FOUND,
        PERMISSIONS
    }

    private static final int VOICE_SEARCH_AUDIO_REQUEST_CODE = 7455;

    private static final @SettingsStore.Storage int MODELS_STORAGE = SettingsStore.INTERNAL;

    private static int MAX_CLIPPING = 10000;
    private static int MAX_DB = 130;
    private static int MIN_DB = 50;

    public interface VoiceSearchDelegate {
        default void OnVoiceSearchResult(String transcription, float confidence) {};
        default void OnVoiceSearchError() {};
    }

    private VoiceSearchDialogBinding mBinding;
    private SpeechService mMozillaSpeechService;
    private VoiceSearchDelegate mDelegate;
    private ClipDrawable mVoiceInputClipDrawable;
    private AnimatedVectorDrawable mSearchingAnimation;
    private VRBrowserApplication mApplication;
    private DownloadsManager mDownloadsManager;
    private UnzipTask mUnzip;

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

        mUnzip = new UnzipTask(getContext());
        mUnzip.addListener(this);

        mDownloadsManager = mApplication.getDownloadsManager();
        mDownloadsManager.addListener(this);

        mMozillaSpeechService = mApplication.getSpeechService();

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

        mUnzip.removeListener(this);
        mDownloadsManager.removeListener(this);

        mMozillaSpeechService.stop();

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

    SpeechResultCallback mResultCallback = new SpeechResultCallback() {
        @Override
        public void onStartListen() {
            // Handle when the api successfully opened the microphone and started listening
            Log.d(LOGTAG, "===> START_LISTEN");
        }

        @Override
        public void onMicActivity(double fftsum) {
            // Captures the activity from the microphone
            Log.d(LOGTAG, "===> MIC_ACTIVITY");
            double db = (double)fftsum * -1; // the higher the value, quieter the user/environment is
            db = db == Double.POSITIVE_INFINITY ? MAX_DB : db;
            int level = (int)(MAX_CLIPPING - (((db - MIN_DB) / (MAX_DB - MIN_DB)) * MAX_CLIPPING));
            Log.d(LOGTAG, "===> db:      " + db);
            Log.d(LOGTAG, "===> level    " + level);
            mVoiceInputClipDrawable.setLevel(level);
        }

        @Override
        public void onDecoding() {
            // Handle when the speech object changes to decoding state
            Log.d(LOGTAG, "===> DECODING");
            setDecodingState();
        }

        @Override
        public void onSTTResult(@Nullable STTResult result) {
            // When the api finished processing and returned a hypothesis
            Log.d(LOGTAG, "===> STT_RESULT");
            String transcription = result.mTranscription;
            float confidence = result.mConfidence;
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
                mDelegate.OnVoiceSearchError();
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

            boolean useDeepSpeech = SettingsStore.getInstance(getContext()).isDeepSpeechEnabled();
            SpeechServiceSettings.Builder builder = new SpeechServiceSettings.Builder()
                    .withLanguage(locale)
                    .withStoreSamples(storeData)
                    .withStoreTranscriptions(storeData)
                    .withProductTag(getContext().getString(R.string.voice_app_id))
                    .withUseDeepSpeech(useDeepSpeech);
            if (useDeepSpeech) {
                handleDeepSpeechModel(builder, locale);

            } else {
                mMozillaSpeechService.start(builder.build(),
                        EngineProvider.INSTANCE.getDefaultGeckoWebExecutor(getContext()),
                        mResultCallback);
            }
        }
    }

    private void handleDeepSpeechModel(@NonNull SpeechServiceSettings.Builder builder, @NonNull String language) {
        String modelPath = ModelUtils.modelPath(getContext(), language);

        if (ModelUtils.isReady(modelPath)) {
            // The model is already downloaded and unzipped
            builder.withModelPath(modelPath);
            mMozillaSpeechService.start(builder.build(),
                    EngineProvider.INSTANCE.getDefaultGeckoWebExecutor(getContext()),
                    mResultCallback);

        } else {
            hide(KEEP_WIDGET);
            String zipPath = ModelUtils.modelDownloadOutputPath(
                    getContext(),
                    language,
                    MODELS_STORAGE);
            if (new File(zipPath).exists()) {
                if (mUnzip.isIsRunning()) {
                    mWidgetManager.getFocusedWindow().showAlert(
                            "Voice search",
                            language + " model unzipping still in progress",
                            null
                    );

                } else {
                    // Model download is ready, start unzipping
                    mWidgetManager.getFocusedWindow().showAlert(
                            "Voice search",
                            "Start " + language + " model unzip",
                            (index, isChecked) -> mUnzip.start(zipPath)
                    );
                }

            } else {
                // The model needs to be downloaded
                handleModelDownload(language);
            }
        }
    }

    private void handleModelDownload(@NonNull String language) {
        String modelUrl = ModelUtils.modelDownloadUrl(language);

        // Check if the model is already downloaded
        Download download = mDownloadsManager.getDownloads().stream()
                .filter(item ->
                        item.getStatus() == DownloadManager.STATUS_SUCCESSFUL &&
                                item.getUri().equals(modelUrl))
                .findFirst().orElse(null);
        if (download != null) {
            onDownloadCompleted(download);

        } else {
            // Check if the model is in progress
            boolean isInProgress = mDownloadsManager.getDownloads().stream()
                    .anyMatch(item ->
                            item.getStatus() != DownloadManager.STATUS_FAILED &&
                                    item.getUri().equals(modelUrl));
            if (!isInProgress) {
                // Download model
                DownloadJob job = DownloadJob.create(
                        ModelUtils.modelDownloadUrl(language),
                        "application/zip",
                        0,
                        null);
                mDownloadsManager.startDownload(job, MODELS_STORAGE);
                mWidgetManager.getFocusedWindow().showAlert(
                        "Voice search",
                        language + " model download started",
                        (index, isChecked) -> mWidgetManager.getFocusedWindow().showPanel(Windows.PanelType.DOWNLOADS)
                );

            } else {
                // Model download is already in progress
                mWidgetManager.getFocusedWindow().showAlert(
                        "Voice search",
                        language + " model download in progress",
                        null
                );
            }
        }
    }

    public void stopVoiceSearch() {
        try {
            mMozillaSpeechService.stop();

        } catch (Exception e) {
            Log.d(LOGTAG, e.getLocalizedMessage());
            e.printStackTrace();
        }
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
        if (SettingsStore.getInstance(getContext()).isSpeechDataCollectionEnabled() ||
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

    private void setResultState(@SpeechResultCallback.ErrorType int errorType) {
        stopVoiceSearch();

        postDelayed(() -> {
            if (errorType == SpeechResultCallback.SPEECH_ERROR) {
                mBinding.setState(State.SPEECH_ERROR);
                startVoiceSearch();

            } else {
                mBinding.setState(State.MODEL_NOT_FOUND);
                handleModelDownload(LocaleUtils.getVoiceSearchLanguageTag(getContext()));
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
        startVoiceSearch();
    }

    @Override
    public void onActivityPaused(Activity activity) {
        stopVoiceSearch();
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

    // DownloadsListener

    @Override
    public void onDownloadCompleted(@NonNull Download download) {
        if (ModelUtils.isModelUri(download.getUri())) {
            File file = new File(download.getOutputFilePath());
            if (file.exists()) {
                String language = ModelUtils.languageForUri(download.getUri());
                mWidgetManager.getFocusedWindow().showAlert(
                        "Voice search",
                        "Start " + language + " model unzip",
                        (index, isChecked) -> mUnzip.start(file.getAbsolutePath())
                );

            } else {
                mDownloadsManager.removeDownload(download.getId(), true);
            }
        }
    }

    // UnzipCallback

    @Override
    public void onUnzipStart(@NonNull String zipFile) {

    }

    @Override
    public void onUnzipProgress(@NonNull String zipFile, double progress) {

    }

    @Override
    public void onUnzipFinish(@NonNull String zipFile, @NonNull String outputPath) {
        File file = new File(zipFile);
        if (file.exists()) {
            file.delete();
        }

        mWidgetManager.getFocusedWindow().showAlert(
                "Voice search",
                "Model successfully unzipped.",
                null
        );
    }

    @Override
    public void onUnzipCancelled(@NonNull String zipFile) {

    }

    @Override
    public void onUnzipError(@NonNull String zipFile, @Nullable String error) {

    }

}
