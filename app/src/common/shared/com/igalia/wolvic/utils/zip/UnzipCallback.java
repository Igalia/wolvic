package com.igalia.wolvic.utils.zip;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface UnzipCallback {
    void onUnzipStart(@NonNull String zipFile);
    void onUnzipProgress(@NonNull String zipFile, double progress);
    void onUnzipFinish(@NonNull String zipFile, @NonNull String outputPath);
    void onUnzipCancelled(@NonNull String zipFile);
    void onUnzipError(@NonNull String zipFile, @Nullable String error);
}
