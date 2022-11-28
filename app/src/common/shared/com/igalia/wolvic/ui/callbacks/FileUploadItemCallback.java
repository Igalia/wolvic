package com.igalia.wolvic.ui.callbacks;

import android.view.View;

import androidx.annotation.NonNull;

import com.igalia.wolvic.ui.adapters.FileUploaditem;

public interface FileUploadItemCallback {
    void onClick(@NonNull View view, @NonNull FileUploaditem item);
}
