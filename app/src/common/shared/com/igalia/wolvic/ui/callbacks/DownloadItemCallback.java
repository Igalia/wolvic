package com.igalia.wolvic.ui.callbacks;

import android.view.View;

import androidx.annotation.NonNull;

import com.igalia.wolvic.downloads.Download;

public interface DownloadItemCallback {
    void onClick(@NonNull View view, @NonNull Download item);
    void onDelete(@NonNull View view, @NonNull Download item);
    void onMore(@NonNull View view, @NonNull Download item);
}
