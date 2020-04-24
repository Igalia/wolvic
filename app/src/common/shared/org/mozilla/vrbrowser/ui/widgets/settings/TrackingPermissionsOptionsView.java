/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.settings;

import android.content.Context;

import org.mozilla.vrbrowser.browser.content.TrackingProtectionStore;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;

import static org.mozilla.vrbrowser.db.SitePermission.SITE_PERMISSION_TRACKING;

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
