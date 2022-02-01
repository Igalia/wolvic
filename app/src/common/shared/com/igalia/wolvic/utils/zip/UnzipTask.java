package com.igalia.wolvic.utils.zip;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.NonNull;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.progress.ProgressMonitor;

import java.io.File;
import java.util.concurrent.Executors;

public class UnzipTask {

    private String mZipPath;
    private ProgressMonitor mUnzipMonitor;
    private UnzipResultReceiver mReceiver;
    private boolean mIsRunning;

    public UnzipTask(@NonNull Context context) {
        mReceiver = new UnzipResultReceiver(new Handler(context.getMainLooper()));
        mIsRunning = false;
    }

    public void addListener(@NonNull UnzipCallback listener) {
        mReceiver.addReceiver(listener);
    }

    public void removeListener(@NonNull UnzipCallback listener) {
        mReceiver.removeReceiver(listener);
    }

    public void start(@NonNull String zipPath, @NonNull String outputPath) {
        Executors.newSingleThreadExecutor().submit(() -> startUnzip(zipPath, outputPath));
    }

    private void startUnzip(@NonNull String zipPath, @NonNull String outputPath) {
        mZipPath = zipPath;

        // Remove previous unzip attempt
        File file = new File(outputPath);
        if (file.exists() && file.isDirectory()) {
            String[] children = file.list();
            for (String child : children) {
                new File(file, child).delete();
            }
        }

        // Start unzip
        try {
            notifyStarted();

            mIsRunning =  true;

            ZipFile zipFile = new ZipFile(mZipPath);
            zipFile.setRunInThread(true);
            zipFile.extractAll(outputPath);
            mUnzipMonitor = zipFile.getProgressMonitor();
            while (mUnzipMonitor.getState() == ProgressMonitor.STATE_BUSY) {
                double progress = zipFile.getProgressMonitor().getPercentDone();

                notifyProgress(progress);

                try {
                    Thread.sleep(500);

                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            if (!mUnzipMonitor.isCancelAllTasks()) {
                notifyFinish(mZipPath);

            } else {
                notifyCancelled();
            }

        } catch (Exception e) {
            notifyError(e.getLocalizedMessage());

        } finally {
            mIsRunning = false;
            mUnzipMonitor = null;
        }
    }

    public void cancel() {
        if (mUnzipMonitor != null) {
            mUnzipMonitor.cancelAllTasks();
        }
    }

    public boolean isIsRunning() {
        return mIsRunning;
    }

    private void notifyStarted() {
        Bundle bundle = new Bundle();
        bundle.putSerializable(UnzipResultReceiver.ZIP_PATH, mZipPath);
        mReceiver.send(UnzipResultReceiver.STARTED, bundle);
    }

    private void notifyProgress(double progress) {
        Bundle bundle = new Bundle();
        bundle.putSerializable(UnzipResultReceiver.ZIP_PATH, mZipPath);
        bundle.putSerializable(UnzipResultReceiver.ZIP_PROGRESS, progress);
        mReceiver.send(UnzipResultReceiver.PROGRESS, bundle);
    }

    private void notifyFinish(@NonNull String outputPath) {
        Bundle bundle = new Bundle();
        bundle.putSerializable(UnzipResultReceiver.ZIP_PATH, mZipPath);
        bundle.putSerializable(UnzipResultReceiver.ZIP_OUTPUT_PATH, outputPath);
        mReceiver.send(UnzipResultReceiver.FINISH, bundle);
    }

    private void notifyCancelled() {
        Bundle bundle = new Bundle();
        bundle.putSerializable(UnzipResultReceiver.ZIP_PATH, mZipPath);
        mReceiver.send(UnzipResultReceiver.CANCEL, bundle);
    }

    private void notifyError(@NonNull String error) {
        Bundle bundle = new Bundle();
        bundle.putSerializable(UnzipResultReceiver.ZIP_PATH, mZipPath);
        bundle.putSerializable(UnzipResultReceiver.ZIP_ERROR, error);
        mReceiver.send(UnzipResultReceiver.ERROR, bundle);
    }


}
