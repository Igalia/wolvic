/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

public class WidgetPlacement {
    public int widgetType;
    public int width;
    public int height;
    public float anchorX = 0.5f;
    public float anchorY = 0.5f;
    public float translationX;
    public float translationY;
    public float translationZ;
    public float rotationAxisX;
    public float rotationAxisY;
    public float rotationAxisZ;
    public float rotation;
    public int parentHandle;
    public float parentAnchorX = 0.5f;
    public float parentAnchorY = 0.5f;
    public float worldScale = 1.0f;
}
