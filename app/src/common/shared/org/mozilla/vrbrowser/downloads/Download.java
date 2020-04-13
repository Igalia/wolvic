package org.mozilla.vrbrowser.downloads;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.ui.adapters.Language;
import org.mozilla.vrbrowser.utils.LocaleUtils;

import java.io.File;
import java.net.URL;

public class Download {

    @IntDef(value = { UNAVAILABLE, PENDING, RUNNING, PAUSED, SUCCESSFUL, FAILED})
    @interface Status {}
    public static final int UNAVAILABLE = 0;
    public static final int PENDING = DownloadManager.STATUS_PENDING;
    public static final int RUNNING = DownloadManager.STATUS_RUNNING;
    public static final int PAUSED = DownloadManager.STATUS_PAUSED;
    public static final int SUCCESSFUL = DownloadManager.STATUS_SUCCESSFUL;
    public static final int FAILED = DownloadManager.STATUS_FAILED;

    private static final long MEGABYTE = 1024L * 1024L;
    private static final long KILOBYTE = 1024L;

    private long mId;
    private String mUri;
    private String mMediaType;
    private long mSizeBytes;
    private long mDownloadedBytes;
    private String mOutputFile;
    private String mTitle;
    private String mDescription;
    private @Status int mStatus;
    private long mLastModified;

    public static Download from(Cursor cursor) {
        Download download = new Download();
        download.mId = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_ID));
        download.mUri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_URI));
        int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
        switch (status) {
            case DownloadManager.STATUS_RUNNING:
                download.mStatus = RUNNING;
                break;
            case DownloadManager.STATUS_FAILED:
                download.mStatus = FAILED;
                break;
            case DownloadManager.STATUS_PAUSED:
                download.mStatus = PAUSED;
                break;
            case DownloadManager.STATUS_PENDING:
                download.mStatus = PENDING;
                break;
            case DownloadManager.STATUS_SUCCESSFUL:
                download.mStatus = SUCCESSFUL;
                break;
            default:
                download.mStatus = UNAVAILABLE;
        }
        download.mMediaType = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE));
        download.mTitle = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_TITLE));
        download.mOutputFile = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
        download.mDescription = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION));
        download.mSizeBytes = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
        download.mDownloadedBytes = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
        download.mLastModified = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP));
        return download;
    }

    public long getId() {
        return mId;
    }

    public String getUri() {
        return mUri;
    }

    public String getMediaType() {
        return mMediaType;
    }

    public long getSizeBytes() {
        return mSizeBytes;
    }

    public long getDownloadedBytes() {
        return mDownloadedBytes;
    }

    public String getOutputFile() {
        return mOutputFile;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getDescription() {
        return mDescription;
    }

    public int getStatus() {
        return mStatus;
    }

    public String getStatusString() {
        switch (mStatus) {
            case RUNNING:
                return "RUNNING";
            case FAILED:
                return "FAILED";
            case PAUSED:
                return "PAUSED";
            case PENDING:
                return "PENDING";
            case SUCCESSFUL:
                return "SUCCESSFUL";
            case UNAVAILABLE:
                return "UNAVAILABLE";
            default:
                return "UNKNOWN";
        }
    }

    public long getLastModified() {
        return mLastModified;
    }

    public double getProgress() {
        if (mSizeBytes != -1) {
            return mDownloadedBytes*100.0/mSizeBytes;
        }
        return 0;
    }

    public String getFilename() {
        try {
            File f = new File(new URL(mOutputFile).getPath());
            return f.getName();

        } catch (Exception e) {
            if (mOutputFile != null) {
                return mOutputFile;
                
            } else {
                return "";
            }
        }
    }

    @NonNull
    public static String progressString(@NonNull Context context, @NonNull Download download) {
        Language language = LocaleUtils.getDisplayLanguage(context);
        if (download.mStatus == RUNNING) {
            if (download.mSizeBytes < MEGABYTE) {
                return String.format(language.getLocale(), "%.2f/%.2fKb (%d%%)",
                        ((double)download.mDownloadedBytes / (double)KILOBYTE),
                        ((double)download.mSizeBytes / (double)KILOBYTE),
                        (download.mDownloadedBytes*100)/download.mSizeBytes);

            } else {
                return String.format(language.getLocale(), "%.2f/%.2fMB (%d%%)",
                        ((double)download.mDownloadedBytes / (double)MEGABYTE),
                        ((double)download.mSizeBytes / (double)MEGABYTE),
                        (download.mDownloadedBytes*100)/download.mSizeBytes);
            }

        } else {
            if (download.mSizeBytes < MEGABYTE) {
                return String.format(language.getLocale(), "%.2fKb", ((double)download.mSizeBytes / (double)KILOBYTE));

            } else {
                return String.format(language.getLocale(), "%.2fMB", ((double)download.mSizeBytes / (double)MEGABYTE));
            }
        }
    }
}
