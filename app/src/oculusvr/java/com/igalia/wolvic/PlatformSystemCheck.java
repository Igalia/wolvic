package com.igalia.wolvic;

import android.util.Log;

import com.igalia.wolvic.utils.SystemUtils;

public class PlatformSystemCheck extends SystemCheck {
    private static final String META_OS_VERSION = "ro.vros.build.version";
    private static final int MIN_META_OS_VERSION_WITH_KHR_LOADER = 62;
    private static final String LOGTAG = SystemUtils.createLogtag(PlatformSystemCheck.class);

    @Override
    public boolean isOSVersionCompatible() {
        String osVersion = getSystemProperty(META_OS_VERSION);
        Log.i(LOGTAG, "Checking that OS version is at least " + MIN_META_OS_VERSION_WITH_KHR_LOADER + " (found " + osVersion + ")");
        try {
            if (osVersion == null || Integer.parseInt(osVersion) < MIN_META_OS_VERSION_WITH_KHR_LOADER)
                return false;
        } catch (NumberFormatException e) {
            Log.e(LOGTAG, "Failed to parse OS version: " + osVersion);
            return false;
        }
        return true;
    }

    @Override
    public String minSupportedVersion() {
        return String.valueOf(MIN_META_OS_VERSION_WITH_KHR_LOADER);
    }
}
