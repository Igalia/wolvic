package com.igalia.wolvic;

import android.util.Log;

import com.igalia.wolvic.utils.SystemUtils;

public class PlatformSystemCheck extends SystemCheck {
    private static final String PICO_OS_VERSION_PROPERTY = "ro.build.display.id";
    private static final int MIN_PICO_OS_MAJOR_VERSION = 5;
    private static final int MIN_PICO_OS_MINOR_VERSION = 7;
    private static final int MIN_PICO_OS_PATCH_VERSION = 1;
    private static final String LOGTAG = SystemUtils.createLogtag(PlatformSystemCheck.class);

    /**
     * Compares two version strings in the form x.y.z where x, y, and z are integers.
     * @return a negative integer, zero, or a positive integer as the first version
     *         is less than, equal to, or greater than the second version.
     */
    private int compare(String str1, String str2) {
        String[] parts1 = str1.split("\\.");
        String[] parts2 = str2.split("\\.");

        for (int i = 0; i < Math.min(parts1.length, parts2.length); i++) {
            int num1 = Integer.parseInt(parts1[i]);
            int num2 = Integer.parseInt(parts2[i]);

            if (num1 != num2)
                return num1 - num2;
        }
        return parts1.length - parts2.length;
    }

    @Override
    public boolean isOSVersionCompatible() {
        String osVersion = getSystemProperty(PICO_OS_VERSION_PROPERTY);
        Log.i(LOGTAG, "Checking that OS version is at least " + minSupportedVersion() + " (found " + osVersion + ")");
        return compare(osVersion, minSupportedVersion()) >= 0;
    }

    @Override
    public String minSupportedVersion() {
        return MIN_PICO_OS_MAJOR_VERSION + "." + MIN_PICO_OS_MINOR_VERSION + "." + MIN_PICO_OS_PATCH_VERSION;
    }
}
