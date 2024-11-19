package com.igalia.wolvic.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.downloads.Download;
import com.igalia.wolvic.downloads.DownloadJob;
import com.igalia.wolvic.downloads.DownloadsManager;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.utils.zip.UnzipCallback;
import com.igalia.wolvic.utils.zip.UnzipTask;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class EnvironmentsManager implements DownloadsManager.DownloadsListener, SharedPreferences.OnSharedPreferenceChangeListener {

    public interface EnvironmentListener {
        default void onEnvironmentSetSuccess(@NonNull String envId) {}
        default void onEnvironmentSetError(@NonNull String error) {}
    }

    static final String LOGTAG = SystemUtils.createLogtag(EnvironmentsManager.class);

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
        if (environment == null) {
            return;
        }

        final String payload = EnvironmentUtils.getEnvironmentPayload(environment);
        try {
            new URL(payload).toURI();
        } catch (Exception e) {
            Log.e(LOGTAG, "Invalid URI in payload for environment " + envId + ": " + e.getMessage());
            return;
        }
    
        // Check if the env is being downloaded or has been already downloaded.
        // The download item will be removed in 2 situations:
        //   1- the user selects another env before the download is completed (see setOrDownloadEnvironment)
        //   2- when the unzip task is completed successfully.
        Download envItem = mDownloadManager.getDownloads().stream()
                .filter(item -> item.getUri().equals(payload))
                .findFirst().orElse(null);

        if (envItem == null) {
            // If the env has not been downloaded, start downloading it
            String outputPath = mContext.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
            DownloadJob job = DownloadJob.create(payload);
            job.setOutputPath(outputPath + "/" + job.getFilename());
            mDownloadManager.startDownload(job);
        } else if (envItem.getStatus() == Download.SUCCESSFUL) {
            Log.w(LOGTAG, "The unzip task failed for unknown reasons");
            mEnvDownloadId = -1;
            mDownloadManager.removeDownload(envItem.getId(), true);
        } else  {
            // The 'downloadEnvironment' method is called either by 'setOrDownloadEnvironment' when the user changes the selected
            // option in the radiobuttons widget (this cause the download item to be removed) or by the 'setOrDownloadEnvironment'
            // method during the java initialization invoked by the VRBrowser native code.
            Log.w(LOGTAG, "Download is still in progress; we shouldn't reach this code.");
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
        assert download.getStatus() == Download.SUCCESSFUL;
        if (download.getOutputFile() == null) {
            Log.w(LOGTAG, "Failed to download URI, missing input stream: " + download.getUri());
            return;
        }
        Environment env = EnvironmentUtils.getExternalEnvironmentByPayload(mContext, download.getUri());
        if (env != null) {
            mEnvDownloadId = -1;

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
                    // We don't want the download to be left in the downloads list, but we wait until the unzip task is completed to remove it.
                    mDownloadManager.removeDownload(download.getId(), true);

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
                unzip.start(download.getOutputFile().getPath(), zipOutputPath);
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
