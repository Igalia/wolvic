/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

public class WidgetPlacement {
    int widgetType;
    int width;
    int height;
    float anchorX = 0.5f;
    float anchorY = 0.5f;
    float translationX;
    float translationY;
    float translationZ;
    int parentHandle;
    float parentAnchorX = 0.5f;
    float parentAnchorY = 0.5f;
}
