package com.igalia.wolvic.browser;

import androidx.annotation.NonNull;

public interface VideoAvailabilityListener {
    default void onVideoAvailabilityChanged(@NonNull Media media, boolean aVideoAvailable) {}
}
