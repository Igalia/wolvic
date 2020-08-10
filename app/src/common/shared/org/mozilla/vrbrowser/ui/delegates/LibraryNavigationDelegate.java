package org.mozilla.vrbrowser.ui.delegates;

import android.view.View;

import androidx.annotation.NonNull;

public interface LibraryNavigationDelegate {
    default void onButtonClick(@NonNull View view) {}
    default void onClose(@NonNull View view) {}
    default void onBack(@NonNull View view) {}
}