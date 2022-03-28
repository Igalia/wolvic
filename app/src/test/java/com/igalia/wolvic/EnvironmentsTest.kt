package com.igalia.wolvic

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.igalia.wolvic.browser.SettingsStore
import com.igalia.wolvic.utils.EnvironmentUtils
import com.igalia.wolvic.utils.TestFileUtils
import mozilla.components.service.glean.testing.GleanTestRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File


@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = TestApplication::class)
class EnvironmentsTest {

    @get:Rule
    val gleanRule = GleanTestRule(ApplicationProvider.getApplicationContext())
    lateinit var settingStore: SettingsStore
    private lateinit var context: Context

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<TestApplication>()
        settingStore = SettingsStore.getInstance(app)
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testNullRemoteProperties() {
        settingStore.setRemoteProperties(null)
        assertNull(settingStore.remoteProperties)
    }

    @Test
    fun testNotNullRemoteProperties() {
        settingStore.setRemoteProperties(TestFileUtils.readTextFile(javaClass.classLoader!!, "environments/testNoEnvs.json"))
        assertNotNull(settingStore.remoteProperties)
    }

    @Test
    fun `Environments for a target version`() {
        settingStore.setRemoteProperties(TestFileUtils.readTextFile(javaClass.classLoader!!,"environments/targetVersionEnvs.json"))
        val env = EnvironmentUtils.getExternalEnvironments(context, "11")
        assertNotNull(env)
        assertEquals(env.size, 2)
    }

    @Test
    fun `Environments do not exist for a target version, we fallback to the most recent ones`() {
        settingStore.setRemoteProperties(TestFileUtils.readTextFile(javaClass.classLoader!!,"environments/previousVersionEnvs.json"))
        val env = EnvironmentUtils.getExternalEnvironments(context, "12")
        assertNotNull(env)
        assertEquals(env.size, 2)
    }

    @Test
    fun `Environment exist for the target version`() {
        settingStore.setRemoteProperties(TestFileUtils.readTextFile(javaClass.classLoader!!,"environments/targetVersionEnvs.json"))
        val env = EnvironmentUtils.getExternalEnvironmentById(context, "space", "11")
        assertNotNull(env)
        assertEquals(env?.value, "space")
        assertEquals(env?.title, "Space")
        assertEquals(env?.thumbnail, "https://mixedreality.mozilla.org/FirefoxReality/envs/space/thumbnail.jpg")
        assertEquals(env?.payload, "https://mixedreality.mozilla.org/FirefoxReality/envs/space/space.zip")
    }

    @Test
    fun `Environment does not exist for the target version, we fallback to the most recent one`() {
        settingStore.setRemoteProperties(TestFileUtils.readTextFile(javaClass.classLoader!!,"environments/previousVersionEnvs.json"))
        val env = EnvironmentUtils.getExternalEnvironmentById(context, "space", "12")
        assertNotNull(env)
        assertEquals(env?.value, "space")
        assertEquals(env?.title, "Space")
        assertEquals(env?.thumbnail, "https://mixedreality.mozilla.org/FirefoxReality/envs/space/thumbnail.jpg")
        assertEquals(env?.payload, "https://mixedreality.mozilla.org/FirefoxReality/envs/space/space.zip")
    }

    @Test
    fun `Environment does not exist for any version`() {
        settingStore.setRemoteProperties(TestFileUtils.readTextFile(javaClass.classLoader!!,"environments/testNoEnvs.json"))
        val env = EnvironmentUtils.getExternalEnvironmentById(context, "test", "12")
        assertNull(env)
    }

    @Test
    fun `Environment by payload url`() {
        settingStore.setRemoteProperties(TestFileUtils.readTextFile(javaClass.classLoader!!,"environments/targetVersionEnvs.json"))
        val env = EnvironmentUtils.getExternalEnvironmentByPayload(context, "https://mixedreality.mozilla.org/FirefoxReality/envs/space/space.zip", "11")
        assertNotNull(env)
        assertEquals(env?.value, "space")
        assertEquals(env?.title, "Space")
        assertEquals(env?.thumbnail, "https://mixedreality.mozilla.org/FirefoxReality/envs/space/thumbnail.jpg")
        assertEquals(env?.payload, "https://mixedreality.mozilla.org/FirefoxReality/envs/space/space.zip")
    }

    @Test
    fun `Environment is builtin`() {
        assertTrue(EnvironmentUtils.isBuiltinEnvironment(context, "void"))
        assertTrue(EnvironmentUtils.isBuiltinEnvironment(context, "offworld"))
    }

    @Test
    fun `Environment is external`() {
        settingStore.setRemoteProperties(TestFileUtils.readTextFile(javaClass.classLoader!!,"environments/targetVersionEnvs.json"))
        val isExternal = EnvironmentUtils.isExternalEnvironment(context, "space", "11")
        assertTrue(isExternal)
    }

    @Test
    fun `Environment is not external`() {
        settingStore.setRemoteProperties(TestFileUtils.readTextFile(javaClass.classLoader!!,"environments/testNoEnvs.json"))
        val isExternal = EnvironmentUtils.isExternalEnvironment(context, "space", "11")
        assertFalse(isExternal)
    }

    @Test
    fun `External environment path`() {
        var path = context.getExternalFilesDir(EnvironmentUtils.ENVS_FOLDER)
        assertNotNull(path)
        path = File(path, "space")
        assertNotNull(path)

        val actualPath = EnvironmentUtils.getExternalEnvPath(context, "space")
        assertNotNull(actualPath)

        assertEquals(actualPath, path.absolutePath)
    }

    @Test
    fun `External environment is not ready`() {
        val isReady = EnvironmentUtils.isExternalEnvReady(context, "space")
        assertFalse(isReady)
    }

    @Test
    fun `External environment is ready`() {
        val actualPath = EnvironmentUtils.getExternalEnvPath(context, "space")
        assertNotNull(actualPath)

        // We just check that there are 6 files in the directory to determine that the env is ready
        // so we just create 6 random files.
        val dir = File(actualPath!!)
        assertNotNull(dir)
        dir.mkdirs()
        for (x in 0..5) {
            val file = File(dir, "image_$x")
            assertTrue(file.createNewFile())
        }

        val isReady = EnvironmentUtils.isExternalEnvReady(context, "space")
        assertTrue(isReady)
    }

}