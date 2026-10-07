package com.igalia.wolvic.browser.persistence;

import com.igalia.wolvic.ui.widgets.Windows;

public class WindowState {
    public Windows.WindowPlacement placement;
    public int textureWidth;
    public int textureHeight;
    public float worldWidth;
    public int tabIndex = -1;

    // NOTE: Enum values may be null when deserialized by GSON.
    public Windows.ContentType contentType = Windows.ContentType.WEB_CONTENT;
}
