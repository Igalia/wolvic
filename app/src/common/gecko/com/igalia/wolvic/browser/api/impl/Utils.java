package com.igalia.wolvic.browser.api.impl;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WAllowOrDeny;
import com.igalia.wolvic.browser.api.WImage;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;

public class Utils {
    static WImage fromGeckoImage(final org.mozilla.geckoview.Image img) {
        return size -> new ResultImpl<>(img.getBitmap(size));
    }

    static @Nullable
    GeckoResult<AllowOrDeny> map(@Nullable GeckoResult<WAllowOrDeny> res) {
        if (res == null) {
            return null;
        }
        return res.map(value -> value == WAllowOrDeny.ALLOW ? AllowOrDeny.ALLOW : AllowOrDeny.DENY);
    }


}
