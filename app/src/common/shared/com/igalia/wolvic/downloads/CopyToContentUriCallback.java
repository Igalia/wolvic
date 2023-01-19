package com.igalia.wolvic.downloads;

import android.net.Uri;

public interface CopyToContentUriCallback {
    void onSuccess(Uri uri, boolean isNewUri);

    void onFailure(Download download);
}
