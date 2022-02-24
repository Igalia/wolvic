package com.igalia.wolvic.speech;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.BuildConfig;
import com.igalia.wolvic.utils.StringUtils;
import com.meetkai.speechlibrary.ISpeechRecognitionListener;
import com.meetkai.speechlibrary.MKSpeechService;
import com.meetkai.speechlibrary.STTResult;

import org.mozilla.geckoview.GeckoWebExecutor;

import com.igalia.wolvic.ui.widgets.dialogs.VoiceSearchWidget;
import com.igalia.wolvic.utils.SystemUtils;

public class MKSpeechRecognizer implements SpeechRecognizer, ISpeechRecognitionListener {

    private Context mContext;


    protected final String LOGTAG = SystemUtils.createLogtag(this.getClass());
    private MKSpeechService mkSpeechService;
    private @Nullable
    SpeechRecognizer.Callback mCallback;
    private static int MAX_CLIPPING = 10000;
    private static int MAX_DB = 130;
    private static int MIN_DB = 50;
    // temporary
    private static float FINAL_CONF = 1.0f;


    private static final String DEBUG_API_KEY = "WOLVIC_DEBUG";

    public MKSpeechRecognizer(Context context) {
        mContext = context;
    }

    @Override
    public void start(@NonNull Settings settings, @Nullable GeckoWebExecutor executor, @NonNull Callback callback) {
        mkSpeechService = MKSpeechService.getInstance();
        mCallback = callback;
        mkSpeechService.addListener(this);
        String key = BuildConfig.MK_API_KEY;
        if (StringUtils.isEmpty(key)) {
            key = DEBUG_API_KEY;
        }
        if (!supportsASR(settings)) {
            callback.onError(Callback.ERROR_LANGUAGE_NOT_SUPPORTED, "language not supported");
            stop();
        } else {
            mkSpeechService.start(mContext, settings.locale, key);
        }
    }

    @Override
    public void stop() {
        mCallback = null;
        mkSpeechService.cancel();
        removeListener();
    }

    @Override
    public boolean shouldDisplayStoreDataPrompt() {
        return false;
    }

    @Override
    public boolean isSpeechError(int code) {
        return code == VoiceSearchWidget.State.SPEECH_ERROR.ordinal();
    }

    // SpeechResultCallback
    public void onStartListen() {
        if (mCallback != null) {
            mCallback.onStartListening();
        }
    }

    public void onMicActivity(double fftsum) {
        if (mCallback != null) {
            double db = (double) fftsum * -1; // the higher the value, quieter the user/environment is
            db = db == Double.POSITIVE_INFINITY ? MAX_DB : db;
            int level = (int) (MAX_CLIPPING - (((db - MIN_DB) / (MAX_DB - MIN_DB)) * MAX_CLIPPING));
            Log.d(LOGTAG, "===> db:      " + db);
            Log.d(LOGTAG, "===> level    " + level);
            mCallback.onMicActivity(level);
        }
    }


    private void dispatch(Runnable runnable) {
        ((Activity) mContext).runOnUiThread(runnable);
    }

    public void removeListener() {
        mkSpeechService.removeListener(this);
    }

    @Override
    public boolean supportsASR(@NonNull Settings settings) {
        return mkSpeechService.supportsLocale(settings.locale);
    }

    @Override
    public void onSpeechStatusChanged(MKSpeechService.SpeechState aState, Object aPayload) {
        dispatch(() -> {

            switch (aState) {
                case MIC_ACTIVITY:
                    this.onMicActivity((double) aPayload);
                    break;
                case INTERIM_STT_RESULT:
                    if (mCallback != null) {
                        Log.w(LOGTAG, "INTERIM_STT_RESULT " + (String) aPayload);
                        mCallback.onPartialResult((String) aPayload);
                    }
                    break;
                case STT_RESULT:
                    if (mCallback != null) {
                        STTResult result = (STTResult) aPayload;
                        Log.w(LOGTAG, "STT_RESULT " + result.transcript);
                        mCallback.onResult(result.transcript, FINAL_CONF);
                    }
                    removeListener();
                    break;
                case START_LISTEN:
                    onStartListen();
                    break;
                case NO_VOICE:
                    if (mCallback != null) {
                        mCallback.onNoVoice();
                    }
                    removeListener();
                    break;
                case CANCELED:
                    if (mCallback != null) {
                        mCallback.onNoVoice();
                    }
                    removeListener();
                    break;
                case ERROR:
                    if (mCallback != null) {
                        mCallback.onError(SpeechRecognizer.Callback.SPEECH_ERROR, "unk error");
                    }
                    removeListener();
                    break;
                default:
                    break;
            }
        });
    }
}
