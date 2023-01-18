package com.igalia.wolvic.downloads;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
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
import com.igalia.wolvic.utils.StringUtils;
import com.igalia.wolvic.utils.UrlUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    public void downloadBlobUri(DownloadJob job) {
        if (job.getInputStream() == null) {
            Log.w(LOGTAG, "Failed to download Blob URI, missing input stream: " + job.getUri());
            return;
        }

        final File dir = mContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
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
                try {
                    name = name.substring(0, lastDashIndex - 1);
                    String index = name.substring(lastDashIndex + 1);
                    currentIndex = Integer.parseInt(index);
                } catch (Exception e) {
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

        mDownloadManager.addCompletedDownload(file.getName(), file.getName(),
                true, UrlUtils.getMimeTypeFromUrl(file.getPath()), file.getPath(), readBytes, true,
                Uri.parse(job.getUri().replaceFirst("^blob:","")), null);
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
                        item.getStatus() == Download.RUNNING)
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

    //Returns the content URI of the public copy of this downloaded file, if it exists.
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public Uri getContentUriForDownloadedFile(@NonNull Download download) {
        ContentResolver contentResolver = mContext.getContentResolver();

        // We assume that the copy would have the same name, origin, and size.
        Cursor cursor = contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Downloads._ID},
                MediaStore.Downloads.DISPLAY_NAME + "=? AND " +
                        MediaStore.Downloads.DOWNLOAD_URI + "=? AND " +
                        MediaStore.Downloads.SIZE + "=" + download.getSizeBytes(),
                new String[]{
                        download.getFilename(),
                        download.getUri()
                },
                null,
                null
        );
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)));
            }
            cursor.close();
        }
        return null;
    }

    // Copies the downloaded file to a content URI in Android's media database.
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public void copyToContentUri(long downloadId, @NonNull CopyToContentUriCallback callback) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler uiHandler = new Handler(Looper.getMainLooper());
        ContentResolver contentResolver = mContext.getContentResolver();

        Download download = getDownload(downloadId);
        if (download == null) {
            uiHandler.post(() -> callback.onFailure(null));
            return;
        }

        // Check that the file has not already been copied before
        Uri existingUri = getContentUriForDownloadedFile(download);
        if (existingUri != null) {
            uiHandler.post(() -> callback.onSuccess(existingUri, false));
            return;
        }

        // Fill in the values for a new entry in the Downloads table in MediaStore.
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Downloads.DISPLAY_NAME, download.getFilename());
        contentValues.put(MediaStore.Downloads.SIZE, download.getSizeBytes());
        contentValues.put(MediaStore.Downloads.DATE_MODIFIED, download.getLastModified());
        contentValues.put(MediaStore.Downloads.DOWNLOAD_URI, download.getUri());

        String mimeFromExtension = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(download.getOutputFilePath());
        if (extension != null) {
            mimeFromExtension = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }

        File downloadedFile = new File(download.getOutputFilePath());
        Uri downloadedFileUri = Uri.fromFile(downloadedFile);
        String mimeFromFile = contentResolver.getType(downloadedFileUri);

        String mimeFromDownload = download.getMediaType();

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

        Uri contentUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);

        if (contentUri == null) {
            uiHandler.post(() -> callback.onFailure(download));
            return;
        }

        // Copy the file asynchronously. Callbacks will happen in the UI thread.
        executor.execute(() -> {
            try {
                Files.copy(downloadedFile.toPath(), contentResolver.openOutputStream(contentUri));
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0);
                contentResolver.update(contentUri, contentValues, null, null);

                uiHandler.post(() -> callback.onSuccess(contentUri, true));
            } catch (IOException e) {
                Log.e(LOGTAG, "Error while copying: " + e.getMessage(), e);

                contentResolver.delete(contentUri, null, null);

                uiHandler.post(() -> callback.onFailure(download));
            }
        });
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
