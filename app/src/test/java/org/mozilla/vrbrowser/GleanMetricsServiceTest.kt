package org.mozilla.vrbrowser

import androidx.test.core.app.ApplicationProvider
import mozilla.components.concept.sync.DeviceType
import mozilla.components.lib.fetch.httpurlconnection.HttpURLConnectionClient
import mozilla.components.service.glean.testing.GleanTestRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.vrbrowser.GleanMetrics.*
import org.mozilla.vrbrowser.telemetry.GleanMetricsService
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config


@Ignore("Disabling until metrics are renewed")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = TestApplication::class)
class GleanMetricsServiceTest {

    @get:Rule
    val gleanRule = GleanTestRule(ApplicationProvider.getApplicationContext())

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<TestApplication>()
        // We use the HttpURLConnectionClient for tests as the GeckoWebExecutor based client needs
        // full GeckoRuntime initialization and it crashes in the test environment.
        val client = HttpURLConnectionClient()
        GleanMetricsService.init(app, client)
    }

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
        assertEquals(Distribution.channelName.testGetValue(),
                org.mozilla.vrbrowser.utils.DeviceType.getDeviceTypeId());

        // Make sure the distribution channel name is set after
        // the telemetry system is switch off/on.
        GleanMetricsService.stop();
        GleanMetricsService.start();
        assertTrue(Distribution.channelName.testHasValue())
        assertEquals(Distribution.channelName.testGetValue(),
                org.mozilla.vrbrowser.utils.DeviceType.getDeviceTypeId());
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

        assertFalse(FirefoxAccount.receivedTab[DeviceType.MOBILE.name.toLowerCase()].testHasValue())
        GleanMetricsService.FxA.receivedTab(DeviceType.MOBILE)
        assertTrue(FirefoxAccount.receivedTab[DeviceType.MOBILE.name.toLowerCase()].testHasValue())
        assertEquals(FirefoxAccount.receivedTab[DeviceType.MOBILE.name.toLowerCase()].testGetValue(), 1)
    }

    @Test
    fun testTabTelemetry() {
        assertFalse(Tabs.opened[GleanMetricsService.Tabs.TabSource.BOOKMARKS.name.toLowerCase()].testHasValue())
        GleanMetricsService.Tabs.openedCounter(GleanMetricsService.Tabs.TabSource.BOOKMARKS)
        assertTrue(Tabs.opened[GleanMetricsService.Tabs.TabSource.BOOKMARKS.name.toLowerCase()].testHasValue())
        assertEquals(Tabs.opened[GleanMetricsService.Tabs.TabSource.BOOKMARKS.name.toLowerCase()].testGetValue(), 1)

        assertFalse(Tabs.activated.testHasValue())
        GleanMetricsService.Tabs.activatedEvent()
        assertTrue(Tabs.activated.testHasValue())
        assertEquals(Tabs.activated.testGetValue(), 1)
    }

    fun testPages() {
        assertFalse(Pages.pageLoad.testHasValue())
        GleanMetricsService.startPageLoadTime("www.example.com")
        assertFalse(Pages.pageLoad.testHasValue())
        GleanMetricsService.stopPageLoadTimeWithURI("www.example.com")
        assertTrue(Pages.pageLoad.testHasValue())
    }

    @Test
    fun testImmersive() {
        assertFalse(Immersive.duration.testHasValue())
        GleanMetricsService.startImmersive()
        assertFalse(Immersive.duration.testHasValue())
        GleanMetricsService.stopImmersive()
        assertTrue(Immersive.duration.testHasValue())
    }

    @Test
    fun testMultiWindow() {
        assertFalse(Windows.duration.testHasValue())
        GleanMetricsService.openWindowEvent(1)
        assertFalse(Windows.duration.testHasValue())
        GleanMetricsService.closeWindowEvent(1)
        assertTrue(Windows.duration.testHasValue())

        assertFalse(Windows.movement.testHasValue())
        GleanMetricsService.windowsMoveEvent()
        assertTrue(Windows.movement.testHasValue())
        assertEquals(Windows.movement.testGetValue(), 1)

        assertFalse(Windows.resize.testHasValue())
        GleanMetricsService.windowsResizeEvent()
        assertTrue(Windows.resize.testHasValue())
        assertEquals(Windows.resize.testGetValue(), 1)

        assertFalse(Windows.activeInFrontTime.testHasValue())
        GleanMetricsService.activePlacementEvent(0, true)
        assertFalse(Windows.activeInFrontTime.testHasValue())
        GleanMetricsService.activePlacementEvent(0, false)
        assertTrue(Windows.activeInFrontTime.testHasValue())

        assertFalse(Windows.activeInLeftTime.testHasValue())
        GleanMetricsService.activePlacementEvent(1, true)
        assertFalse(Windows.activeInLeftTime.testHasValue())
        GleanMetricsService.activePlacementEvent(1, false)
        assertTrue(Windows.activeInLeftTime.testHasValue())

        assertFalse(Windows.activeInRightTime.testHasValue())
        GleanMetricsService.activePlacementEvent(2, true)
        assertFalse(Windows.activeInRightTime.testHasValue())
        GleanMetricsService.activePlacementEvent(2, false)
        assertTrue(Windows.activeInRightTime.testHasValue())

        assertFalse(Windows.openedWindowCount["single"].testHasValue())
        assertFalse(Windows.singleWindowOpenedTime.testHasValue())
        GleanMetricsService.openWindowsEvent(0, 1,false)
        assertTrue(Windows.openedWindowCount["single"].testHasValue())
        assertEquals(Windows.openedWindowCount["single"].testGetValue(), 1)
        assertFalse(Windows.singleWindowOpenedTime.testHasValue())
        assertFalse(Windows.doubleWindowOpenedTime.testHasValue())
        assertFalse(Windows.openedWindowCount["double"].testHasValue())
        GleanMetricsService.openWindowsEvent(1, 2, false)
        assertTrue(Windows.openedWindowCount["double"].testHasValue())
        assertEquals(Windows.openedWindowCount["double"].testGetValue(), 1)
        assertTrue(Windows.singleWindowOpenedTime.testHasValue())
        assertFalse(Windows.doubleWindowOpenedTime.testHasValue())
        assertFalse(Windows.tripleWindowOpenedTime.testHasValue())
        assertFalse(Windows.openedWindowCount["triple"].testHasValue())
        GleanMetricsService.openWindowsEvent(2, 3, false)
        assertTrue(Windows.openedWindowCount["triple"].testHasValue())
        assertEquals(Windows.openedWindowCount["triple"].testGetValue(), 1)
        assertTrue(Windows.doubleWindowOpenedTime.testHasValue())
        assertFalse(Windows.tripleWindowOpenedTime.testHasValue())
        GleanMetricsService.openWindowsEvent(3, 2, false)
        assertEquals(Windows.openedWindowCount["double"].testGetValue(), 2)
        assertTrue(Windows.tripleWindowOpenedTime.testHasValue())
        Pings.sessionEnd.submit();
        // Windows.openedWindowCount will reset when a session is ended.
        GleanMetricsService.resetOpenedWindowsCount(2, false)
        assertEquals(Windows.openedWindowCount["double"].testGetValue(), 1)

        assertFalse(Windows.openedPriWindowCount["single"].testHasValue())
        assertFalse(Windows.singlePriWindowOpenedTime.testHasValue())
        GleanMetricsService.openWindowsEvent(0, 1,true)
        assertTrue(Windows.openedPriWindowCount["single"].testHasValue())
        assertEquals(Windows.openedPriWindowCount["single"].testGetValue(), 1)
        assertFalse(Windows.singlePriWindowOpenedTime.testHasValue())
        assertFalse(Windows.doublePriWindowOpenedTime.testHasValue())
        assertFalse(Windows.openedPriWindowCount["double"].testHasValue())
        GleanMetricsService.openWindowsEvent(1, 2, true)
        assertTrue(Windows.openedPriWindowCount["double"].testHasValue())
        assertEquals(Windows.openedPriWindowCount["double"].testGetValue(), 1)
        assertTrue(Windows.singlePriWindowOpenedTime.testHasValue())
        assertFalse(Windows.doublePriWindowOpenedTime.testHasValue())
        assertFalse(Windows.triplePriWindowOpenedTime.testHasValue())
        assertFalse(Windows.openedPriWindowCount["triple"].testHasValue())
        GleanMetricsService.openWindowsEvent(2, 3, true)
        assertTrue(Windows.openedPriWindowCount["triple"].testHasValue())
        assertEquals(Windows.openedPriWindowCount["triple"].testGetValue(), 1)
        assertTrue(Windows.doublePriWindowOpenedTime.testHasValue())
        assertFalse(Windows.triplePriWindowOpenedTime.testHasValue())
        GleanMetricsService.openWindowsEvent(3, 2, true)
        assertEquals(Windows.openedPriWindowCount["double"].testGetValue(), 2)
        assertTrue(Windows.triplePriWindowOpenedTime.testHasValue())
        Pings.sessionEnd.submit();
        // Windows.openedPriWindowCount will reset when a session is ended.
        GleanMetricsService.resetOpenedWindowsCount(2, true)
        assertEquals(Windows.openedPriWindowCount["double"].testGetValue(), 1)
    }
}
