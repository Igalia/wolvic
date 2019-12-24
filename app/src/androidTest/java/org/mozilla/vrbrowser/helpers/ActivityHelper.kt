package org.mozilla.vrbrowser.helpers

import android.app.Activity
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.io.File


fun getActivity() : Activity {
    var currentActivity: Activity? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync { run { currentActivity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).elementAtOrNull(0) } }
    return currentActivity as Activity
}

fun clearAppFiles() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val files = context.filesDir.listFiles()
    if (files != null) {
        for (file in files) {
            file.delete()
        }
    }
}

fun clearPreferences() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val root = context.filesDir.parentFile
    val sharedPreferencesFileNames = File(root, "shared_prefs").list()
    for (fileName in sharedPreferencesFileNames) {
        context.getSharedPreferences(fileName.replace(".xml", ""), Context.MODE_PRIVATE).edit().clear().commit()
    }
}