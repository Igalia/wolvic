package com.igalia.wolvic.downloads;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.format.Formatter;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import com.igalia.wolvic.R;
import com.igalia.wolvic.ui.adapters.Language;
import com.igalia.wolvic.utils.LocaleUtils;
import com.igalia.wolvic.utils.StringUtils;

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
    private String mLocalUri;
    private String mTitle;
    private String mDescription;
    private @Status int mStatus;
    private long mLastModified;
    private String mReason;
    private Uri mOutputFileUri = null;

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
        download.mLocalUri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
        download.mDescription = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION));
        download.mSizeBytes = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
        download.mDownloadedBytes = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
        download.mLastModified = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP));
        download.mReason = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_REASON));

        if (download.mLocalUri != null) {
            download.mOutputFileUri = Uri.parse(download.mLocalUri);
        }
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

    public Uri getOutputFileUri() {
        return mOutputFileUri;
    }

    public String getOutputFileUriAsString() {
        if (mOutputFileUri != null) {
            return mOutputFileUri.toString();
        }
        return null;
    }

    public File getOutputFile() {
        if (mOutputFileUri != null) {
            String path = mOutputFileUri.getPath();
            if (path != null) {
                return new File(path);
            }
        }
        return null;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getDescription() {
        return mDescription;
    }

    @Status
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
        if (!StringUtils.isEmpty(mTitle)) {
            return mTitle;
        } else {
            try {
                File f = new File(new URL(mUri).getPath());
                return f.getName();
            } catch (Exception e) {
                if (mLocalUri != null) {
                    return mLocalUri;
                } else {
                    return "";
                }
            }
        }
    }

    public String getReason() {
        return mReason;
    }

    @NonNull
    public static String progressString(@NonNull Context context, @NonNull Download download) {
        Language language = LocaleUtils.getDisplayLanguage(context);
        switch (download.mStatus) {
            case Download.RUNNING:
                try {
                    return Formatter.formatFileSize(context, download.mDownloadedBytes) + '/' +
                            Formatter.formatFileSize(context, download.mSizeBytes) +
                            String.format(language.getLocale(), " (%d%%)",
                            (download.mDownloadedBytes*100)/download.mSizeBytes);

                } catch (Exception e) {
                    return "-/-";
                }

            case Download.SUCCESSFUL:
                return Formatter.formatFileSize(context, download.mSizeBytes);

            case Download.FAILED:
                return context.getString(R.string.download_status_failed);

            case Download.PAUSED:
                return context.getString(R.string.download_status_paused);

            case Download.PENDING:
                return context.getString(R.string.download_status_pending);

            case Download.UNAVAILABLE:
                return context.getString(R.string.download_status_unavailable);

            default:
                return context.getString(R.string.download_status_unknown_error);
        }
    }
}
