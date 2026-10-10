package com.igalia.wolvic;

import static org.junit.Assert.assertEquals;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.annotation.Config;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.utils.UrlUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(application = VRBrowserApplication.class)
public class UrlUtilsTest {
    private static WSession.UrlUtilsVisitor mVisitor;
    @Parameter(value = 0)
    public String text;
    @Parameter(value = 1)
    public String expected;

    @BeforeClass
    public static void init() {
        mVisitor = new WSession.UrlUtilsVisitor() {
            // Any web engine should support at least these schemes
            private final List<String> ENGINE_SUPPORTED_SCHEMES = Arrays.asList("about", "data", "file", "ftp", "http", "https", "ws", "wss", "blob");
            @Override
            public boolean isSupportedScheme(@NonNull String scheme) {
                return ENGINE_SUPPORTED_SCHEMES.contains(scheme);
            }
        };
        UrlUtils.isUnderTest = true;
    }

    @Parameters(name = "{0} => {1}")
    public static Collection data() {
        return Arrays.asList(new Object[][]{
                {"test", UrlUtils.TEST_SEARCH_URL + "test"},
                {" test spaces ", UrlUtils.TEST_SEARCH_URL + "test spaces"},
                {"http://example", "http://example"},
                {"http://exam ple", UrlUtils.TEST_SEARCH_URL + "http://exam ple"},
                {"http://example.com", "http://example.com"},
                {"https://example.com", "https://example.com"},
                {"example.com", "http://example.com"},
                {"sublevel.example.uvwxyz", "http://sublevel.example.uvwxyz"},
                {"121.25.63.2", "http://121.25.63.2"},
                {"http://121.25.63.2", "http://121.25.63.2"},
                {"http://121 .25.63.2", UrlUtils.TEST_SEARCH_URL + "http://121 .25.63.2"},
                {"1211.25.63.2", UrlUtils.TEST_SEARCH_URL + "1211.25.63.2"},
                {"http://1211.25.63.2", UrlUtils.TEST_SEARCH_URL + "http://1211.25.63.2"},
                {"http://11.222.333.4", UrlUtils.TEST_SEARCH_URL + "http://11.222.333.4"},
                {"about://config", "about://config"},
                {"file:///tmp/data", "file:///tmp/data"},
                {"ftp://example.com", "ftp://example.com"},
                {"ws://example.com", "ws://example.com"},
                {"wss://example.com", "wss://example.com"},
                {"data://images", "data://images"},
                {"data:,Hello%2C%20World%21", "data:,Hello%2C%20World%21"},
                {"data:text/plain;base64,SGVsbG8sIFdvcmxkIQ==", "data:text/plain;base64,SGVsbG8sIFdvcmxkIQ=="},
                {"blob:example.com/123456-7890-abcdef-ghijk-lmnopqrstuvwx", "blob:example.com/123456-7890-abcdef-ghijk-lmnopqrstuvwx"},
                {"https://en.wikipedia.org/wiki/Virtual reality", "https://en.wikipedia.org/wiki/Virtual%20reality"},
                {"https://www.google.com/search?q=wolvic browser", "https://www.google.com/search?q=wolvic%20browser"},
                {"https://example.com/a%20b c#d e", "https://example.com/a%20b%20c#d%20e"},
                {"file:///sdcard/Download/My Video.mp4", "file:///sdcard/Download/My%20Video.mp4"},
                {"http://exam ple/path", UrlUtils.TEST_SEARCH_URL + "http://exam ple/path"},
                {"en.wikipedia.org/wiki/Virtual reality", "http://en.wikipedia.org/wiki/Virtual%20reality"},
                {"192.168.1.1/my files", "http://192.168.1.1/my%20files"},
                {"localhost/my files", "http://localhost/my%20files"},
                {"node.js/express tutorial", UrlUtils.TEST_SEARCH_URL + "node.js/express tutorial"},
                {"example.com ?q=search", UrlUtils.TEST_SEARCH_URL + "example.com ?q=search"}
        });
    }

    @Test
    public void testUrlForText() {
        String result = UrlUtils.urlForText(ApplicationProvider.getApplicationContext(), text, mVisitor);
        assertEquals(expected, result);
    }
}

