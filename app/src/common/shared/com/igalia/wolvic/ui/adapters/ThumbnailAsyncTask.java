package com.igalia.wolvic.ui.adapters;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
// TODO: Deprecated AsyncTask, see https://github.com/Igalia/wolvic/issues/801
import android.os.AsyncTask;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.igalia.wolvic.utils.SystemUtils;
import com.igalia.wolvic.utils.UrlUtils;

import java.io.File;
import java.io.IOException;

public class ThumbnailAsyncTask extends AsyncTask<Void, Void, Bitmap> {

    static final String LOGTAG = SystemUtils.createLogtag(ThumbnailAsyncTask.class);

    public interface OnSuccessDelegate {
        void onSuccess(Bitmap bitmap);
    }

    private static final Size DEFAULT_SIZE = new Size(96, 96);

    private final ContentResolver mContentResolver;
    private final Uri mFileUri;
    private final OnSuccessDelegate mOnSuccessDelegate;
    private CancellationSignal mCancellationSignal;

    public ThumbnailAsyncTask(@NonNull Context context, Uri fileUri, OnSuccessDelegate onSuccessDelegate) {
        mContentResolver = context.getContentResolver();
        mFileUri = fileUri;
        mOnSuccessDelegate = onSuccessDelegate;
    }

    @Override
    protected Bitmap doInBackground(Void... voids) {
        if (mFileUri == null)
            return null;

        if (UrlUtils.isFileUri(mFileUri.toString())) {
            File file = new File(mFileUri.getPath());
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                return createFileThumbnail(file);
            } else {
                return createFileThumbnailLegacy(file);
            }
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                return createContentThumbnail(mFileUri);
            } else {
                return createContentThumbnailLegacy(mFileUri);
            }
        }
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();

        if (mCancellationSignal != null) {
            mCancellationSignal.cancel();
            mCancellationSignal = null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private Bitmap createFileThumbnail(@NonNull File file) {
        String mimeType = UrlUtils.getMimeTypeFromUrl(file.getPath());
        mCancellationSignal = new CancellationSignal();

        try {
            if (mimeType.startsWith("audio")) {
                return ThumbnailUtils.createAudioThumbnail(file, DEFAULT_SIZE, mCancellationSignal);
            } else if (mimeType.startsWith("video")) {
                return ThumbnailUtils.createVideoThumbnail(file, DEFAULT_SIZE, mCancellationSignal);
            } else if (mimeType.startsWith("image")) {
                return ThumbnailUtils.createImageThumbnail(file, DEFAULT_SIZE, mCancellationSignal);
            }
        } catch (IOException e) {
            Log.w(LOGTAG, "createFileThumbnail error, file=" + file + " : " + e.getMessage());
        }
        return null;
    }

    private Bitmap createFileThumbnailLegacy(@NonNull File file) {
        // TODO implement
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private Bitmap createContentThumbnail(@NonNull Uri uri) {
        try {
            return mContentResolver.loadThumbnail(uri, DEFAULT_SIZE, null);
        } catch (IOException e) {
            Log.w(LOGTAG, "createContentThumbnail error, uri=" + uri + " : " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private Bitmap createContentThumbnailLegacy(@NonNull Uri uri) {
        // TODO implement
        return null;
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        if (bitmap != null && mOnSuccessDelegate != null) {
            (new Handler(Looper.getMainLooper())).post(() -> mOnSuccessDelegate.onSuccess(bitmap));
        }
    }
}