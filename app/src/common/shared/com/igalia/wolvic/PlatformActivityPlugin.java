package com.igalia.wolvic;

import java.util.ArrayList;
import java.util.List;

public abstract class PlatformActivityPlugin {
    public interface PlatformActivityPluginListener {
        void onPlatformScrollEvent(float distanceX, float distanceY);
    }
    abstract void onVideoAvailabilityChange();
    void registerListener(PlatformActivityPluginListener listener) {
        if (mListeners == null)
            mListeners = new ArrayList<>();
        mListeners.add(listener);
    }
    void unregisterListener(PlatformActivityPluginListener listener) {
        mListeners.remove(listener);
    }
    void notifyOnScrollEvent(float distanceX, float distanceY) {
        for (PlatformActivityPluginListener listener : mListeners)
            listener.onPlatformScrollEvent(distanceX, distanceY);
    }
    private List<PlatformActivityPluginListener> mListeners;
}
