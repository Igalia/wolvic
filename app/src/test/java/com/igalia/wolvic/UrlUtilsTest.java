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

import com.igalia.wolvic.utils.UrlUtils;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class UrlUtilsTest {
    private static Context mContext;
    @Parameter(value = 0)
    public String text;
    @Parameter(value = 1)
    public String expected;

    @BeforeClass
    public static void init() {
        mContext = Mockito.mock(Context.class);
        UrlUtils.isUnderTest = true;
    }

    @Parameters(name = "{0} => {1}")
    public static Collection data() {
        return Arrays.asList(new Object[][]{
                {"test", UrlUtils.TEST_SEARCH_URL + "test"},
                {"test spaces", UrlUtils.TEST_SEARCH_URL + "test spaces"},
                {"http://example", UrlUtils.TEST_SEARCH_URL + "http://example"},
                {"http://exam ple", UrlUtils.TEST_SEARCH_URL + "http://exam ple"},
                {"http://example.com", "http://example.com"},
                {"example.com", "http://example.com"},
                {"sublevel.example.uvwxyz", "http://sublevel.example.uvwxyz"},
                {"http://121.25.63.2", "http://121.25.63.2"},
                {"http://121 .25.63.2", UrlUtils.TEST_SEARCH_URL + "http://121 .25.63.2"},
                {"http://1211.25.63.2", UrlUtils.TEST_SEARCH_URL + "http://1211.25.63.2"},
                {"about://config", "about://config"},
                {"resource://images", "resource://images"},
                {"file:///tmp/data", "file:///tmp/data"}
        });
    }

    @Test
    public void testUrlForText() {
        String result = UrlUtils.urlForText(mContext, text);
        assertEquals(expected, result);
    }
}

