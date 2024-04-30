package com.igalia.wolvic;

import static org.junit.Assert.assertEquals;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mockito;

import android.content.Context;
import androidx.annotation.NonNull;

import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.utils.UrlUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@RunWith(Parameterized.class)
public class UrlUtilsTest {
    private static Context mContext;
    private static WSession.UrlUtilsVisitor mVisitor;
    @Parameter(value = 0)
    public String text;
    @Parameter(value = 1)
    public String expected;

    @BeforeClass
    public static void init() {
        mContext = Mockito.mock(Context.class);
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
                {"blob:example.com/123456-7890-abcdef-ghijk-lmnopqrstuvwx", "blob:example.com/123456-7890-abcdef-ghijk-lmnopqrstuvwx"}
        });
    }

    @Test
    public void testUrlForText() {
        String result = UrlUtils.urlForText(mContext, text, mVisitor);
        assertEquals(expected, result);
    }
}

