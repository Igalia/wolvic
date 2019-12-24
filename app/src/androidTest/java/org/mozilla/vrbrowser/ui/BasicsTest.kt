/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mozilla.vrbrowser.helpers.HomeActivityTestRule
import org.mozilla.vrbrowser.helpers.clearAppFiles
import org.mozilla.vrbrowser.ui.robots.tray
import tools.fastlane.screengrab.Screengrab

/**
 *  Basic UI tests
 */

class BasicsTest {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @get:Rule
    val activityTestRule = HomeActivityTestRule()

    @Before
    fun setUp() {
        clearAppFiles()

        activityTestRule.launchActivity(null)
    }

    @After
    fun tearDown() {
        clearAppFiles()
    }

    /**
     * Check that the settings panel is closed when clicking on the back button
     */
    @Test
    fun settingsCloseWithBackButton() {
        tray {
            verifyTrayVisible()

        }.openSettings {
            verifySettingsVisible()

        }.clickBack {
            verifySettingsInvisible()
        }
    }

    /**
     * Check that the settings panel is closed when clicking on the device back button
     */
    @Test
    fun settingsCloseWithDeviceBackButton() {
        tray {
            verifyTrayVisible()

        }.openSettings {
            verifySettingsVisible()

        }.clickDeviceBack {
            verifySettingsInvisible()
        }
    }

    /**
     * Check that the settings panel is closed when clicking outside the settings panel
     */
    @Test
    fun settingsCloseWithWorldClick() {
        tray {
            verifyTrayVisible()

        }.openSettings {
            verifySettingsVisible()

        }.clickBack {
            verifySettingsInvisible()
        }
    }

    /**
     * Check that we can't open more than 3 windows
     */
    @Test
    fun openWindowsLimit() {
        tray {
            verifyNumberOfWindows(1)
            Thread.sleep(1000) // Opening Windows too fast crashes the app

        }.openNewWindow {
            Thread.sleep(1000)

        }.openNewWindow {
            Thread.sleep(1000)

        }.openNewWindow {
            verifyNumberOfWindows(3)
        }
    }

    /**
     * Take a screenshot
     */
    @Test
    fun grabScreenshot() {
        tray {
            Thread.sleep(10000)
            Screengrab.screenshot("test")
        }
    }
}
