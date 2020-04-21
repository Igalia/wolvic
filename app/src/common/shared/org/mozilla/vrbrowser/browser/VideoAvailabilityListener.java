package org.mozilla.vrbrowser.browser;

import androidx.annotation.NonNull;

public interface VideoAvailabilityListener {
    default void onVideoAvailabilityChanged(@NonNull Media media, boolean aVideoAvailable) {}
}
