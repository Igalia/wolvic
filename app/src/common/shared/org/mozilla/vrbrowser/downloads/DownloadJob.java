package org.mozilla.vrbrowser.downloads;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement;
import org.mozilla.geckoview.GeckoSession.WebResponseInfo;

import java.io.File;
import java.net.URL;

public class DownloadJob {

    private String mUri;
    private String mContentType;
    private long mContentLength;
    private String mFilename;
    private String mTitle;
    private String mDescription;

    public static DownloadJob create(@NonNull String uri, @Nullable String contentType,
                                     long contentLength, @Nullable String filename) {
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
        return job;
    }

    public static DownloadJob from(@NonNull WebResponseInfo response) {
        DownloadJob job = new DownloadJob();
        job.mUri = response.uri;
        job.mContentType = response.contentType;
        job.mContentLength = response.contentLength;
        if (response.filename != null && !response.filename.isEmpty()) {
            job.mFilename = response.filename;

        } else {
            try {
                File f = new File(new URL(response.uri).getPath());
                job.mFilename = f.getName();

            } catch (Exception e) {
                job.mFilename = "Unknown";
            }
        }
        job.mTitle = response.filename;
        job.mDescription = response.filename;
        return job;
    }

    public static DownloadJob fromSrc(@NonNull ContextElement contextElement) {
        DownloadJob job = new DownloadJob();
        job.mUri = contextElement.srcUri;
        switch (contextElement.type) {
            case ContextElement.TYPE_NONE:
                job.mContentType = "";
                break;
            case ContextElement.TYPE_AUDIO:
                job.mContentType = "audio";
                break;
            case ContextElement.TYPE_IMAGE:
                job.mContentType = "image";
                break;
            case ContextElement.TYPE_VIDEO:
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

    public static DownloadJob fromLink(@NonNull ContextElement contextElement) {
        DownloadJob job = new DownloadJob();
        job.mUri = contextElement.linkUri;
        switch (contextElement.type) {
            case ContextElement.TYPE_NONE:
                job.mContentType = "";
                break;
            case ContextElement.TYPE_AUDIO:
                job.mContentType = "audio";
                break;
            case ContextElement.TYPE_IMAGE:
                job.mContentType = "image";
                break;
            case ContextElement.TYPE_VIDEO:
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
}
