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
        val env = EnvironmentUtils.getExternalEnvironments(context, "1")
        assertNotNull(env)
        assertEquals(env.size, 2)
    }

    @Test
    fun `Environments do not exist for a target version, we fallback to the most recent ones`() {
        settingStore.setRemoteProperties(TestFileUtils.readTextFile(javaClass.classLoader!!,"environments/previousVersionEnvs.json"))
        val env = EnvironmentUtils.getExternalEnvironments(context, "2")
        assertNotNull(env)
        assertEquals(env.size, 2)
    }

    @Test
    fun `Environment exist for the target version`() {
        settingStore.setRemoteProperties(TestFileUtils.readTextFile(javaClass.classLoader!!,"environments/targetVersionEnvs.json"))
        val env = EnvironmentUtils.getExternalEnvironmentById(context, "wolvic", "1")
        assertNotNull(env)
        assertEquals(env?.value, "wolvic")
        assertEquals(env?.title, "Wolvic")
        assertEquals(env?.thumbnail, "https://mixedreality.mozilla.org/FirefoxReality/envs/wolvic/thumbnail.jpg")
        assertEquals(env?.payload, "https://mixedreality.mozilla.org/FirefoxReality/envs/wolvic/space.zip")
    }

    @Test
    fun `Environment does not exist for the target version, we fallback to the most recent one`() {
        settingStore.setRemoteProperties(TestFileUtils.readTextFile(javaClass.classLoader!!,"environments/previousVersionEnvs.json"))
        val env = EnvironmentUtils.getExternalEnvironmentById(context, "wolvic", "2")
        assertNotNull(env)
        assertEquals(env?.value, "wolvic")
        assertEquals(env?.title, "Wolvic")
        assertEquals(env?.thumbnail, "https://mixedreality.mozilla.org/FirefoxReality/envs/wolvic/thumbnail.jpg")
        assertEquals(env?.payload, "https://mixedreality.mozilla.org/FirefoxReality/envs/wolvic/space.zip")
    }

    @Test
    fun `Environment does not exist for any version`() {
        settingStore.setRemoteProperties(TestFileUtils.readTextFile(javaClass.classLoader!!,"environments/testNoEnvs.json"))
        val env = EnvironmentUtils.getExternalEnvironmentById(context, "test", "2")
        assertNull(env)
    }

    @Test
    fun `Environment by payload url`() {
        settingStore.setRemoteProperties(TestFileUtils.readTextFile(javaClass.classLoader!!,"environments/targetVersionEnvs.json"))
        val env = EnvironmentUtils.getExternalEnvironmentByPayload(context, "https://mixedreality.mozilla.org/FirefoxReality/envs/wolvic/space.zip", "11")
        assertNotNull(env)
        assertEquals(env?.value, "wolvic")
        assertEquals(env?.title, "Wolvic")
        assertEquals(env?.thumbnail, "https://mixedreality.mozilla.org/FirefoxReality/envs/wolvic/thumbnail.jpg")
        assertEquals(env?.payload, "https://mixedreality.mozilla.org/FirefoxReality/envs/wolvic/space.zip")
    }

    @Test
    fun `Environment is builtin`() {
        assertTrue(EnvironmentUtils.isBuiltinEnvironment(context, "void"))
        assertTrue(EnvironmentUtils.isBuiltinEnvironment(context, "wolvic"))
        assertTrue(EnvironmentUtils.isBuiltinEnvironment(context, "cyberpunk"))
    }

    @Test
    fun `Environment is external`() {
        settingStore.setRemoteProperties(TestFileUtils.readTextFile(javaClass.classLoader!!,"environments/targetVersionEnvs.json"))
        val isExternal = EnvironmentUtils.isExternalEnvironment(context, "wolvic", "1")
        assertTrue(isExternal)
    }

    @Test
    fun `Environment is not external`() {
        settingStore.setRemoteProperties(TestFileUtils.readTextFile(javaClass.classLoader!!,"environments/testNoEnvs.json"))
        val isExternal = EnvironmentUtils.isExternalEnvironment(context, "wolvic", "1")
        assertFalse(isExternal)
    }

    @Test
    fun `External environment path`() {
        val cacheDir = context.cacheDir.absolutePath
        assertNotNull(cacheDir)

        val path = File(cacheDir, EnvironmentUtils.ENVS_FOLDER + "/wolvic")
        assertNotNull(path)

        val actualPath = EnvironmentUtils.getExternalEnvPath(context, "wolvic")
        assertNotNull(actualPath)

        assertEquals(path.absolutePath, actualPath)
    }

    @Test
    fun `External environment is not ready`() {
        val isReady = EnvironmentUtils.isExternalEnvReady(context, "wolvic")
        assertFalse(isReady)
    }

    @Test
    fun `External environment is ready`() {
        val actualPath = EnvironmentUtils.getExternalEnvPath(context, "wolvic")
        assertNotNull(actualPath)

        val dir = File(actualPath!!)
        assertNotNull(dir)
        dir.mkdirs()

        val fileNames = listOf("negx.png", "negy.png", "negz.png", "posx.png", "posy.png", "posz.png")
        for (fileName in fileNames) {
            val file = File(dir, fileName)
            assertTrue(file.createNewFile())
        }

        val isReady = EnvironmentUtils.isExternalEnvReady(context, "wolvic")
        assertTrue(isReady)
    }

}