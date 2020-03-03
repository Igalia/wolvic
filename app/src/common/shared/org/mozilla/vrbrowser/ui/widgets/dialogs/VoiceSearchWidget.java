package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
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

import org.mozilla.gecko.util.ThreadUtils;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.engine.EngineProvider;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.VoiceSearchDialogBinding;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.LocaleUtils;

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
        updateUI();

        mWidgetManager.addPermissionListener(this);

        mMozillaSpeechService = MozillaSpeechService.getInstance();
        mMozillaSpeechService.setGeckoWebExecutor(EngineProvider.INSTANCE.createGeckoWebExecutor(getContext()));
        mMozillaSpeechService.setProductTag(getContext().getString(R.string.voice_app_id));

        mSearchingAnimation = new RotateAnimation(0, 360f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);

        mSearchingAnimation.setInterpolator(new LinearInterpolator());
        mSearchingAnimation.setDuration(ANIMATION_DURATION);
        mSearchingAnimation.setRepeatCount(Animation.INFINITE);

        mMozillaSpeechService.addListener(mVoiceSearchListener);
        ((Application)aContext.getApplicationContext()).registerActivityLifecycleCallbacks(this);
    }

    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.voice_search_dialog, this, true);

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
                        hide(KEEP_WIDGET);
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
            String locale = LocaleUtils.getVoiceSearchLanguageTag(getContext());
            mMozillaSpeechService.setLanguage(LocaleUtils.mapToMozillaSpeechLocales(locale));
            boolean storeData = SettingsStore.getInstance(getContext()).isSpeechDataCollectionEnabled();
            if (SessionStore.get().getActiveSession().isPrivateMode()) {
                storeData = false;
            }
            mMozillaSpeechService.storeSamples(storeData);
            mMozillaSpeechService.storeTranscriptions(storeData);
            mMozillaSpeechService.start(getContext().getApplicationContext());
            mIsSpeechRecognitionRunning = true;
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
}
