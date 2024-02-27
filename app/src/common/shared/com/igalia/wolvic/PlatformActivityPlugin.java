package com.igalia.wolvic;

public interface PlatformActivityPlugin {
    void onKeyboardVisibilityChange(boolean isVisible);
    void onVideoAvailabilityChange();
}
