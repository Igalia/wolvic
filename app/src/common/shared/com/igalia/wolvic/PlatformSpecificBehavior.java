package com.igalia.wolvic;

import android.content.Intent;

/**
 * Methods that will be implemented by the platform-specific activities.
 */
public interface PlatformSpecificBehavior {

    boolean shouldOpenInKioskMode(Intent intent);
}
