package org.mozilla.vrbrowser

import androidx.test.core.app.ApplicationProvider
import mozilla.components.concept.sync.DeviceType
import mozilla.components.service.glean.testing.GleanTestRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.vrbrowser.GleanMetrics.*
import org.mozilla.vrbrowser.telemetry.GleanMetricsService
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config


@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GleanMetricsServiceTest {

    @get:Rule
    val gleanRule = GleanTestRule(ApplicationProvider.getApplicationContext())

    @Test
    fun testURLTelemetry() {
        assertFalse(Url.domains.testHasValue())
        assertFalse(Url.visits.testHasValue())
        GleanMetricsService.stopPageLoadTimeWithURI("https://www.example.com/test.html");
        assertTrue(Url.domains.testHasValue())
        assertEquals(Url.domains.testGetValue(), 1)

        assertTrue(Url.visits.testHasValue())
        assertEquals(Url.visits.testGetValue(), 1)

        assertFalse(Url.queryType["type_link"].testHasValue())
        assertFalse(Url.queryType["type_query"].testHasValue())
        GleanMetricsService.urlBarEvent(true)
        assertTrue(Url.queryType["type_link"].testHasValue())
        assertEquals(Url.queryType["type_link"].testGetValue(), 1)
        GleanMetricsService.urlBarEvent(false)
        assertTrue(Url.queryType["type_query"].testHasValue())
        assertEquals(Url.queryType["type_query"].testGetValue(), 1)

        assertFalse(Url.queryType["voice_query"].testHasValue())
        GleanMetricsService.voiceInputEvent()
        assertTrue(Url.queryType["voice_query"].testHasValue())
        assertEquals(Url.queryType["voice_query"].testGetValue(), 1)
    }

    @Test
    fun testDistributionChannelTelemetry() {
        assertFalse(Distribution.channelName.testHasValue())
        GleanMetricsService.testSetStartupMetrics()
        assertTrue(Distribution.channelName.testHasValue())
        assertEquals(Distribution.channelName.testGetValue(), BuildConfig.FLAVOR_platform)
    }

    @Test
    fun testFxAAccountTelemetry() {
        assertFalse(FirefoxAccount.signIn.testHasValue())
        GleanMetricsService.FxA.signIn()
        assertTrue(FirefoxAccount.signIn.testHasValue())
        var events = FirefoxAccount.signIn.testGetValue()
        assertEquals(events.size, 1)

        assertFalse(FirefoxAccount.signInResult.testHasValue())
        GleanMetricsService.FxA.signInResult(false)
        assertTrue(FirefoxAccount.signInResult.testHasValue())
        events = FirefoxAccount.signInResult.testGetValue()
        assertEquals(events.size, 1)
        // We only expect 1 extra key.
        assertEquals(events[0].extra!!.size, 1)
        assertEquals(events[0].extra!!["state"], "false")

        GleanMetricsService.FxA.signInResult(true)
        events = FirefoxAccount.signInResult.testGetValue()
        assertEquals(events.size, 2)
        // We only expect 1 extra key.
        assertEquals(events[1].extra!!.size, 1)
        assertEquals(events[1].extra!!["state"], "true")

        assertFalse(FirefoxAccount.signOut.testHasValue())
        GleanMetricsService.FxA.signOut()
        assertTrue(FirefoxAccount.signOut.testHasValue())
        events = FirefoxAccount.signOut.testGetValue()
        assertEquals(events.size, 1)
    }

    @Test
    fun testFxABookmarkTelemetry() {
        assertFalse(FirefoxAccount.bookmarksSyncStatus.testHasValue())
        GleanMetricsService.FxA.bookmarksSyncStatus(false)
        assertTrue(FirefoxAccount.bookmarksSyncStatus.testHasValue())
        assertEquals(FirefoxAccount.bookmarksSyncStatus.testGetValue(), false)

        GleanMetricsService.FxA.bookmarksSyncStatus(true)
        assertEquals(FirefoxAccount.bookmarksSyncStatus.testGetValue(), true)
    }

    @Test
    fun testFxAHistoryTelemetry() {
        assertFalse(FirefoxAccount.historySyncStatus.testHasValue())
        GleanMetricsService.FxA.historySyncStatus(false)
        assertTrue(FirefoxAccount.historySyncStatus.testHasValue())
        assertEquals(FirefoxAccount.historySyncStatus.testGetValue(), false)

        GleanMetricsService.FxA.historySyncStatus(true)
        assertEquals(FirefoxAccount.historySyncStatus.testGetValue(), true)
    }

    @Test
    fun testFxATabTelemetry() {
        assertFalse(FirefoxAccount.tabSent.testHasValue())
        GleanMetricsService.FxA.sentTab()
        assertTrue(FirefoxAccount.tabSent.testHasValue())
        assertEquals(FirefoxAccount.tabSent.testGetValue(), 1)

        assertFalse(FirefoxAccount.receivedTab[DeviceType.MOBILE.name].testHasValue())
        GleanMetricsService.FxA.receivedTab(DeviceType.MOBILE)
        assertTrue(FirefoxAccount.receivedTab[DeviceType.MOBILE.name].testHasValue())
        assertEquals(FirefoxAccount.receivedTab[DeviceType.MOBILE.name].testGetValue(), 1)
    }

    @Test
    fun testTabTelemetry() {
        assertFalse(Tabs.opened[GleanMetricsService.Tabs.TabSource.BOOKMARKS.name].testHasValue())
        GleanMetricsService.Tabs.openedCounter(GleanMetricsService.Tabs.TabSource.BOOKMARKS)
        assertTrue(Tabs.opened[GleanMetricsService.Tabs.TabSource.BOOKMARKS.name].testHasValue())
        assertEquals(Tabs.opened[GleanMetricsService.Tabs.TabSource.BOOKMARKS.name].testGetValue(), 1)

        assertFalse(Tabs.activated.testHasValue())
        GleanMetricsService.Tabs.activatedEvent()
        assertTrue(Tabs.activated.testHasValue())
        assertEquals(Tabs.activated.testGetValue(), 1)
    }
}
