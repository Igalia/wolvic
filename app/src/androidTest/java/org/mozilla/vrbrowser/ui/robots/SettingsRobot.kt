/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

@file:Suppress("TooManyFunctions")

package org.mozilla.vrbrowser.ui.robots

import android.widget.ImageButton
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Test
import org.mozilla.vrbrowser.R
import org.mozilla.vrbrowser.VRBrowserActivity
import org.mozilla.vrbrowser.helpers.getActivity
import org.mozilla.vrbrowser.ui.FxRTransition

/**
 * Implementation of Robot Pattern for the Settings panel.
 */
class SettingRobot {

    fun verifySettingsVisible() = assertSettingsVisible()

    class Transition : FxRTransition() {

        @Test
        fun clickWorld(interact: TrayRobot.() -> Unit): TrayRobot.Transition {
            rootWidget().apply {
                activity.runOnUiThread {
                    performClick()
                }
            }

            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            TrayRobot().interact()
            return TrayRobot.Transition()
        }

        @Test
        fun clickBack(interact: TrayRobot.() -> Unit): TrayRobot.Transition {
            backButton().apply {
                activity.runOnUiThread {
                    performClick()
                }
            }

            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            TrayRobot().interact()
            return TrayRobot.Transition()
        }

        @Test
        fun clickDeviceBack(interact: TrayRobot.() -> Unit): TrayRobot.Transition {
            device.pressBack()

            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            TrayRobot().interact()
            return TrayRobot.Transition()
        }

    }
}

private fun assertSettingsVisible() {
    Assert.assertTrue(settingsWidget().isVisible)
}

private fun backButton() = settingsWidget().findViewById<ImageButton>(R.id.backButton)
private fun settingsWidget() = (getActivity() as VRBrowserActivity).tray.settingsWidget
private fun rootWidget() = (getActivity() as VRBrowserActivity).rootWidget
