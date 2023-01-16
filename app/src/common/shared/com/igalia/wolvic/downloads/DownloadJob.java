package com.igalia.wolvic.downloads;

import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WSession;

import java.io.InputStream;
import java.util.Map;

public class DownloadJob {

    private String mUri;
    private String mContentType;
    private long mContentLength;
    private String mFilename;
    private String mTitle;
    private String mDescription;
    private String mOutputPath;
    private InputStream inputStream;

    public static DownloadJob create(@NonNull String uri) {
        DownloadJob job = new DownloadJob();
        job.mUri = uri;
        job.mContentType = null;
        job.mContentLength = 0;
        job.mFilename = URLUtil.guessFileName(uri, null, null);
        job.mTitle = job.mFilename;
        job.mDescription = job.mFilename;
        job.mOutputPath = null;
        return job;
    }

    public static DownloadJob fromUri(@NonNull String uri, Map<String, String> headers) {
        DownloadJob job = new DownloadJob();
        job.mUri = uri;
        job.mContentLength = 0;
        if (headers != null) {
            job.mFilename = URLUtil.guessFileName(uri, headers.get("content-disposition"), headers.get("content/type"));
        } else {
            job.mFilename = URLUtil.guessFileName(uri, null, null);
        }
        job.mTitle = job.mFilename;
        job.mDescription = job.mFilename;
        return job;
    }

    public static DownloadJob fromUri(@NonNull String uri, Map<String, String> headers, InputStream inputStream) {
        DownloadJob job = fromUri(uri, headers);
        job.inputStream = inputStream;
        return job;
    }

    public static DownloadJob fromSrc(@NonNull WSession.ContentDelegate.ContextElement contextElement) {
        DownloadJob job = new DownloadJob();
        job.mUri = contextElement.srcUri;
        switch (contextElement.type) {
            case WSession.ContentDelegate.ContextElement.TYPE_NONE:
                job.mContentType = "";
                break;
            case WSession.ContentDelegate.ContextElement.TYPE_AUDIO:
                job.mContentType = "audio";
                break;
            case WSession.ContentDelegate.ContextElement.TYPE_IMAGE:
                job.mContentType = "image";
                break;
            case WSession.ContentDelegate.ContextElement.TYPE_VIDEO:
                job.mContentType = "video";
                break;
        }
        job.mFilename = URLUtil.guessFileName(job.mUri, null, null);
        job.mContentLength = 0;
        job.mTitle = job.mFilename;
        job.mDescription = job.mFilename;
        return job;
    }

    public static DownloadJob fromLink(@NonNull WSession.ContentDelegate.ContextElement contextElement) {
        DownloadJob job = new DownloadJob();
        job.mUri = contextElement.linkUri;
        switch (contextElement.type) {
            case WSession.ContentDelegate.ContextElement.TYPE_NONE:
                job.mContentType = "";
                break;
            case WSession.ContentDelegate.ContextElement.TYPE_AUDIO:
                job.mContentType = "audio";
                break;
            case WSession.ContentDelegate.ContextElement.TYPE_IMAGE:
                job.mContentType = "image";
                break;
            case WSession.ContentDelegate.ContextElement.TYPE_VIDEO:
                job.mContentType = "video";
                break;
        }
        job.mFilename = URLUtil.guessFileName(job.mUri, null, null);
        job.mContentLength = 0;
        job.mTitle = job.mFilename;
        job.mDescription = job.mFilename;
        return job;
    }

    @NonNull
    public String getUri() {
        return mUri;
    }

    @Nullable
    public String getContentType() {
        return mContentType;
    }

    public long getContentLength() {
        return mContentLength;
    }

    @NonNull
    public String getFilename() {
        return mFilename;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String mTitle) {
        this.mTitle = mTitle;
    }

    public String getDescription() {
        return mDescription;
    }

    public void setDescription(String mDescription) {
        this.mDescription = mDescription;
    }

    @Nullable
    public String getOutputPath() {
        return mOutputPath;
    }

    @Nullable
    public InputStream getInputStream() {
        return inputStream;
    }
}
