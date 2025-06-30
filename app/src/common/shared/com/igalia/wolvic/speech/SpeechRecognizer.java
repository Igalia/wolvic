package com.igalia.wolvic.speech;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

public interface SpeechRecognizer {
    class Settings {
        public String locale;
        public boolean storeData;
        public String productTag;
    }

    interface Callback {
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(value = {SPEECH_ERROR, ERROR_NETWORK, ERROR_SERVER, ERROR_TOO_MANY_REQUESTS, ERROR_LANGUAGE_NOT_SUPPORTED})
        @interface ErrorType {}
        int SPEECH_ERROR = 0;
        int ERROR_NETWORK = 1;
        int ERROR_SERVER = 2;
        int ERROR_TOO_MANY_REQUESTS = 3;
        int ERROR_LANGUAGE_NOT_SUPPORTED = 4;



        void onStartListening();
        void onMicActivity(int level);
        void onDecoding();
        default void onPartialResult(String transcription) {};
        void onResult(String transcription, float confidence);
        void onNoVoice();
        void onCanceled();
        void onError(@ErrorType int errorType, @Nullable String error);
    }

    void start(@NonNull Settings settings, @NonNull Callback callback);
    void stop();
    boolean isActive();
    boolean shouldDisplayStoreDataPrompt();
    default boolean supportsASR(@NonNull Settings settings) {return true;}
    boolean isSpeechError(int code);
    List<String> getSupportedLanguages();
}
