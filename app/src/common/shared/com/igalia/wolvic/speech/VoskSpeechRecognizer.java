package com.igalia.wolvic.speech;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.igalia.wolvic.utils.SystemUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.LibVosk;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class VoskSpeechRecognizer implements SpeechRecognizer {

    private static final String TAG = SystemUtils.createLogtag(VoskSpeechRecognizer.class);

    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int SILENCE_THRESHOLD = 100;
    private static final int MIN_SILENCE_FRAMES = 30;

    private Context mContext;
    private VoskModelManager mModelManager;
    private Callback mCallback;
    private Settings mSettings;
    private final Handler mMainHandler;
    private final ExecutorService mExecutor;
    private volatile boolean mIsActive = false;

    private Model mModel;
    private Recognizer mRecognizer;
    private AudioRecord mAudioRecord;
    private Thread mRecognitionThread;
    private final AtomicBoolean mStopRecognition = new AtomicBoolean(false);

    private int mSilenceFrameCount = 0;
    private boolean mSpeechStarted = false;

    public VoskSpeechRecognizer(@NonNull Context context) {
        mContext = context.getApplicationContext();
        mModelManager = new VoskModelManager(mContext);
        mMainHandler = new Handler(Looper.getMainLooper());
        mExecutor = Executors.newSingleThreadExecutor();

        try {
            LibVosk.setLogLevel(org.vosk.LogLevel.INFO);
        } catch (Exception e) {
            Log.w(TAG, "Failed to set Vosk log level", e);
        }
    }

    @Override
    public void start(@NonNull Settings settings, @NonNull Callback callback) {
        if (mIsActive) {
            Log.w(TAG, "Recognition already active");
            mMainHandler.post(() ->
                callback.onError(Callback.ERROR_TOO_MANY_REQUESTS, "Voice recognition already in progress."));
            return;
        }

        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            mMainHandler.post(() ->
                callback.onError(Callback.ERROR_AUDIO_PERMISSION, "RECORD_AUDIO permission not granted"));
            return;
        }

        List<String> availableLangs = mModelManager.getAvailableLanguages();
        String normalizedLang = settings.locale != null ? settings.locale.split("-")[0].toLowerCase() : "en";
        if (!availableLangs.contains(normalizedLang)) {
            String closestLang = findClosestSupportedLanguage(settings.locale);
            settings.locale = closestLang;
        }

        mIsActive = true;
        mCallback = callback;
        mSettings = settings;
        mSilenceFrameCount = 0;
        mSpeechStarted = false;
        mStopRecognition.set(false);

        mExecutor.execute(() -> {
            if (!mModelManager.isModelDownloaded(settings.locale)) {
                mMainHandler.post(() -> {
                    mIsActive = false;
                    callback.onError(Callback.ERROR_MODEL_NOT_DOWNLOADED, "Model not downloaded for language: " + settings.locale);
                });
                return;
            }

            try {
                String modelPath = mModelManager.getModelDir(settings.locale);
                File modelDir = new File(modelPath);

                if (!modelDir.exists() || !modelDir.isDirectory()) {
                    mMainHandler.post(() -> {
                        mIsActive = false;
                        callback.onError(Callback.ERROR_MODEL_NOT_DOWNLOADED, "Model directory not found");
                    });
                    return;
                }

                mModel = new Model(modelPath);
                mRecognizer = new Recognizer(mModel, (float) SAMPLE_RATE);

                int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
                if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                    bufferSize = SAMPLE_RATE * 2;
                }
                bufferSize = Math.max(bufferSize, SAMPLE_RATE);

                mAudioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        bufferSize * 2
                );

                if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    throw new IOException("AudioRecord initialization failed");
                }

                Log.d(TAG, "Starting recognition loop");
                mMainHandler.post(() -> {
                    if (mCallback != null) {
                        Log.d(TAG, "Calling onStartListening");
                        mCallback.onStartListening();
                    }
                });

                mRecognitionThread = new Thread(this::recognitionLoop);
                mRecognitionThread.start();

            } catch (UnsatisfiedLinkError e) {
                Log.e(TAG, "Native library error", e);
                mMainHandler.post(() -> {
                    mIsActive = false;
                    if (mCallback != null) {
                        mCallback.onError(Callback.SPEECH_ERROR, "Failed to load speech recognition");
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to start recognition", e);
                mMainHandler.post(() -> {
                    mIsActive = false;
                    if (mCallback != null) {
                        mCallback.onError(Callback.SPEECH_ERROR, e.getMessage() != null ? e.getMessage() : "Failed to start recognition");
                    }
                });
            }
        });
    }

    private void recognitionLoop() {
        short[] buffer = new short[SAMPLE_RATE / 10];

        mAudioRecord.startRecording();

        try {
            while (!mStopRecognition.get() && mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                int nbytes = mAudioRecord.read(buffer, 0, buffer.length);

                if (nbytes > 0) {
                    int amplitude = calculateAmplitude(buffer, nbytes);
                    dispatchMicActivity(amplitude);

                    if (mRecognizer.acceptWaveForm(buffer, nbytes)) {
                        String result = mRecognizer.getResult();
                        dispatchResult(result);
                        mSpeechStarted = false;
                        mSilenceFrameCount = 0;
                    } else {
                        String jsonResult = mRecognizer.getPartialResult();
                        dispatchPartialResult(jsonResult);
                        mSpeechStarted = true;
                        mSilenceFrameCount = 0;
                    }

                    if (amplitude < SILENCE_THRESHOLD) {
                        mSilenceFrameCount++;
                        if (mSilenceFrameCount > MIN_SILENCE_FRAMES) {
                            if (mSpeechStarted) {
                                String finalResult = mRecognizer.getFinalResult();
                                dispatchResult(finalResult);
                            } else {
                                dispatchNoVoice();
                            }
                            break;
                        }
                    } else {
                        mSilenceFrameCount = 0;
                    }
                }
            }

            String finalResult = mRecognizer.getFinalResult();
            if (finalResult != null && !finalResult.isEmpty()) {
                dispatchResult(finalResult);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in recognition loop", e);
            dispatchError(e.getMessage());
        } finally {
            cleanup();
        }
    }

    private int calculateAmplitude(short[] buffer, int length) {
        long sum = 0;
        for (int i = 0; i < length; i++) {
            sum += Math.abs(buffer[i]);
        }
        return (int) (sum / length);
    }

    private void dispatchMicActivity(int amplitude) {
        int level = Math.min(10000, amplitude * 100);
        mMainHandler.post(() -> {
            if (mCallback != null) {
                mCallback.onMicActivity(level);
            }
        });
    }

    private void dispatchResult(String jsonResult) {
        String text = parseResultText(jsonResult);
        if (text == null || text.isEmpty()) {
            return;
        }

        mMainHandler.post(() -> {
            if (mCallback != null) {
                mCallback.onResult(text, 1.0f);
            }
        });
    }

    private void dispatchPartialResult(String jsonResult) {
        String text = parseResultText(jsonResult);
        if (text == null || text.isEmpty()) {
            return;
        }

        mMainHandler.post(() -> {
            if (mCallback != null) {
                mCallback.onPartialResult(text);
            }
        });
    }

    @Nullable
    private String parseResultText(String jsonResult) {
        if (jsonResult == null || jsonResult.isEmpty()) {
            return null;
        }
        try {
            JSONObject result = new JSONObject(jsonResult);
            String text = result.optString("text", "");
            return text != null && !text.trim().isEmpty() ? text.trim() : null;
        } catch (JSONException e) {
            Log.w(TAG, "Failed to parse Vosk result", e);
            return null;
        }
    }

    private void dispatchNoVoice() {
        mIsActive = false;
        mMainHandler.post(() -> {
            if (mCallback != null) {
                mCallback.onNoVoice();
            }
        });
    }

    private void dispatchError(String error) {
        mIsActive = false;
        mMainHandler.post(() -> {
            if (mCallback != null) {
                mCallback.onError(Callback.SPEECH_ERROR, error != null ? error : "Unknown error");
            }
        });
    }

    @Override
    public void stop() {
        mStopRecognition.set(true);
    }

    private void cleanup() {
        Log.d(TAG, "Starting cleanup");

        if (mRecognitionThread != null) {
            assert(Thread.currentThread() == mRecognitionThread);
            mRecognitionThread = null;
        }

        if (mAudioRecord != null) {
            try {
                if (mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    mAudioRecord.stop();
                }
                mAudioRecord.release();
            } catch (Exception e) {
                Log.w(TAG, "Error stopping AudioRecord", e);
            }
            mAudioRecord = null;
        }

        if (mRecognizer != null) {
            try {
                mRecognizer.close();
            } catch (Exception e) {
                Log.w(TAG, "Error closing Recognizer", e);
            }
            mRecognizer = null;
        }

        if (mModel != null) {
            try {
                mModel.close();
            } catch (Exception e) {
                Log.w(TAG, "Error closing Model", e);
            }
            mModel = null;
        }

        mMainHandler.post(() -> {
            mCallback = null;
        });

        mIsActive = false;
        Log.d(TAG, "Cleanup complete");
    }

    @Override
    public boolean isActive() {
        return mIsActive;
    }

    @Override
    public boolean shouldDisplayStoreDataPrompt() {
        return false;
    }

    @Override
    public List<String> getSupportedLanguages() {
        return mModelManager.getAvailableLanguages();
    }

    @Nullable
    private String findClosestSupportedLanguage(String locale) {
        if (locale == null) {
            return "en";
        }

        List<String> availableLangs = mModelManager.getAvailableLanguages();
        String lang = locale.split("-")[0].toLowerCase();

        if (availableLangs.contains(lang)) {
            return lang;
        }

        for (String availableLang : availableLangs) {
            if (locale.startsWith(availableLang)) {
                return availableLang;
            }
        }

        return "en";
    }

    @Override
    public boolean isModelDownloaded(@Nullable String lang) {
        return mModelManager.isModelDownloaded(lang);
    }

    @Override
    public void cancelDownload() {
        mModelManager.cancelDownload();
    }

    @Override
    public void downloadModel(@Nullable String lang, @NonNull DownloadCallback callback) {
        mModelManager.downloadModel(lang, new VoskModelManager.DownloadCallback() {
            @Override
            public void onProgress(int progress) {
                callback.onProgress(progress);
            }

            @Override
            public void onSuccess(@NonNull File modelDir) {
                callback.onSuccess();
            }

            @Override
            public void onError(@NonNull String error) {
                callback.onError(error);
            }
        });
    }

    public void shutdown() {
        stop();
        mModelManager.shutdown();
        mExecutor.shutdown();
    }
}
