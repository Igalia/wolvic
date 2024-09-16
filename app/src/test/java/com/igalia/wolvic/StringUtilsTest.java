package com.igalia.wolvic;

import static org.junit.Assert.assertEquals;

import com.igalia.wolvic.utils.StringUtils;

import org.junit.Test;

public class StringUtilsTest {
    @Test
    public void testCompareVersions() {
        assertEquals(1, StringUtils.compareVersions("5.11.1", "5.7.1"));
        assertEquals(-1, StringUtils.compareVersions("1.0.0", "1.0.1"));
        assertEquals(1, StringUtils.compareVersions("1.0.2", "1.0.1"));
        assertEquals(1, StringUtils.compareVersions("2.0", "1.9.9"));
        assertEquals(0, StringUtils.compareVersions("1.0", "1.0"));
        assertEquals(1, StringUtils.compareVersions("1.0.0a", "1.0.0"));
        assertEquals(-1, StringUtils.compareVersions("1.0", "1.0a"));
        assertEquals(0, StringUtils.compareVersions("1.0", "1.0.0"));
        assertEquals(-1, StringUtils.compareVersions("", "1.0"));
        assertEquals(1, StringUtils.compareVersions("1.1", ""));
    }
}
