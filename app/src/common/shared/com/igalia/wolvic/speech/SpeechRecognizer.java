package com.igalia.wolvic.speech;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.IntDef;


import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.mozilla.geckoview.GeckoWebExecutor;

public interface SpeechRecognizer {
    class Settings {
        public String locale;
        public boolean storeData;
        public String productTag;
    }

    interface Callback {
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(value = { SPEECH_ERROR, MODEL_NOT_FOUND})
        @interface ErrorType {}
        int SPEECH_ERROR = 0;
        int MODEL_NOT_FOUND = 1;



        void onStartListening();
        void onMicActivity(int level);
        void onDecoding();
        default void onPartialResult(String transcription) {};
        void onResult(String transcription, float confidence);
        void onNoVoice();
        void onError(@ErrorType int errorType, @Nullable String error);
    }

    void start(@NonNull Settings settings, @Nullable GeckoWebExecutor executor, @NonNull Callback callback);
    void stop();
    boolean shouldDisplayStoreDataPrompt();
    boolean isSpeechError(int code);
}
