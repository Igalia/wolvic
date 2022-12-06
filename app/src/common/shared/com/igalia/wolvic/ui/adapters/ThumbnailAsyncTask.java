package com.igalia.wolvic.ui.adapters;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.webkit.MimeTypeMap;

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
    private static final int DEFAULT_SIZE_KIND = MediaStore.Images.Thumbnails.MICRO_KIND;

    private final ContentResolver mContentResolver;
    private final Uri mFileUri;
    private final OnSuccessDelegate mOnSuccessDelegate;
    private CancellationSignal mCancellationSignal;

    public ThumbnailAsyncTask(@NonNull Context context, @NonNull Uri fileUri, OnSuccessDelegate onSuccessDelegate) {

        Log.e(LOGTAG, "new ThumbnailAsyncTask");

        mContentResolver = context.getContentResolver();
        mFileUri = fileUri;
        mOnSuccessDelegate = onSuccessDelegate;
    }

    @Override
    protected Bitmap doInBackground(Void... voids) {
        Log.e(LOGTAG, "doInBackground  mFileUri = " + mFileUri);

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
        Log.e(LOGTAG, "createFileThumbnail file = " + file);
        String extension = MimeTypeMap.getFileExtensionFromUrl(file.getPath());
        Log.e(LOGTAG, "  extension = " + extension);
        if (extension == null) {
            return null;
        }
        String mimeFromExtension = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        mCancellationSignal = new CancellationSignal();
        Log.e(LOGTAG, "  mimeFromExtension = " + mimeFromExtension);

        try {
            if (mimeFromExtension.startsWith("audio")) {
                Log.e(LOGTAG, "  createAudioThumbnail");
                return ThumbnailUtils.createAudioThumbnail(file, DEFAULT_SIZE, mCancellationSignal);
            } else if (mimeFromExtension.startsWith("video")) {
                Log.e(LOGTAG, "  createVideoThumbnail");
                return ThumbnailUtils.createVideoThumbnail(file, DEFAULT_SIZE, mCancellationSignal);
            } else if (mimeFromExtension.startsWith("image")) {
                Log.e(LOGTAG, "  createImageThumbnail");
                return ThumbnailUtils.createImageThumbnail(file, DEFAULT_SIZE, mCancellationSignal);
            }
        } catch (IOException e) {
            Log.e(LOGTAG, "  loadThumbnail: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private Bitmap createFileThumbnailLegacy(@NonNull File file) {
        // TODO implement
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private Bitmap createContentThumbnail(@NonNull Uri uri) {
        Log.e(LOGTAG, "createContentThumbnail uri = " + uri);
        try {
            Log.e(LOGTAG, "  loadThumbnail");
            return mContentResolver.loadThumbnail(uri, DEFAULT_SIZE, null);
        } catch (IOException e) {
            Log.e(LOGTAG, "  loadThumbnail: " + e.getMessage());
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
        Log.e(LOGTAG, "  onPostExecute : " + bitmap);

        if (bitmap != null && mOnSuccessDelegate != null) {
            (new Handler(Looper.getMainLooper())).post(() -> mOnSuccessDelegate.onSuccess(bitmap));
        }
    }
}