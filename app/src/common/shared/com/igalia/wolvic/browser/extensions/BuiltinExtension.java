/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.browser.extensions;

import android.util.Log;

import androidx.annotation.NonNull;

import com.igalia.wolvic.utils.SystemUtils;

import mozilla.components.concept.engine.webextension.InstallationMethod;
import mozilla.components.concept.engine.webextension.WebExtensionRuntime;

/**
 * Feature to enable a Builtin extension via the Web Compatibility System-Addon.
 */
public class BuiltinExtension {

    private static final String LOGTAG = SystemUtils.createLogtag(BuiltinExtension.class);

    /**
     * Installs the web extension in the runtime through the WebExtensionRuntime install method
     */
    public static void install(@NonNull WebExtensionRuntime runtime, @NonNull String extensionId, @NonNull String extensionUrl) {
        runtime.installBuiltInWebExtension(extensionId, extensionUrl, webExtension -> {
            Log.i(LOGTAG, extensionId + " Web Extension successfully installed");
            return null;
        }, (throwable) -> {
            Log.e(LOGTAG, "Error installing the " + extensionId + " Web Extension: " + throwable.getLocalizedMessage());
            return null;
        });
    }
}
