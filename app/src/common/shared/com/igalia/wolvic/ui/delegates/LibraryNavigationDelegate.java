package com.igalia.wolvic.ui.delegates;

import android.view.View;

import androidx.annotation.NonNull;

import com.igalia.wolvic.ui.widgets.Windows;

public interface LibraryNavigationDelegate {
    default void onButtonClick(Windows.ContentType contentType) {}
    default void onClose(@NonNull View view) {}
    default void onBack(@NonNull View view) {}
}