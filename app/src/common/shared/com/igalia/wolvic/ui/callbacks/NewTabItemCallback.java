package com.igalia.wolvic.ui.callbacks;

import android.view.View;

import androidx.annotation.NonNull;

public interface NewTabItemCallback {
    void onClick(@NonNull View view, @NonNull String name, @NonNull String url);

    void onDelete(@NonNull View view, @NonNull String name, @NonNull String url);
}
