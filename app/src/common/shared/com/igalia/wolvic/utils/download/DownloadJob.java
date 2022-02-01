package com.igalia.wolvic.utils.download;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.net.URL;

public class DownloadJob {

    private String mUri;
    private String mContentType;
    private String mOutputPath;
    private long mContentLength;
    private String mFilename;
    private String mTitle;
    private String mDescription;

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

    public String getOutputPath() {
        return mOutputPath;
    }
}
