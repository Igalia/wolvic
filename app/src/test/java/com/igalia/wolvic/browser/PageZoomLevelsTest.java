package com.igalia.wolvic.browser;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PageZoomLevelsTest {

    @Test
    public void stepsThroughPresets() {
        assertEquals(110, PageZoomLevels.zoomIn(PageZoomLevels.DEFAULT_LEVEL));
        assertEquals(90, PageZoomLevels.zoomOut(PageZoomLevels.DEFAULT_LEVEL));
        assertEquals(150, PageZoomLevels.zoomIn(125));
        assertEquals(67, PageZoomLevels.zoomOut(75));
    }

    @Test
    public void clampsAtBounds() {
        assertEquals(500, PageZoomLevels.zoomIn(500));
        assertEquals(25, PageZoomLevels.zoomOut(25));
    }

    @Test
    public void movesFromNonPresetToNearestPresetInDirection() {
        assertEquals(110, PageZoomLevels.zoomIn(105));
        assertEquals(100, PageZoomLevels.zoomOut(105));
    }
}
