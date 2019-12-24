/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.helpers

import androidx.test.rule.ActivityTestRule
import org.mozilla.vrbrowser.VRBrowserActivity

/**
 * A [org.junit.Rule] to handle shared test set up for tests on [VRBrowserActivity].
 *
 * @param initialTouchMode See [ActivityTestRule]
 * @param launchActivity See [ActivityTestRule]
 */

class HomeActivityTestRule(initialTouchMode: Boolean = false, launchActivity: Boolean = false) :
    ActivityTestRule<VRBrowserActivity>(VRBrowserActivity::class.java, initialTouchMode, launchActivity)
