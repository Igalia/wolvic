/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

@file:Suppress("TooManyFunctions")

package org.mozilla.vrbrowser.ui.robots

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Test
import org.mozilla.vrbrowser.R
import org.mozilla.vrbrowser.VRBrowserActivity
import org.mozilla.vrbrowser.helpers.getActivity
import org.mozilla.vrbrowser.ui.FxRTransition
import org.mozilla.vrbrowser.ui.views.UIButton


/**
 * Implementation of Robot Pattern for the Tray panel
 */
class TrayRobot {

    fun verifyTrayVisible() = assertTrayVisible()
    fun verifySettingsInvisible() = assertSettingsInvisible()
    fun verifyNumberOfWindows(number : Int) = assertNumberOfWindows(number)

    class Transition : FxRTransition() {

        @Test
        fun openSettings(interact: SettingRobot.() -> Unit): SettingRobot.Transition {
            settingsButton().apply {
                activity.runOnUiThread {
                    performClick()
                }
            }

            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            SettingRobot().interact()
            return SettingRobot.Transition()
        }

        @Test
        fun openNewWindow(interact: TrayRobot.() -> Unit): TrayRobot.Transition {
            newWindowButton().apply {
                activity.runOnUiThread {
                    performClick()
                }
            }

            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            TrayRobot().interact()
            return TrayRobot.Transition()
        }
    }
}

private fun assertTrayVisible() {
    Assert.assertTrue(tray().isVisible)
}

private fun assertSettingsInvisible() {
    Assert.assertFalse(settingsWidget().isVisible)
}

private fun assertNumberOfWindows(number : Int) {
    Assert.assertEquals(number, windowManager().windowsCount)
}

private fun tray() = (getActivity() as VRBrowserActivity).tray
private fun windowManager() = (getActivity() as VRBrowserActivity).windows
private fun settingsButton() = (getActivity() as VRBrowserActivity).tray.findViewById<UIButton>(R.id.settingsButton)
private fun newWindowButton() = (getActivity() as VRBrowserActivity).tray.findViewById<UIButton>(R.id.addwindowButton)
private fun settingsWidget() = (getActivity() as VRBrowserActivity).tray.settingsWidget

fun tray(interact: TrayRobot.() -> Unit): TrayRobot.Transition {
    TrayRobot().interact()
    return TrayRobot.Transition()
}
