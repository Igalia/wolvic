package com.igalia.wolvic.downloads;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserApplication;
import com.igalia.wolvic.utils.StringUtils;
import com.igalia.wolvic.utils.UrlUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
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
    private Executor mDiskExecutor;

    public DownloadsManager(@NonNull Context context) {
        mMainHandler = new Handler(Looper.getMainLooper());
        mContext = context;
        mListeners = new ArrayList<>();
        mDownloadManager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
        mExecutor = new ScheduledThreadPoolExecutor(1);
        mDiskExecutor = ((VRBrowserApplication) mContext.getApplicationContext()).getExecutors().diskIO();
    }

    private void removeDownloadOnDiskIO(long id) {
        mDiskExecutor.execute(() -> { mDownloadManager.remove(id); });
    }

    public void init() {
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mContext.registerReceiver(mDownloadReceiver, filter, null, mMainHandler, Context.RECEIVER_NOT_EXPORTED);
        } else {
            mContext.registerReceiver(mDownloadReceiver, filter, null, mMainHandler);
        }
        List<Download> downloads = getDownloads();
        downloads.forEach(download -> {
            File downloadedFile = download.getOutputFile();
            if (mDownloadManager != null && (downloadedFile == null || !downloadedFile.exists())) {
                removeDownloadOnDiskIO(download.getId());
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
        mFuture = mExecutor.scheduleWithFixedDelay(mDownloadUpdateTask, 0, REFRESH_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private void stopUpdates() {
        if (mFuture != null) {
            mFuture.cancel(true);
            mFuture = null;
        }
    }

    public void startDownload(@NonNull DownloadJob job) {
        if (UrlUtils.isBlobUri(job.getUri())) {
            downloadBlobUri(job);
            return;
        }

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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            request.setVisibleInDownloadsUi(false);
        }

        if (job.getOutputPath() == null) {
            try {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, job.getFilename());
            } catch (IllegalStateException e) {
                e.printStackTrace();
                notifyDownloadError(mContext.getString(R.string.download_error_output), job.getFilename());
                return;
            }
        } else {
            request.setDestinationUri(Uri.parse("file://" + job.getOutputPath()));
        }

        if (mDownloadManager != null) {
            try {
                mDownloadManager.enqueue(request);
            } catch (SecurityException e) {
                e.printStackTrace();
                notifyDownloadError(mContext.getString(R.string.download_error_output), job.getFilename());
                return;
            }
            scheduleUpdates();
        }
    }

    public void downloadBlobUri(DownloadJob job) {
        if (job.getInputStream() == null) {
            Log.w(LOGTAG, "Failed to download Blob URI, missing input stream: " + job.getUri());
            return;
        }

        final File dir = new File(Environment.getExternalStorageDirectory() + "/" + Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            Log.e(LOGTAG, "Error when saving " + job.getUri() + " : failed to get the Downloads directory");
            return;
        }

        File file = new File(dir, job.getFilename());
        if (file.exists()) {
            // If the file already exists, we try to generate a new one.
            String extension = MimeTypeMap.getFileExtensionFromUrl(file.toString());
            if (!StringUtils.isEmpty(extension)) {
                extension = '.' + extension;
            }
            String name = file.getName();
            int lastDotIndex = name.lastIndexOf('.');
            if (lastDotIndex >= 0) {
                name = name.substring(0, lastDotIndex);
            }
            int currentIndex = 0;
            int lastDashIndex = name.lastIndexOf('-');
            if (lastDashIndex >= 0) {
                String nameBackup = name;
                try {
                    name = name.substring(0, lastDashIndex - 1);
                    String index = name.substring(lastDashIndex + 1);
                    currentIndex = Integer.parseInt(index);
                } catch (Exception e) {
                    name = nameBackup;
                }
            }
            do {
                currentIndex++;
                file = new File(dir, name + '-' + currentIndex + extension);
            } while (file.exists() || file.isDirectory());
        }
        Log.i(LOGTAG, "Will save " + job.getUri() + " to " + file.getName());

        long readBytes = 0L;
        byte[] buf = new byte[8192];
        int n;
        try {
            InputStream in = job.getInputStream();
            OutputStream out = new FileOutputStream(file, false);
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                readBytes += n;
            }
            out.close();
        } catch (IOException e) {
            Log.e(LOGTAG, "Error when saving " + job.getUri() + " : " + e.getMessage());
            return;
        }
        Log.i(LOGTAG, "Saved " + job.getUri() + " to " + file.getName() + " (" + readBytes + " bytes)");

        // TODO: Deprecated addCompletedDownload(...), see https://github.com/Igalia/wolvic/issues/798
        long downloadId = mDownloadManager.addCompletedDownload(file.getName(), file.getName(),
                true, UrlUtils.getMimeTypeFromUrl(file.getPath()), file.getPath(), readBytes, true,
                Uri.parse(job.getUri().replaceFirst("^blob:", "")), null);

        notifyDownloadCompleted(downloadId);
    }

    public void removeDownload(long downloadId, boolean deleteFiles) {
        Download download = getDownload(downloadId);
        if (download != null) {
            if (!deleteFiles) {
                File file = download.getOutputFile();
                if (file != null && file.exists()) {
                    File newFile = new File(file.getAbsolutePath().concat(".bak"));
                    file.renameTo(newFile);
                    if (mDownloadManager != null) {
                        removeDownloadOnDiskIO(downloadId);
                    }
                    newFile.renameTo(file);

                } else {
                    if (mDownloadManager != null) {
                        removeDownloadOnDiskIO(downloadId);
                    }
                }

            } else {
                if (mDownloadManager != null) {
                    removeDownloadOnDiskIO(downloadId);
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
                        item.getStatus() == Download.RUNNING)
                .findFirst().orElse(null) != null;
    }

    private BroadcastReceiver mDownloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);

            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                notifyDownloadCompleted(downloadId);
            }
        }
    };

    private void notifyDownloadsUpdate() {
        mDiskExecutor.execute(() -> {
            List<Download> downloads = getDownloads();
            int filter = Download.RUNNING | Download.PAUSED | Download.PENDING;
            boolean activeDownloads = downloads.stream().filter(d -> (d.getStatus() & filter) != 0).count()  > 0;
            mMainHandler.post(() -> {
                mListeners.forEach(listener -> listener.onDownloadsUpdate(downloads));
            });
            if (!activeDownloads) {
                stopUpdates();
            }
        });
    }

    private void notifyDownloadCompleted(@NonNull long downloadId) {
        if (mDownloadManager == null) {
            return;
        }
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(downloadId);
        Cursor c = mDownloadManager.query(query);
        if (c == null) {
            return;
        }
        if (c.moveToFirst()) {
            notifyDownloadsUpdate();
            Download download = Download.from(c);
            if (download.getStatus() == Download.SUCCESSFUL)
                notifyDownloadCompleted(download);
            else
                notifyDownloadError("Failed to download URI, missing input stream: ", download.getUri());
        }
        c.close();
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
