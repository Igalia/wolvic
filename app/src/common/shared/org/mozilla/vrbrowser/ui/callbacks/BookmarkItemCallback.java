package org.mozilla.vrbrowser.ui.callbacks;

import android.view.View;

import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.ui.adapters.Bookmark;

public interface BookmarkItemCallback {
    void onClick(@NonNull View view, @NonNull Bookmark item);
    void onDelete(@NonNull View view, @NonNull Bookmark item);
    void onMore(@NonNull View view, @NonNull Bookmark item);
    void onFolderOpened(@NonNull Bookmark item);
}
