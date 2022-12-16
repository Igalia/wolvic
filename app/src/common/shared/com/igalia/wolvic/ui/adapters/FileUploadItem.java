package com.igalia.wolvic.ui.adapters;

import android.content.Context;
import android.net.Uri;
import android.text.format.Formatter;

import androidx.annotation.NonNull;

import com.igalia.wolvic.utils.UrlUtils;

import java.util.Objects;

public class FileUploadItem {
    private final long mId;
    private final String mFilename;
    private final Uri mUri;
    private final String mMimeType;
    private final long mSizeBytes;

    public FileUploadItem(@NonNull String filename, @NonNull Uri uri, @NonNull String mimeType, long sizeBytes) {
        mFilename = filename;
        mUri = uri;
        mSizeBytes = sizeBytes;

        // The download managed might give a generic type e.g. "image" or "image/*";
        // in those cases, we use the file extension if it is more specific.
        if (mimeType.contains("/") && !mimeType.endsWith("/*")) {
            mMimeType = mimeType;
        } else {
            String extensionMime = UrlUtils.getMimeTypeFromUrl(filename);
            mMimeType = extensionMime.equals(UrlUtils.UNKNOWN_MIME_TYPE) ? mimeType : extensionMime;
        }

        // the id is just the item's hashCode
        mId = hashCode();
    }

    public long getId() {
        return mId;
    }

    public String getFilename() {
        return mFilename;
    }

    public Uri getUri() {
        return mUri;
    }

    public String getMimeType() {
        return mMimeType;
    }

    public long getSizeBytes() {
        return mSizeBytes;
    }

    public String getSizeString(Context context) {
        return Formatter.formatFileSize(context, mSizeBytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileUploadItem that = (FileUploadItem) o;
        return mSizeBytes == that.mSizeBytes && mFilename.equals(that.mFilename) && mUri.equals(that.mUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mFilename, mUri, mSizeBytes);
    }

    @Override
    public String toString() {
        return "FileUploadItem{" +
                "Id=" + mId +
                ", Filename='" + mFilename + '\'' +
                ", Uri=" + mUri +
                ", MimeType='" + mMimeType + '\'' +
                ", Size=" + mSizeBytes +
                '}';
    }
}
