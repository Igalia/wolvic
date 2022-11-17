package com.igalia.wolvic.ui.callbacks;

import android.view.View;

import androidx.annotation.NonNull;

import com.igalia.wolvic.ui.adapters.WebApp;

public interface WebAppItemCallback {
    void onClick(@NonNull View view, @NonNull WebApp item);

    void onDelete(@NonNull View view, @NonNull WebApp item);
}
