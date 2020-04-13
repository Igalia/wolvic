package org.mozilla.vrbrowser.ui.callbacks;

import android.view.View;

import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.downloads.Download;
import org.mozilla.vrbrowser.ui.adapters.Bookmark;

public interface DownloadItemCallback {
    void onClick(@NonNull View view, @NonNull Download item);
    void onDelete(@NonNull View view, @NonNull Download item);
    void onMore(@NonNull View view, @NonNull Download item);
}
