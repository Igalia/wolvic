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
        @IntDef(value = {SPEECH_ERROR, ERROR_NETWORK, ERROR_SERVER, ERROR_TOO_MANY_REQUESTS, ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_AUDIO_PERMISSION, ERROR_MODEL_NOT_DOWNLOADED})
        @interface ErrorType {}
        int SPEECH_ERROR = 0;
        int ERROR_NETWORK = 1;
        int ERROR_SERVER = 2;
        int ERROR_TOO_MANY_REQUESTS = 3;
        int ERROR_LANGUAGE_NOT_SUPPORTED = 4;
        int ERROR_AUDIO_PERMISSION = 5;
        int ERROR_MODEL_NOT_DOWNLOADED = 6;



        void onStartListening();
        void onMicActivity(int level);
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
    List<String> getSupportedLanguages();
    default boolean isModelDownloaded(@Nullable String lang) {return true;}

    interface DownloadCallback {
        void onProgress(int progress);
        void onSuccess();
        void onError(@NonNull String error);
    }

    default void downloadModel(@Nullable String lang, @NonNull DownloadCallback callback) {
        callback.onSuccess();
    }

    default void cancelDownload() {}
}
