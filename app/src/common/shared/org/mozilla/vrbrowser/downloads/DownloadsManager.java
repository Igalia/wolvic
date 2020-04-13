package org.mozilla.vrbrowser.downloads;

import  android.app.DownloadManager;
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

import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.utils.UrlUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
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
            if (!new File(UrlUtils.stripProtocol(download.getOutputFile())).exists()) {
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
            mExecutor.scheduleAtFixedRate(mDownloadUpdateTask, 0, REFRESH_INTERVAL, TimeUnit.MILLISECONDS);
        }
    }

    public void removeListener(@NonNull DownloadsListener listener) {
        mListeners.remove(listener);
        if (mListeners.size() == 0) {
            mExecutor.remove(mDownloadUpdateTask);
        }
    }

    public void startDownload(@NonNull DownloadJob job) {
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
        if (SettingsStore.getInstance(mContext).getDownloadsStorage() == SettingsStore.EXTERNAL) {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, job.getFilename());

        } else {
            String outputPath = getOutputPathForJob(job);
            if (outputPath == null) {
                notifyDownloadError("Cannot create output file", job.getFilename());
                return;
            }
            request.setDestinationUri(Uri.parse(outputPath));
        }

        mDownloadManager.enqueue(request);
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

    public void removeDownload(long downloadId) {
        mDownloadManager.remove(downloadId);
        notifyDownloadsUpdate();
    }

    public void removeAllDownloads() {
        if (getDownloads().size() > 0) {
            mDownloadManager.remove(getDownloads().stream().mapToLong(Download::getId).toArray());
            notifyDownloadsUpdate();
        }
    }

    public void clearDownload(long downloadId) {
        Download download = getDownload(downloadId);
        if (download != null) {
            File file = new File(UrlUtils.stripProtocol(download.getOutputFile()));
            if (file.exists()) {
                File newFile = new File(UrlUtils.stripProtocol(download.getOutputFile().concat(".bak")));
                file.renameTo(newFile);
                mDownloadManager.remove(downloadId);
                newFile.renameTo(file);
            }
        }
        notifyDownloadsUpdate();
    }

    public void clearAllDownloads() {
        getDownloads().forEach(download -> {
            clearDownload(download.getId());
        });
        notifyDownloadsUpdate();
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
        mListeners.forEach(listener -> listener.onDownloadsUpdate(downloads));
    }

    private void notifyDownloadCompleted(@NonNull Download download) {
        mListeners.forEach(listener -> listener.onDownloadCompleted(download));
    }

    private void notifyDownloadError(@NonNull String error, @NonNull String file) {
        mListeners.forEach(listener -> listener.onDownloadError(error, file));
    }

    private Runnable mDownloadUpdateTask = new Runnable() {
        @Override
        public void run() {
            DownloadManager.Query query = new DownloadManager.Query();
            Cursor c = mDownloadManager.query(query);

            while (c.moveToNext()) {
                int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_RUNNING) {
                    mMainHandler.post(() -> notifyDownloadsUpdate());
                }
            }
            c.close();
        }
    };

}
