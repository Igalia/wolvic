/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

@file:Suppress("TooManyFunctions")

package org.mozilla.vrbrowser.ui.robots

import org.junit.Assert
import org.mozilla.vrbrowser.VRBrowserActivity
import org.mozilla.vrbrowser.helpers.getActivity
import org.mozilla.vrbrowser.ui.FxRTransition


/**
 * Implementation of Robot Pattern for the Home Screen
 */
class HomeScreenRobot {

    fun verifyNumberOfWindows(number : Int) = assertNumberOfWindows(number)

    class Transition : FxRTransition() {

    }
}

private fun assertNumberOfWindows(number : Int) {
    Assert.assertEquals(windowManager().windowsCount, number)
}

private fun windowManager() = (getActivity() as VRBrowserActivity).windows

fun homeScreen(interact: HomeScreenRobot.() -> Unit): HomeScreenRobot.Transition {
    HomeScreenRobot().interact()
    return HomeScreenRobot.Transition()
}
