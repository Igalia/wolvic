package com.igalia.wolvic;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.huawei.hms.mlsdk.asr.MLAsrConstants;
import com.huawei.hms.mlsdk.asr.MLAsrListener;
import com.huawei.hms.mlsdk.asr.MLAsrRecognizer;

import org.mozilla.geckoview.GeckoWebExecutor;
import com.igalia.wolvic.speech.SpeechRecognizer;

public class HVRSpeechRecognizer implements SpeechRecognizer, MLAsrListener {
    private Context mContext;
    private MLAsrRecognizer mRecognizer;
    private Callback mCallback;

    public HVRSpeechRecognizer(Context context) {
        mContext = context;
    }

    @Override
    public void start(@NonNull Settings settings, @Nullable GeckoWebExecutor executor, @NonNull Callback callback) {
        if (mRecognizer != null) {
            stop();
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
        mRecognizer.startRecognizing(intent);
    }

    @Override
    public void stop() {
        if (mRecognizer != null) {
            mRecognizer.setAsrListener(null);
            mRecognizer.destroy();
        }
        mCallback = null;
    }

    @Override
    public boolean shouldDisplayStoreDataPrompt() {
        return false;
    }


    @Override
    public boolean isSpeechError(int code) {
        return true;
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
                mCallback.onError(Callback.SPEECH_ERROR, msg);
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
