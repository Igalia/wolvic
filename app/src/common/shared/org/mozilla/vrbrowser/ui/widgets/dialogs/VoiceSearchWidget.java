package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;

import androidx.core.app.ActivityCompat;
import androidx.databinding.DataBindingUtil;

import com.mozilla.speechlibrary.ISpeechRecognitionListener;
import com.mozilla.speechlibrary.MozillaSpeechService;
import com.mozilla.speechlibrary.STTResult;

import net.lingala.zip4j.core.ZipFile;

import org.mozilla.gecko.util.ThreadUtils;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.VoiceSearchDialogBinding;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.LocaleUtils;

import java.io.File;

public class VoiceSearchWidget extends UIDialog implements WidgetManagerDelegate.PermissionListener,
        Application.ActivityLifecycleCallbacks {

    public enum State {
        LISTENING,
        SEARCHING,
        ERROR,
        PERMISSIONS
    }

    private static final int VOICE_SEARCH_AUDIO_REQUEST_CODE = 7455;
    private static final int ANIMATION_DURATION = 1000;

    private static int MAX_CLIPPING = 10000;
    private static int MAX_DB = 130;
    private static int MIN_DB = 50;

    public interface VoiceSearchDelegate {
        default void OnVoiceSearchResult(String transcription, float confidance) {};
        default void OnVoiceSearchCanceled() {};
        default void OnVoiceSearchError() {};
    }

    private VoiceSearchDialogBinding mBinding;
    private MozillaSpeechService mMozillaSpeechService;
    private VoiceSearchDelegate mDelegate;
    private ClipDrawable mVoiceInputClipDrawable;
    private RotateAnimation mSearchingAnimation;
    private boolean mIsSpeechRecognitionRunning = false;
    private boolean mWasSpeechRecognitionRunning = false;
    private AudioEngine mAudio;
    private String mModelPath;

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
        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.voice_search_dialog, this, true);

        mModelPath = getContext().getExternalFilesDir("models").getAbsolutePath();

        mWidgetManager.addPermissionListener(this);

        mMozillaSpeechService = MozillaSpeechService.getInstance();
        mMozillaSpeechService.setProductTag(getContext().getString(R.string.voice_app_id));

        Drawable mVoiceInputBackgroundDrawable = getResources().getDrawable(R.drawable.ic_voice_search_volume_input_black, getContext().getTheme());
        mVoiceInputClipDrawable = new ClipDrawable(getContext().getDrawable(R.drawable.ic_voice_search_volume_input_clip), Gravity.START, ClipDrawable.HORIZONTAL);
        Drawable[] layers = new Drawable[] {mVoiceInputBackgroundDrawable, mVoiceInputClipDrawable };
        mBinding.voiceSearchAnimationListening.setImageDrawable(new LayerDrawable(layers));
        mVoiceInputClipDrawable.setLevel(0);

        mSearchingAnimation = new RotateAnimation(0, 360f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);

        mSearchingAnimation.setInterpolator(new LinearInterpolator());
        mSearchingAnimation.setDuration(ANIMATION_DURATION);
        mSearchingAnimation.setRepeatCount(Animation.INFINITE);

        mBinding.closeButton.setOnClickListener(view -> onDismiss());

        mMozillaSpeechService.addListener(mVoiceSearchListener);
        ((Application)aContext.getApplicationContext()).registerActivityLifecycleCallbacks(this);
    }

    public void setDelegate(VoiceSearchDelegate delegate) {
        mDelegate = delegate;
    }

    @Override
    public void releaseWidget() {
        mWidgetManager.removePermissionListener(this);
        mMozillaSpeechService.removeListener(mVoiceSearchListener);
        ((Application)getContext().getApplicationContext()).unregisterActivityLifecycleCallbacks(this);

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

    private ISpeechRecognitionListener mVoiceSearchListener = new ISpeechRecognitionListener() {

        public void onSpeechStatusChanged(final MozillaSpeechService.SpeechState aState, final Object aPayload){
            if (!mIsSpeechRecognitionRunning) {
                return;
            }
            ((Activity)getContext()).runOnUiThread(() -> {
                switch (aState) {
                    case DECODING:
                        // Handle when the speech object changes to decoding state
                        Log.d(LOGTAG, "===> DECODING");
                        setDecodingState();
                        break;
                    case MIC_ACTIVITY:
                        // Captures the activity from the microphone
                        Log.d(LOGTAG, "===> MIC_ACTIVITY");
                        double db = (double)aPayload * -1; // the higher the value, quieter the user/environment is
                        db = db == Double.POSITIVE_INFINITY ? MAX_DB : db;
                        int level = (int)(MAX_CLIPPING - (((db - MIN_DB) / (MAX_DB - MIN_DB)) * MAX_CLIPPING));
                        Log.d(LOGTAG, "===> db:      " + db);
                        Log.d(LOGTAG, "===> level    " + level);
                        mVoiceInputClipDrawable.setLevel(level);
                        break;
                    case STT_RESULT:
                        // When the api finished processing and returned a hypothesis
                        Log.d(LOGTAG, "===> STT_RESULT");
                        String transcription = ((STTResult)aPayload).mTranscription;
                        float confidence = ((STTResult)aPayload).mConfidence;
                        if (mDelegate != null) {
                            mDelegate.OnVoiceSearchResult(transcription, confidence);
                        }
                        hide(REMOVE_WIDGET);
                        break;
                    case START_LISTEN:
                        // Handle when the api successfully opened the microphone and started listening
                        Log.d(LOGTAG, "===> START_LISTEN");
                        break;
                    case NO_VOICE:
                        // Handle when the api didn't detect any voice
                        Log.d(LOGTAG, "===> NO_VOICE");
                        setResultState();
                        break;
                    case CANCELED:
                        // Handle when a cancellation was fully executed
                        Log.d(LOGTAG, "===> CANCELED");
                        if (mDelegate != null) {
                            mDelegate.OnVoiceSearchCanceled();
                        }
                        break;
                    case ERROR:
                        Log.d(LOGTAG, "===> ERROR: " + aPayload.toString());
                        setResultState();
                        // Handle when any error occurred
                        if (mDelegate != null) {
                            mDelegate.OnVoiceSearchError();
                        }
                        break;
                    default:
                        break;
                }
            });
        }
    };

    public void startVoiceSearch() {
        if (ActivityCompat.checkSelfPermission(getContext().getApplicationContext(), Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity)getContext(), new String[]{Manifest.permission.RECORD_AUDIO},
                    VOICE_SEARCH_AUDIO_REQUEST_CODE);
        } else {
            String locale = LocaleUtils.getVoiceSearchLocale(getContext());
            mMozillaSpeechService.setLanguage(LocaleUtils.mapToMozillaSpeechLocales(locale));
            boolean storeData = SettingsStore.getInstance(getContext()).isSpeechDataCollectionEnabled();
            if (SessionStore.get().getActiveSession().isPrivateMode()) {
                storeData = false;
            }
            mMozillaSpeechService.storeSamples(storeData);
            mMozillaSpeechService.storeTranscriptions(storeData);
            mMozillaSpeechService.setModelPath(mModelPath);
            mMozillaSpeechService.useDeepSpeech(true);
            if (mMozillaSpeechService.ensureModelInstalled()) {
                mMozillaSpeechService.start(getContext().getApplicationContext());
                mIsSpeechRecognitionRunning = true;

            } else if (mDownloadId == 0){
                maybeDownloadOrExtractModel(mModelPath, mMozillaSpeechService.getLanguageDir());
                onDismiss();

            } else {
                mWidgetManager.getFocusedWindow().showAlert("Deep Speech", "Model not ready yet", null);
            }
        }
    }

    public void stopVoiceSearch() {
        try {
            mMozillaSpeechService.cancel();
            mIsSpeechRecognitionRunning = false;

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
                    index -> {
                        SettingsStore.getInstance(getContext()).setSpeechDataCollectionReviewed(true);
                        if (index == PromptDialogWidget.POSITIVE) {
                            SettingsStore.getInstance(getContext()).setSpeechDataCollectionEnabled(true);
                        }
                        ThreadUtils.postToUiThread(() -> show(aShowFlags));
                    },
                    () -> {
                        mWidgetManager.openNewTabForeground(getResources().getString(R.string.private_policy_url));
                    });
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
        mBinding.voiceSearchAnimationSearching.clearAnimation();
        mBinding.executePendingBindings();
    }

    private void setDecodingState() {
        mBinding.setState(State.SEARCHING);
        mBinding.voiceSearchAnimationSearching.startAnimation(mSearchingAnimation);
        mBinding.executePendingBindings();
    }

    private void setResultState() {
        stopVoiceSearch();

        postDelayed(() -> {
            mBinding.setState(State.ERROR);
            mBinding.voiceSearchAnimationSearching.clearAnimation();
            mBinding.executePendingBindings();

            startVoiceSearch();
        }, 100);
    }

    private void setPermissionNotGranted() {
        mBinding.setState(State.PERMISSIONS);
        mBinding.voiceSearchAnimationSearching.clearAnimation();
        mBinding.executePendingBindings();
    }

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

    private static long mDownloadId = 0;
    private static DownloadManager sDownloadManager = null;
    public void maybeDownloadOrExtractModel(String aModelsPath, String aLang) {
        String zipFile   = aModelsPath + "/" + aLang + ".zip";
        String aModelPath= aModelsPath + "/" + aLang + "/";

        File aModelFolder = new File(aModelPath);
        if (!aModelFolder.exists()) {
            aModelFolder.mkdirs();
        }

        Uri modelZipURL  = Uri.parse(mMozillaSpeechService.getModelDownloadURL());
        Uri modelZipFile = Uri.parse("file://" + zipFile);

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                    long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(downloadId);
                    Cursor c = sDownloadManager.query(query);
                    if (c.moveToFirst()) {
                        int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex)) {
                            Log.d(LOGTAG, "Model download finished");
                            mWidgetManager.getTray().showNotification("Download finished");
                            mWidgetManager.getFocusedWindow().showAlert("Deep Speech", "Model download finished", null);

                            new AsyncUnzip(new AsyncUnzip.Delegate() {
                                @Override
                                public void onDownloadStarted() {
                                    Log.d(LOGTAG, "Model unzipping started");
                                    mWidgetManager.getTray().showNotification("Model unzipping started");
                                    mWidgetManager.getFocusedWindow().showAlert("Deep Speech", "Model unzipping started", null);
                                }

                                @Override
                                public void onDownloadFinished() {
                                    Log.d(LOGTAG, "Model unzipping finished");
                                    mWidgetManager.getTray().showNotification("Model unzipping finished");
                                    mWidgetManager.getFocusedWindow().showAlert("Deep Speech", "Model unzipping finished", null);
                                }

                                @Override
                                public void onDownloadError(String error) {
                                    Log.d(LOGTAG, "Model unzipping error: " + error);
                                    mWidgetManager.getTray().showNotification("Model unzipping error");
                                    mWidgetManager.getFocusedWindow().showAlert("Deep Speech", "Model unzipping error: " + error, null);
                                }
                            }).execute(zipFile, aModelPath);
                        }
                    }
                }
            }
        };

        sDownloadManager = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(modelZipURL);
        request.setTitle("DeepSpeech " + aLang);
        request.setDescription("DeepSpeech Model");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setVisibleInDownloadsUi(false);
        request.setDestinationUri(modelZipFile);
        mDownloadId = sDownloadManager.enqueue(request);

        Log.d(LOGTAG, "Model download started");
        mWidgetManager.getTray().showNotification("Model download started");
        mWidgetManager.getFocusedWindow().showAlert("Deep Speech", "Model download started", null);

        getContext().getApplicationContext().registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    private static class AsyncUnzip extends AsyncTask<String, Void, Boolean> {

        public interface Delegate {
            void onDownloadStarted();
            void onDownloadFinished();
            void onDownloadError(String error);
        }

        private Delegate mDelegate;

        public AsyncUnzip(Delegate delegate) {
            mDelegate = delegate;
        }

        @Override
        protected void onPreExecute() {
            if (mDelegate != null) {
                mDelegate.onDownloadStarted();
            }
        }

        @Override
        protected Boolean doInBackground(String...params) {
            String aZipFile = params[0], aRootModelsPath = params[1];
            try {
                ZipFile zf = new ZipFile(aZipFile);
                zf.extractAll(aRootModelsPath);

            } catch (Exception e) {
                e.printStackTrace();
                if (mDelegate != null) {
                    mDelegate.onDownloadError(e.getMessage());
                }
            }

            return (new File(aZipFile)).delete();
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (mDelegate != null) {
                mDelegate.onDownloadFinished();
            }
        }

    }
}
