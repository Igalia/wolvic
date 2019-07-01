package org.mozilla.vrbrowser.browser;

public interface VideoAvailabilityListener {
    default void onVideoAvailabilityChanged(boolean aVideosAvailable) {};
}
