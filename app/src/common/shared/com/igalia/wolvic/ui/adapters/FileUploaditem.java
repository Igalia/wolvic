package com.igalia.wolvic.ui.adapters;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.Objects;

public class FileUploaditem {
    private final long mId;
    private final String mFilename;
    private final Uri mUri;
    private final long mSizeBytes;

    public FileUploaditem(@NonNull String filename, @NonNull Uri uri, long sizeBytes) {
        mFilename = filename;
        mUri = uri;
        mSizeBytes = sizeBytes;

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

    public long getSizeBytes() {
        return mSizeBytes;
    }

    public String getSizeString() {
        // TODO
        return mSizeBytes + " bytes";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileUploaditem that = (FileUploaditem) o;
        return mSizeBytes == that.mSizeBytes && mFilename.equals(that.mFilename) && mUri.equals(that.mUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mFilename, mUri, mSizeBytes);
    }
}
