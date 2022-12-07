package com.igalia.wolvic.downloads;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.igalia.wolvic.R;
import com.igalia.wolvic.utils.UrlUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public void startDownload(@NonNull DownloadJob job) {
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
            try {
                request.setDestinationInExternalFilesDir(mContext, Environment.DIRECTORY_DOWNLOADS, job.getFilename());
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
                        Download download = Download.from(c);
                        notifyDownloadCompleted(download);

                        // Copy the downloaded file to the public Downloads folder.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            copyToDownloadsFolder(download);
                        }
                    }
                    c.close();
                }
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void copyToDownloadsFolder(Download download) {
        Log.e(LOGTAG, "************************ copyToDownloadsFolder ************************");
        Log.e(LOGTAG, "DISPLAY_NAME : " + download.getFilename());
        Log.e(LOGTAG, "EXTERNAL_CONTENT_URI : " + MediaStore.Downloads.EXTERNAL_CONTENT_URI);
        Log.e(LOGTAG, "SIZE : " + download.getSizeBytes());
        Log.e(LOGTAG, "OutputFilePath : " + download.getOutputFilePath());

        // Create a new entry for the Downloads table in MediaStore.
        ContentResolver contentResolver = mContext.getContentResolver();
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Downloads.DISPLAY_NAME, download.getFilename());
        contentValues.put(MediaStore.Downloads.SIZE, download.getSizeBytes());
        contentValues.put(MediaStore.Downloads.DATE_MODIFIED, download.getLastModified());
        contentValues.put(MediaStore.Downloads.DOWNLOAD_URI, download.getUri());

        // TODO There are several ways to obtain the MIME type. But how do we pick the right one?

        String mimeFromExtension = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(download.getOutputFilePath());
        if (extension != null) {
            mimeFromExtension = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        Log.e(LOGTAG, "MIME type from extension : " + mimeFromExtension);

        File downloadedFile = new File(download.getOutputFilePath());
        Uri downloadedFileUri = Uri.fromFile(downloadedFile);
        String mimeFromFile = contentResolver.getType(downloadedFileUri);
        Log.e(LOGTAG, "MIME type from file " + downloadedFileUri + " : " + mimeFromFile);

        String mimeFromDownload = download.getMediaType();
        Log.e(LOGTAG, "MIME type from download : " + download.getMediaType());

        // TODO : find out which of the possible MIME types is the most specific.
        if (mimeFromExtension != null) {
            contentValues.put(MediaStore.Downloads.MIME_TYPE, mimeFromExtension);
        } else if (mimeFromFile != null) {
            contentValues.put(MediaStore.Downloads.MIME_TYPE, mimeFromFile);
        } else {
            contentValues.put(MediaStore.Downloads.MIME_TYPE, mimeFromDownload);
        }

        // This flag indicates that the file is not yet ready.
        contentValues.put(MediaStore.Downloads.IS_PENDING, 1);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            contentValues.put(MediaStore.Downloads.IS_DRM, 0);
        }

        Log.e(LOGTAG, "Inserting : " + contentValues);

        Uri uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);

        Log.e(LOGTAG, "Inserted URI : " + uri);

        // This URI can be accessed from the console:
        //     adb shell content query --uri content://media/external/downloads/362

        try {
            Log.e(LOGTAG, "Source file : " + downloadedFile);

            Log.e(LOGTAG, "Copying to : " + uri);
            Files.copy(downloadedFile.toPath(), contentResolver.openOutputStream(uri));

            // The file is finally ready.
            Log.e(LOGTAG, "Updating IS_PENDING");
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0);
            contentResolver.update(uri, contentValues, null, null);

            Log.e(LOGTAG, "************************ Done! ************************");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void notifyDownloadsUpdate() {
        List<Download> downloads = getDownloads();
        int filter = Download.RUNNING | Download.PAUSED | Download.PENDING;
        boolean activeDownloads = downloads.stream().filter(d -> (d.getStatus() & filter) != 0).count() > 0;
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