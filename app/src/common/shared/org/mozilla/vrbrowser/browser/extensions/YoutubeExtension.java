package org.mozilla.vrbrowser.browser.extensions;

import android.util.Log;

import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.utils.SystemUtils;

import mozilla.components.concept.engine.webextension.WebExtensionRuntime;

/**
 * Feature to enable YouTube hotfixing via the Web Compatibility System-Addon.
 */
public class YoutubeExtension {

    private static final String LOGTAG = SystemUtils.createLogtag(YoutubeExtension.class);

    private static final String EXTENSION_ID = "fxr-webcompat_youtube@mozilla.org";
    private static final String EXTENSION_URL = "resource://android/assets/extensions/fxr_youtube/";

    /**
     * Installs the web extension in the runtime through the WebExtensionRuntime install method
     */
    public static void install(@NonNull WebExtensionRuntime runtime) {
        runtime.installWebExtension(EXTENSION_ID, EXTENSION_URL, false, false, webExtension -> {
            Log.i(LOGTAG, "Youtube Web Extension successfully installed");
            return null;
        }, (s, throwable) -> {
            Log.e(LOGTAG, "Error installing the Youtube Web Extension: " + throwable.getLocalizedMessage());
            return null;
        });
    }
}
