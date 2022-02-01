package com.igalia.wolvic.utils.download;

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

import com.igalia.wolvic.utils.storage.StorageUtils;

import java.io.File;
import java.net.URI;
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
            if (!new File(stripProtocol(download.getOutputFile())).exists()) {
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

    public void startDownload(@NonNull DownloadJob job, @StorageUtils.StorageType int storageType) {
        if (!URLUtil.isHttpUrl(job.getUri()) && !URLUtil.isHttpsUrl(job.getUri())) {
            notifyDownloadError("Cannot download non http/https files", job.getFilename());
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
            if (storageType == StorageUtils.EXTERNAL_STORAGE) {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, job.getFilename());

            } else {
                String outputPath = getOutputPathForJob(job);
                if (outputPath == null) {
                    notifyDownloadError("Cannot create output file", job.getFilename());
                    return;
                }
                request.setDestinationUri(Uri.parse(outputPath));
            }

        } else {
            request.setDestinationUri(Uri.parse("file://" + job.getOutputPath()));
        }

        mDownloadManager.enqueue(request);
        scheduleUpdates();
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

    @Nullable
    public String getOutputPathForUrl(@NonNull String url) {
        File file = new File(URI.create(url).getPath());
        String fileName = file.getName();
        int pos = fileName.lastIndexOf(".");
        if (pos > 0 && pos < (fileName.length() - 1)) { // If '.' is not the first or last character.
            fileName = fileName.substring(0, pos);
        }
        File outputFolder =  new File(mContext.getExternalFilesDir(null), Environment.DIRECTORY_DOWNLOADS);
        File outputFile = new File(outputFolder, fileName);

        return outputFile.getAbsolutePath();
    }

    public void removeDownload(long downloadId, boolean deleteFiles) {
        Download download = getDownload(downloadId);
        if (download != null) {
            if (!deleteFiles) {
                File file = new File(stripProtocol(download.getOutputFile()));
                if (file.exists()) {
                    File newFile = new File(stripProtocol(download.getOutputFile().concat(".bak")));
                    file.renameTo(newFile);
                    mDownloadManager.remove(downloadId);
                    newFile.renameTo(file);

                } else {
                    mDownloadManager.remove(downloadId);
                }

            } else {
                mDownloadManager.remove(downloadId);
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

        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(downloadId);
        Cursor c = mDownloadManager.query(query);
        if (c.moveToFirst()) {
            download = Download.from(c);
        }
        c.close();

        return download;
    }

    public List<Download> getDownloads() {
        List<Download> downloads = new ArrayList<>();

        DownloadManager.Query query = new DownloadManager.Query();
        Cursor c = mDownloadManager.query(query);
        while (c.moveToNext()) {
            downloads.add(Download.from(c));
        }
        c.close();

        return downloads;
    }

    private BroadcastReceiver mDownloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);

            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(downloadId);
                Cursor c = mDownloadManager.query(query);
                if (c.moveToFirst()) {
                    notifyDownloadsUpdate();
                    notifyDownloadCompleted(Download.from(c));
                }
                c.close();
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

    private String stripProtocol(@Nullable String host) {
        if (host == null) {
            return "";
        }

        if (host.startsWith("data:")) {
            return "";
        }

        String result;
        int index = host.indexOf("://");
        if (index >= 0) {
            result = host.substring(index + 3);
        } else {
            result = host;
        }

        if (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }

        return result;
    }

}
