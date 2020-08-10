package org.mozilla.vrbrowser.downloads;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.utils.UrlUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DownloadsManager {

    private static final String LOGTAG = DownloadsManager.class.getSimpleName();

    private static final int REFRESH_INTERVAL = 100;

    public interface DownloadsListener {
        default void onDownloadsUpdate(@NonNull List<Download> downloads) {}
        default void onDownloadCompleted(@NonNull Download download) {}
        default void onDownloadError(@NonNull String error, @NonNull String file) {}
    }

    private Handler mMainHandler;
    private Context mContext;
    private List<DownloadsListener> mListeners;
    private DownloadManager mDownloadManager;
    private ScheduledThreadPoolExecutor mExecutor;
    private ScheduledFuture<?> mFuture;

    public DownloadsManager(@NonNull Context context) {
        mMainHandler = new Handler(Looper.getMainLooper());
        mContext = context;
        mListeners = new ArrayList<>();
        mDownloadManager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
        mExecutor = new ScheduledThreadPoolExecutor(1);
    }

    public  void init() {
        mContext.registerReceiver(mDownloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        List<Download> downloads = getDownloads();
        downloads.forEach(download -> {
            if (mDownloadManager != null &&
                    !new File(UrlUtils.stripProtocol(download.getOutputFileUri())).exists()) {
                mDownloadManager.remove(download.getId());
            }
        });
    }

    public void end() {
        mContext.unregisterReceiver(mDownloadReceiver);
    }

    public void addListener(@NonNull DownloadsListener listener) {
        mListeners.add(listener);
        if (mListeners.size() == 1) {
            scheduleUpdates();
        }
    }

    public void removeListener(@NonNull DownloadsListener listener) {
        mListeners.remove(listener);
        if (mListeners.size() == 0) {
            stopUpdates();
        }
    }

    private void scheduleUpdates() {
        if (mFuture != null) {
            // Already scheduled
            return;
        }
        mFuture = mExecutor.scheduleAtFixedRate(mDownloadUpdateTask, 0, REFRESH_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private void stopUpdates() {
        if (mFuture != null) {
            mFuture.cancel(true);
            mFuture = null;
        }
    }

    public void startDownload(@NonNull DownloadJob job) {
        startDownload(job, SettingsStore.getInstance(mContext).getDownloadsStorage());
    }

    public void startDownload(@NonNull DownloadJob job, @SettingsStore.Storage int storageType) {
        if (!URLUtil.isHttpUrl(job.getUri()) && !URLUtil.isHttpsUrl(job.getUri())) {
            notifyDownloadError(mContext.getString(R.string.download_error_protocol), job.getFilename());
            return;
        }

        Uri url = Uri.parse(job.getUri());
        DownloadManager.Request request = new DownloadManager.Request(url);
        request.setTitle(job.getTitle());
        request.setDescription(job.getDescription());
        request.setMimeType(job.getContentType());
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setVisibleInDownloadsUi(false);
        if (job.getOutputPath() == null) {
            if (storageType == SettingsStore.EXTERNAL) {
                request.setDestinationInExternalFilesDir(mContext, Environment.DIRECTORY_DOWNLOADS, job.getFilename());

            } else {
                String outputPath = getOutputPathForJob(job);
                if (outputPath == null) {
                    notifyDownloadError(mContext.getString(R.string.download_error_output), job.getFilename());
                    return;
                }
                request.setDestinationUri(Uri.parse(outputPath));
            }

        } else {
            request.setDestinationUri(Uri.parse("file://" + job.getOutputPath()));
        }

        if (mDownloadManager != null) {
            try {
                mDownloadManager.enqueue(request);
            } catch (SecurityException e) {
                notifyDownloadError(mContext.getString(R.string.download_error_output), job.getFilename());
                return;
            }
            scheduleUpdates();
        }
    }

    @Nullable
    private String getOutputPathForJob(@NonNull DownloadJob job) {
        File outputFolder =  new File(mContext.getExternalFilesDir(null), Environment.DIRECTORY_DOWNLOADS);
        if (outputFolder.exists() || (!outputFolder.exists() && outputFolder.mkdir())) {
            File outputFile = new File(outputFolder, job.getFilename());
            return "file://" + outputFile.getAbsolutePath();
        }

        return null;
    }

    public void removeDownload(long downloadId, boolean deleteFiles) {
        Download download = getDownload(downloadId);
        if (download != null) {
            if (!deleteFiles) {
                File file = new File(UrlUtils.stripProtocol(download.getOutputFileUri()));
                if (file.exists()) {
                    File newFile = new File(UrlUtils.stripProtocol(download.getOutputFileUri().concat(".bak")));
                    file.renameTo(newFile);
                    if (mDownloadManager != null) {
                        mDownloadManager.remove(downloadId);
                    }
                    newFile.renameTo(file);

                } else {
                    if (mDownloadManager != null) {
                        mDownloadManager.remove(downloadId);
                    }
                }

            } else {
                if (mDownloadManager != null) {
                    mDownloadManager.remove(downloadId);
                }
            }
        }
        notifyDownloadsUpdate();
    }

    public void removeAllDownloads(boolean deleteFiles) {
        getDownloads().forEach(download -> removeDownload(download.getId(), deleteFiles));
    }

    @Nullable
    public Download getDownload(long downloadId) {
        Download download = null;

        if (mDownloadManager != null) {
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(downloadId);
            Cursor c = mDownloadManager.query(query);
            if (c != null) {
                if (c.moveToFirst()) {
                    download = Download.from(c);
                }
                c.close();
            }
        }

        return download;
    }

    public List<Download> getDownloads() {
        List<Download> downloads = new ArrayList<>();

        if (mDownloadManager != null) {
            DownloadManager.Query query = new DownloadManager.Query();
            Cursor c = mDownloadManager.query(query);
            if (c != null) {
                while (c.moveToNext()) {
                    downloads.add(Download.from(c));
                }
                c.close();
            }
        }

        return downloads;
    }

    public boolean isDownloading() {
        return getDownloads().stream()
                .filter(item ->
                        item.getStatus() == DownloadManager.STATUS_RUNNING)
                .findFirst().orElse(null) != null;
    }

    private BroadcastReceiver mDownloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);

            if (mDownloadManager != null && DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(downloadId);
                Cursor c = mDownloadManager.query(query);
                if (c != null) {
                    if (c.moveToFirst()) {
                        notifyDownloadsUpdate();
                        notifyDownloadCompleted(Download.from(c));
                    }
                    c.close();
                }
            }
        }
    };

    private void notifyDownloadsUpdate() {
        List<Download> downloads = getDownloads();
        int filter = Download.RUNNING | Download.PAUSED | Download.PENDING;
        boolean activeDownloads = downloads.stream().filter(d -> (d.getStatus() & filter) != 0).count()  > 0;
        mListeners.forEach(listener -> listener.onDownloadsUpdate(downloads));
        if (!activeDownloads) {
            stopUpdates();
        }
    }

    private void notifyDownloadCompleted(@NonNull Download download) {
        mListeners.forEach(listener -> listener.onDownloadCompleted(download));
    }

    private void notifyDownloadError(@NonNull String error, @NonNull String file) {
        mListeners.forEach(listener -> listener.onDownloadError(error, file));
    }

    private Runnable mDownloadUpdateTask = () -> {
        mMainHandler.post(this::notifyDownloadsUpdate);
    };

}
