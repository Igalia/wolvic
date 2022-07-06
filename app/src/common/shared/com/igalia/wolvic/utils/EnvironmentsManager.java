package com.igalia.wolvic.utils;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.downloads.Download;
import com.igalia.wolvic.downloads.DownloadJob;
import com.igalia.wolvic.downloads.DownloadsManager;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.utils.zip.UnzipCallback;
import com.igalia.wolvic.utils.zip.UnzipTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class EnvironmentsManager implements DownloadsManager.DownloadsListener, SharedPreferences.OnSharedPreferenceChangeListener {

    public interface EnvironmentListener {
        default void onEnvironmentSetSuccess(@NonNull String envId) {}
        default void onEnvironmentSetError(@NonNull String error) {}
    }

    private WidgetManagerDelegate mApplicationDelegate;
    private Context mContext;
    private DownloadsManager mDownloadManager;
    private SharedPreferences mPrefs;
    private ArrayList<EnvironmentListener> mListeners;
    private long mEnvDownloadId = -1;

    public EnvironmentsManager(@NonNull Context context) {
        mContext = context;
        mApplicationDelegate = ((WidgetManagerDelegate)context);
        mDownloadManager = mApplicationDelegate.getServicesProvider().getDownloadsManager();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mListeners = new ArrayList<>();
    }

    public void addListener(@NonNull EnvironmentListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(@NonNull EnvironmentListener listener) {
        if (mListeners.contains(listener)) {
            mListeners.remove(listener);
        }
    }

    public void init() {
        mDownloadManager.addListener(this);
        mPrefs.registerOnSharedPreferenceChangeListener(this);
    }

    public void end() {
        mDownloadManager.removeListener(this);
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    public void setOrDownloadEnvironment(@NonNull String envId) {
        if (mEnvDownloadId != -1) {
            mDownloadManager.removeDownload(mEnvDownloadId, true);
        }

        if (EnvironmentUtils.isBuiltinEnvironment(mContext, envId)) {
            SettingsStore.getInstance(mContext).setEnvironment(envId);
            mListeners.forEach(environmentListener -> environmentListener.onEnvironmentSetSuccess(envId));
            mApplicationDelegate.updateEnvironment();

        } else {
            if (EnvironmentUtils.isExternalEnvReady(mContext, envId)) {
                // If the environment is ready, call native to update
                SettingsStore.getInstance(mContext).setEnvironment(envId);
                mListeners.forEach(environmentListener -> environmentListener.onEnvironmentSetSuccess(envId));
                mApplicationDelegate.updateEnvironment();

            } else {
                downloadEnvironment(envId);
            }
        }
    }

    @Nullable
    public String getOrDownloadEnvironment() {
        return getOrDownloadEnvironment(SettingsStore.getInstance(mContext).getEnvironment());
    }

    @Nullable
    public String getOrDownloadEnvironment(@NonNull String envId) {
        String environmentPath = null;
        if (EnvironmentUtils.isBuiltinEnvironment(mContext, envId)) {
            environmentPath = EnvironmentUtils.getBuiltinEnvPath(envId);

        } else {
            if (EnvironmentUtils.isExternalEnvReady(mContext, envId)) {
                // If the environment is ready, return the path
                environmentPath = EnvironmentUtils.getExternalEnvPath(mContext, envId);

            } else {
                downloadEnvironment(envId);
            }
        }

        return environmentPath;
    }

    private void downloadEnvironment(@NonNull String envId) {
        final Environment environment = EnvironmentUtils.getExternalEnvironmentById(mContext, envId);
        if (environment != null) {
            // Check if the env is being downloaded
            boolean isDownloading = mDownloadManager.getDownloads().stream()
                    .filter(item ->
                            item.getStatus() == DownloadManager.STATUS_RUNNING &&
                                    item.getStatus() == DownloadManager.STATUS_PAUSED &&
                                    item.getStatus() == DownloadManager.STATUS_PENDING &&
                                    item.getUri().equals(environment.getPayload()))
                    .findFirst().orElse(null) != null;

            if (!isDownloading) {
                // If the env is not being downloaded, start downloading it
                DownloadJob job = DownloadJob.create(environment.getPayload());
                mDownloadManager.startDownload(job);
            }
        }
    }

    // DownloadsManager


    @Override
    public void onDownloadsUpdate(@NonNull List<Download> downloads) {
        Download envDownload = downloads.stream().filter(download -> EnvironmentUtils.getExternalEnvironmentByPayload(mContext, download.getUri()) != null).findFirst().orElse(null);
        if (envDownload != null) {
            mEnvDownloadId = envDownload.getId();
        }
    }

    @Override
    public void onDownloadCompleted(@NonNull Download download) {
        Environment env = EnvironmentUtils.getExternalEnvironmentByPayload(mContext, download.getUri());
        if (env != null) {
            mEnvDownloadId = -1;

            // We don't want the download to be left in the downloads list, so we just remove it when the download is done.
            mDownloadManager.removeDownload(download.getId(), false);
            UnzipTask unzip = new UnzipTask(mContext);
            unzip.addListener(new UnzipCallback() {
                @Override
                public void onUnzipStart(@NonNull String zipFile) {

                }

                @Override
                public void onUnzipProgress(@NonNull String zipFile, double progress) {

                }

                @Override
                public void onUnzipFinish(@NonNull String zipFile, @NonNull String outputPath) {
                    // Delete the zip file when the unzipping is done.
                    File file = new File(zipFile);
                    file.delete();

                    // the environment is ready, call native to update the current env.
                    SettingsStore.getInstance(mContext).setEnvironment(env.getValue());
                    mListeners.forEach(environmentListener -> environmentListener.onEnvironmentSetSuccess(env.getValue()));
                    mApplicationDelegate.updateEnvironment();
                }

                @Override
                public void onUnzipCancelled(@NonNull String zipFile) {
                    mListeners.forEach(listener -> listener.onEnvironmentSetError(
                            mContext.getString(R.string.environment_download_unzip_error_body)
                    ));
                }

                @Override
                public void onUnzipError(@NonNull String zipFile, @Nullable String error) {
                    mListeners.forEach(listener -> listener.onEnvironmentSetError(
                            mContext.getString(R.string.environment_download_unzip_error_body)
                    ));
                }
            });
            String zipOutputPath = EnvironmentUtils.getEnvPath(mContext, env.getValue());
            if (zipOutputPath != null) {
                unzip.start(download.getOutputFilePath(), zipOutputPath);
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (s.equals(mContext.getString(R.string.settings_key_remote_props))) {
            mApplicationDelegate.updateEnvironment();
        }
    }
}
