package com.igalia.wolvic.speech;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import com.huawei.hms.mlsdk.asr.MLAsrConstants;
import com.huawei.hms.mlsdk.asr.MLAsrListener;
import com.huawei.hms.mlsdk.asr.MLAsrRecognizer;
import com.igalia.wolvic.speech.SpeechRecognizer;
import com.igalia.wolvic.utils.LocaleUtils;
import com.igalia.wolvic.utils.SystemUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class HVRSpeechRecognizer implements SpeechRecognizer, MLAsrListener {
    private Context mContext;
    private MLAsrRecognizer mRecognizer;
    private Callback mCallback;

    protected final String LOGTAG = SystemUtils.createLogtag(this.getClass());

    // TODO Language support depends on the region and English is the only one supported everywhere.
    // https://developer.huawei.com/consumer/en/doc/development/hiai-Guides/ml-asr-0000001050066212
    private static final List<String> DEFAULT_SUPPORTED_LANGUAGES = Collections.singletonList(MLAsrConstants.LAN_EN_US);
    // The first element in these two lists corresponds to "Follow device language"
    private final List<String> mSupportedLanguages = new ArrayList<>();

    public HVRSpeechRecognizer(Context context) {
        mContext = context;

        setSupportedLanguages(DEFAULT_SUPPORTED_LANGUAGES);
        mRecognizer = MLAsrRecognizer.createAsrRecognizer(mContext);
        mRecognizer.getLanguages(new MLAsrRecognizer.LanguageCallback() {
            @Override
            public void onResult(List<String> list) {
                setSupportedLanguages(list);
            }

            @Override
            public void onError(int i, String s) {
                Log.e(LOGTAG, "Getting the list of supported languages failed: " + s);
            }
        });
    }

    private void setSupportedLanguages(List<String> supportedLanguages) {
        mSupportedLanguages.clear();
        mSupportedLanguages.add(LocaleUtils.DEFAULT_LANGUAGE_ID);
        mSupportedLanguages.addAll(supportedLanguages);
    }

    @Override
    public void start(@NonNull Settings settings, @NonNull Callback callback) {
        if (mRecognizer != null) {
            stop();
        }
        if (settings.locale.equals(LocaleUtils.DEFAULT_LANGUAGE_ID)) {
            settings.locale = LocaleUtils.getClosestLanguageForLocale(
                    Locale.getDefault(), mSupportedLanguages, LocaleUtils.FALLBACK_LANGUAGE_TAG);
        }
        mCallback = callback;
        mRecognizer = MLAsrRecognizer.createAsrRecognizer(mContext);
        mRecognizer.setAsrListener(this);
        Intent intent = new Intent(MLAsrConstants.ACTION_HMS_ASR_SPEECH);
        intent
                // Set the language that can be recognized to English. If this parameter is not set, English is recognized by default. Example: "zh-CN": Chinese; "en-US": English; "fr-FR": French; "es-ES": Spanish; "de-DE": German; "it-IT": Italian; "ar": Arabic; "ru-RU": Russian.
                .putExtra(MLAsrConstants.LANGUAGE, settings.locale)
                // Set to return the recognition result along with the speech. If you ignore the setting, this mode is used by default. Options are as follows:
                // MLAsrConstants.FEATURE_WORDFLUX: Recognizes and returns texts through onRecognizingResults.
                // MLAsrConstants.FEATURE_ALLINONE: After the recognition is complete, texts are returned through onResults.
                .putExtra(MLAsrConstants.FEATURE, MLAsrConstants.FEATURE_ALLINONE);
        // Start speech recognition.
        Log.w(LOGTAG, "Starting speech recognition, language = " + settings.locale);
        mRecognizer.startRecognizing(intent);
    }

    @Override
    public void stop() {
        if (mRecognizer != null) {
            mRecognizer.setAsrListener(null);
            mRecognizer.destroy();
            mRecognizer = null;
        }
        mCallback = null;
    }

    @Override
    public boolean isActive() {
        return mRecognizer != null;
    }

    @Override
    public boolean shouldDisplayStoreDataPrompt() {
        return false;
    }


    @Override
    public boolean isSpeechError(int code) {
        return true;
    }

    @Override
    public List<String> getSupportedLanguages() {
        return mSupportedLanguages;
    }

    private void dispatch(Runnable runnable) {
        ((Activity)mContext).runOnUiThread(runnable);
    }

    // MLAsrListener

    // Text data of ASR
    @Override
    public void onResults(Bundle bundle) {
        dispatch(() -> {
            if (mCallback == null) {
                return;
            }
            String text = "";
            if (bundle.containsKey(MLAsrRecognizer.RESULTS_RECOGNIZED)) {
                text = bundle.getString(MLAsrRecognizer.RESULTS_RECOGNIZED);
            }

            mCallback.onResult(text, 1.0f);
        });
    }

    // Receive the recognized text from MLAsrRecognizer
    @Override
    public void onRecognizingResults(Bundle partialResults) {

    }

    @Override
    public void onError(int code, String msg) {
        dispatch(() -> {
            if (mCallback != null) {
                switch (code) {
                    case MLAsrConstants.ERR_NO_NETWORK:
                        mCallback.onError(Callback.ERROR_NETWORK, msg);
                        break;
                    case MLAsrConstants.ERR_SERVICE_UNAVAILABLE:
                        mCallback.onError(Callback.ERROR_SERVER, msg);
                        break;
                    case MLAsrConstants.ERR_NO_UNDERSTAND:
                    default:
                        mCallback.onError(Callback.SPEECH_ERROR, msg);
                }
            }
        });
    }

    // The recorder starts to receive speech.
    @Override
    public void onStartListening() {
        dispatch(() -> {
            if (mCallback != null) {
                mCallback.onStartListening();
            }
        });
    }

    // The user starts to speak, that is, the speech recognizer detects that the user starts to speak.
    @Override
    public void onStartingOfSpeech() {
    }

    // Return the original PCM stream and audio power to the user
    @Override
    public void onVoiceDataReceived(byte[] bytes, float volume, Bundle bundle) {
        dispatch(() -> {
            if (mCallback != null) {
                mCallback.onMicActivity((int)volume);
            }
        });
    }

    // Notify the app status change
    @Override
    public void onState(int state, Bundle bundle) {
    }
}
