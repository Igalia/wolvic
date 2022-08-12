package com.igalia.wolvic;

import android.content.Intent;

import androidx.annotation.NonNull;

/**
 * Methods that will be implemented by the platform-specific activities.
 */
public interface PlatformSpecificBehavior {

    default boolean shouldOpenInKioskMode(@NonNull Intent intent) {
        return false;
    }
}
