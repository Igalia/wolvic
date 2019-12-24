package org.mozilla.vrbrowser.ui

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.mozilla.vrbrowser.VRBrowserActivity
import org.mozilla.vrbrowser.helpers.HomeActivityTestRule
import org.mozilla.vrbrowser.helpers.getActivity

open class FxRTransition {

    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @get:Rule
    val activityTestRule = HomeActivityTestRule()

    val activity by lazy {
        getActivity() as VRBrowserActivity
    }
}