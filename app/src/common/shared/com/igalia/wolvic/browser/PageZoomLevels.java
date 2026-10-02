package com.igalia.wolvic.browser;

/**
 * Discrete page zoom levels, in percent, copied from Chromium's kPresetZoomFactors
 * (components/zoom/page_zoom_constants.cc). Only the Gecko port uses them: the Chromium port
 * steps through the same presets natively in its own zoom code.
 */
public final class PageZoomLevels {

    public static final int DEFAULT_LEVEL = 100;

    private static final int[] LEVELS = { 25, 33, 50, 67, 75, 80, 90, 100, 110, 125, 150, 175, 200, 250, 300, 400, 500 };

    private PageZoomLevels() {}

    /** Returns the smallest preset above {@code level}, or the maximum if there is none. */
    public static int zoomIn(int level) {
        for (int preset : LEVELS) {
            if (preset > level) {
                return preset;
            }
        }
        return LEVELS[LEVELS.length - 1];
    }

    /** Returns the largest preset below {@code level}, or the minimum if there is none. */
    public static int zoomOut(int level) {
        for (int i = LEVELS.length - 1; i >= 0; i--) {
            if (LEVELS[i] < level) {
                return LEVELS[i];
            }
        }
        return LEVELS[0];
    }
}
