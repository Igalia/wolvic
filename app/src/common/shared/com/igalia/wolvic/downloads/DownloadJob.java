package com.igalia.wolvic.downloads;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WSession;

import java.io.File;
import java.net.URL;

public class DownloadJob {

    private String mUri;
    private String mContentType;
    private long mContentLength;
    private String mFilename;
    private String mTitle;
    private String mDescription;
    private String mOutputPath;

    public static DownloadJob create(@NonNull String uri) {
        return create(uri, null, 0, null, null);
    }

    public static DownloadJob create(@NonNull String uri, @Nullable String contentType,
                                     long contentLength, @Nullable String filename) {
        return create(uri, contentType, contentLength, filename, null);
    }

    public static DownloadJob create(@NonNull String uri, @Nullable String contentType,
                                     long contentLength, @Nullable String filename,
                                     @Nullable String outputPath) {
        DownloadJob job = new DownloadJob();
        job.mUri = uri;
        job.mContentType = contentType;
        job.mContentLength = contentLength;
        if (filename != null) {
            job.mFilename = filename;
        } else {
            try {
                File f = new File(new URL(uri).getPath());
                job.mFilename = f.getName();

            } catch (Exception e) {
                job.mFilename = "Untitled";
            }
        }
        job.mTitle = filename;
        job.mDescription = filename;
        job.mOutputPath = outputPath;
        return job;
    }

    public static DownloadJob fromUri(@NonNull String uri) {
        DownloadJob job = new DownloadJob();
        job.mUri = uri;
        job.mContentLength = 0;
        try {
            File f = new File(new URL(uri).getPath());
            job.mFilename = f.getName();

        } catch (Exception e) {
            job.mFilename = "Download";
        }
        job.mTitle = job.mFilename;
        job.mDescription = job.mFilename;
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
        try {
            File f = new File(new URL(contextElement.srcUri).getPath());
            job.mFilename = f.getName();

        } catch (Exception e) {
            job.mFilename = "Unknown";
        }
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
        try {
            File f = new File(new URL(contextElement.linkUri).getPath());
            job.mFilename = f.getName();

        } catch (Exception e) {
            job.mFilename = "Unknown";
        }
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
}
