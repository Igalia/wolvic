package org.mozilla.vrbrowser.speech;

import android.telecom.Call;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mozilla.speechlibrary.SpeechResultCallback;

import org.mozilla.geckoview.GeckoWebExecutor;

public interface SpeechRecognizer {
    class Settings {
        public String locale;
        public boolean storeData;
        public String productTag;
    }

    interface Callback {
        void onStartListening();
        void onMicActivity(int level);
        void onDecoding();
        void onResult(String transcription, float confidence);
        void onNoVoice();
        void onError(@SpeechResultCallback.ErrorType int errorType, @Nullable String error);
    }

    void start(@NonNull Settings settings, @Nullable GeckoWebExecutor executor, @NonNull Callback callback);
    void stop();
    boolean shouldDisplayStoreDataPrompt();
    boolean isSpeechError(int code);
}
