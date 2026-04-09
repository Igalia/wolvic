package com.igalia.wolvic.search;

import org.junit.Test;

import static org.junit.Assert.*;

public class CustomSearchEngineTest {

    @Test
    public void testBuilderCreatesValidEngine() {
        CustomSearchEngine engine = new CustomSearchEngine.Builder()
                .setId("custom_1")
                .setName("Test Engine")
                .setSearchUrl("https://example.com/search?q=%s")
                .setSuggestUrl("https://example.com/suggest?q=%s")
                .build();

        assertEquals("custom_1", engine.getId());
        assertEquals("Test Engine", engine.getName());
        assertEquals("https://example.com/search?q=%s", engine.getSearchUrl());
        assertEquals("https://example.com/suggest?q=%s", engine.getSuggestUrl());
    }

    @Test
    public void testBuilderWithOptionalSuggestUrl() {
        CustomSearchEngine engine = new CustomSearchEngine.Builder()
                .setId("custom_2")
                .setName("No Suggestions")
                .setSearchUrl("https://example.com/search?q=%s")
                .build();

        assertNull(engine.getSuggestUrl());
    }

    @Test(expected = IllegalStateException.class)
    public void testBuilderThrowsWhenMissingId() {
        new CustomSearchEngine.Builder()
                .setName("Test")
                .setSearchUrl("https://example.com/search?q=%s")
                .build();
    }

    @Test(expected = IllegalStateException.class)
    public void testBuilderThrowsWhenMissingName() {
        new CustomSearchEngine.Builder()
                .setId("custom_1")
                .setSearchUrl("https://example.com/search?q=%s")
                .build();
    }

    @Test(expected = IllegalStateException.class)
    public void testBuilderThrowsWhenMissingSearchUrl() {
        new CustomSearchEngine.Builder()
                .setId("custom_1")
                .setName("Test")
                .build();
    }

    @Test
    public void testEquality() {
        CustomSearchEngine engine1 = new CustomSearchEngine.Builder()
                .setId("custom_1")
                .setName("Test")
                .setSearchUrl("https://example.com/search?q=%s")
                .build();

        CustomSearchEngine engine2 = new CustomSearchEngine.Builder()
                .setId("custom_1")
                .setName("Test")
                .setSearchUrl("https://example.com/search?q=%s")
                .build();

        assertEquals(engine1, engine2);
    }

    @Test
    public void testInequality() {
        CustomSearchEngine engine1 = new CustomSearchEngine.Builder()
                .setId("custom_1")
                .setName("Test 1")
                .setSearchUrl("https://example.com/search?q=%s")
                .build();

        CustomSearchEngine engine2 = new CustomSearchEngine.Builder()
                .setId("custom_2")
                .setName("Test 2")
                .setSearchUrl("https://example.com/search?q=%s")
                .build();

        assertNotEquals(engine1, engine2);
    }

    @Test
    public void testToString() {
        CustomSearchEngine engine = new CustomSearchEngine.Builder()
                .setId("custom_1")
                .setName("Test")
                .setSearchUrl("https://example.com/search?q=%s")
                .build();

        String str = engine.toString();
        assertTrue(str.contains("custom_1"));
        assertTrue(str.contains("Test"));
    }
}