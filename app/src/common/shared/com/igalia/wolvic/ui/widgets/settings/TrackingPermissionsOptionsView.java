/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets.settings;

import static com.igalia.wolvic.db.SitePermission.SITE_PERMISSION_TRACKING;

import android.content.Context;

import com.igalia.wolvic.browser.content.TrackingProtectionStore;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;

class TrackingPermissionsOptionsView extends SitePermissionsOptionsView {

    private TrackingProtectionStore mTrackingProtectionStore;

    public TrackingPermissionsOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
        super(aContext, aWidgetManager, SITE_PERMISSION_TRACKING);

        mTrackingProtectionStore = SessionStore.get().getTrackingProtectionStore();
    }

    protected void initialize(Context aContext) {
        mCallback = item -> mTrackingProtectionStore.remove(item);

        super.initialize(aContext);
    }

    @Override
    protected boolean reset() {
        mTrackingProtectionStore.removeAll();
        return true;
    }
}
