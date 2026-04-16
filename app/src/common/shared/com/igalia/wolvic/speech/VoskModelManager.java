package com.igalia.wolvic.speech;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.utils.SystemUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class VoskModelManager {

    private static final String TAG = SystemUtils.createLogtag(VoskModelManager.class);
    private static final String MODELS_DIR = "vosk_models/";
    private static final String MODEL_BASE_URL = "https://alphacephei.com/vosk/models/";

    private static final Map<String, String> MODEL_MAP = new LinkedHashMap<>();
    public static final String VOSK_MODEL_FINAL_MDL_PATH = "am/final.mdl";
    public static final String VOSK_MODEL_ZIP_EXTENSION = ".zip";
    public static final int DOWNLOAD_CONNECTION_TIMEOUT_MS = 30000;

    static {
        // The settings UI assumes the recognizer's supported-languages list includes "default" at
        // a stable index (see VoiceSearchLanguageOptionsView#setLanguage() and its reset path)
        // However we do not include it here to avoid confusion as not all languages are supported.
        MODEL_MAP.put("en", "vosk-model-small-en-us-0.15");
        MODEL_MAP.put("es", "vosk-model-small-es-0.42");
        MODEL_MAP.put("de", "vosk-model-small-de-0.15");
        MODEL_MAP.put("fr", "vosk-model-small-fr-0.22");
        MODEL_MAP.put("it", "vosk-model-small-it-0.22");
        MODEL_MAP.put("ru", "vosk-model-small-ru-0.22");
        MODEL_MAP.put("zh", "vosk-model-small-cn-0.22");
    }

    public interface DownloadCallback {
        void onProgress(int progress);
        void onSuccess(@NonNull File modelDir);
        void onError(@NonNull String error);
    }

    private final Context mContext;
    private final Handler mMainHandler;
    private final ExecutorService mExecutor;
    private volatile boolean mIsDownloading = false;
    private volatile boolean mCancelled = false;
    private volatile java.net.HttpURLConnection mActiveConnection = null;

    public VoskModelManager(@NonNull Context context) {
        mContext = context.getApplicationContext();
        mMainHandler = new Handler(Looper.getMainLooper());
        mExecutor = Executors.newSingleThreadExecutor();
    }

    @NonNull
    public List<String> getAvailableLanguages() {
        return new ArrayList<>(MODEL_MAP.keySet());
    }

    @NonNull
    private static String getModelName(@Nullable String lang) {
        if (lang == null || lang.equals("default")) {
            return MODEL_MAP.get("en");
        }
        String normalizedLang = lang.split("-")[0].toLowerCase();
        String model = MODEL_MAP.get(normalizedLang);
        return model != null ? model : MODEL_MAP.get("en");
    }

    @NonNull
    public String getModelDir(@Nullable String lang) {
        String modelName = getModelName(lang);
        return mContext.getFilesDir().getAbsolutePath() + "/vosk_models/" + modelName;
    }

    public boolean isModelDownloaded(@Nullable String lang) {
        String modelName = getModelName(lang);
        File modelDir = new File(mContext.getFilesDir(), MODELS_DIR + modelName);
        return modelDir.exists() && modelDir.isDirectory() && new File(modelDir, VOSK_MODEL_FINAL_MDL_PATH).exists();
    }

    public boolean isDownloading() {
        return mIsDownloading;
    }

    public void cancelDownload() {
        mCancelled = true;
        java.net.HttpURLConnection conn = mActiveConnection;
        if (conn != null) {
            conn.disconnect();
        }
    }

    public void downloadModel(@Nullable String lang, @Nullable DownloadCallback callback) {
        if (mIsDownloading) {
            if (callback != null) {
                callback.onError("Download already in progress");
            }
            return;
        }

        mCancelled = false;
        mIsDownloading = true;

        mExecutor.execute(() -> {
            String modelName = getModelName(lang);
            String modelDirPath = getModelDir(lang);
            String zipUrl = MODEL_BASE_URL + modelName + VOSK_MODEL_ZIP_EXTENSION;

            File zipFile = null;
            try {
                notifyProgress(callback, 0);
                Log.d(TAG, "Downloading model from: " + zipUrl);

                URL url = new URL(zipUrl);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(DOWNLOAD_CONNECTION_TIMEOUT_MS);
                connection.setReadTimeout(DOWNLOAD_CONNECTION_TIMEOUT_MS);
                connection.setRequestMethod("GET");
                mActiveConnection = connection;

                try {
                    int responseCode = connection.getResponseCode();
                    if (responseCode != java.net.HttpURLConnection.HTTP_OK) {
                        throw new IOException("HTTP error: " + responseCode);
                    }
                    int contentLength = connection.getContentLength();
                    Log.d(TAG, "Content length: " + contentLength);
                    zipFile = new File(mContext.getCacheDir(), modelName + VOSK_MODEL_ZIP_EXTENSION);
                    try (java.io.InputStream input = connection.getInputStream();
                         java.io.FileOutputStream output = new java.io.FileOutputStream(zipFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        long totalBytesRead = 0;
                        while (!mCancelled && (bytesRead = input.read(buffer)) != -1) {
                            output.write(buffer, 0, bytesRead);
                            totalBytesRead += bytesRead;
                            if (contentLength > 0) {
                                int progress = (int) ((totalBytesRead * 100) / contentLength);
                                notifyProgress(callback, progress);
                            }
                        }
                    }
                } finally {
                    mActiveConnection = null;
                    connection.disconnect();
                }

                if (mCancelled) {
                    return;
                }

                File targetDir = new File(modelDirPath).getParentFile();
                Log.d(TAG, "Download complete, extracting to: " + targetDir.getAbsolutePath());
                targetDir.mkdirs();

                unzipFile(zipFile, targetDir);
                Log.d(TAG, "Unzip complete");

                File modelCheck = new File(modelDirPath, VOSK_MODEL_FINAL_MDL_PATH);
                Log.d(TAG, "Checking for model at: " + modelCheck.getAbsolutePath());
                if (!modelCheck.exists())
                    throw new IOException("Model extraction failed: " + VOSK_MODEL_FINAL_MDL_PATH + " not found");

                Log.d(TAG, "Model verification passed");

                notifyProgress(callback, 100);
                notifySuccess(callback, new File(modelDirPath));

            } catch (Exception e) {
                if (!mCancelled) {
                    Log.e(TAG, "Failed to download/unpack model", e);
                    notifyError(callback, e.getMessage() != null ? e.getMessage() : "Unknown error");
                }
            } finally {
                if (zipFile != null) {
                    zipFile.delete();
                }
                mActiveConnection = null;
                mIsDownloading = false;
                mCancelled = false;
            }
        });
    }

    private void unzipFile(File zipFile, File targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new java.io.FileInputStream(zipFile))) {
            ZipEntry entry;
            int count = 0;
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(targetDir, entry.getName());
                Log.d(TAG, "Extracting: " + entry.getName() + " -> " + newFile.getAbsolutePath());

                String canonicalTargetPath = targetDir.getCanonicalPath();
                String canonicalDestPath = newFile.getCanonicalPath();
                if (!canonicalDestPath.startsWith(canonicalTargetPath + File.separator)) {
                    Log.e(TAG, "Zip slip detected: " + entry.getName() + " would escape to " + canonicalDestPath);
                    zis.closeEntry();
                    throw new IOException("Zip slip attempt detected: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    File parent = newFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(newFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                count++;
                zis.closeEntry();
            }
            Log.d(TAG, "Extracted " + count + " entries");
        }
    }

    private void notifyProgress(@Nullable DownloadCallback callback, int progress) {
        if (callback != null) {
            mMainHandler.post(() -> callback.onProgress(progress));
        }
    }

    private void notifySuccess(@Nullable DownloadCallback callback, File modelDir) {
        if (callback != null) {
            mMainHandler.post(() -> callback.onSuccess(modelDir));
        }
    }

    private void notifyError(@Nullable DownloadCallback callback, String error) {
        if (callback != null) {
            mMainHandler.post(() -> callback.onError(error));
        }
    }

    public void shutdown() {
        mExecutor.shutdown();
    }
}
