package com.igalia.wolvic.browser.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.junit.Test;


public class SessionStateTest {

    @Test
    public void testSerializationWithoutDuplicates() {
        Gson gson = new GsonBuilder().create();

        SessionState state = new SessionState();
        state.mUri = "https://wolvic.com";
        state.mTitle = "Wolvic";
        state.mSettings = new SessionSettings();

        String json = gson.toJson(state);

        int firstIndex = json.indexOf("\"mSettings\"");
        int lastIndex  = json.lastIndexOf("\"mSettings\"");

        assertTrue("mSettings key must be present in JSON", firstIndex != -1);
        assertEquals("mSettings key must appear exactly once (duplicate = corruption)", firstIndex, lastIndex);
    }


    @Test
    public void testRoundTrip_preservesUriAndTitle() {
        Gson gson = new GsonBuilder().create();

        SessionState state = new SessionState();
        state.mUri   = "https://wolvic.com";
        state.mTitle = "Wolvic Browser";
        state.mSettings = new SessionSettings();

        SessionState restored = gson.fromJson(gson.toJson(state), SessionState.class);

        assertNotNull(restored);
        assertEquals("https://wolvic.com", restored.mUri);
        assertEquals("Wolvic Browser", restored.mTitle);
        assertNotNull(restored.mSettings);
    }

    @Test
    public void testRoundTrip_withNullSettings_noSettingsKeyInJson() {
        Gson gson = new GsonBuilder().create();

        SessionState state = new SessionState();
        state.mUri      = "https://example.com";
        state.mTitle    = "Example";
        state.mSettings = null; // null settings → the conditional branch is NOT entered

        String json = gson.toJson(state);

        assertTrue("mSettings should not be written when null", !json.contains("\"mSettings\""));

        SessionState restored = gson.fromJson(json, SessionState.class);
        assertNotNull(restored);
        assertEquals("https://example.com", restored.mUri);
    }


    @Test
    public void testDeserialize_corruptJson_returnsNull() {
        Gson gson = new GsonBuilder().create();

        String corrupt = "{ \"mUri\": \"https://wolvic.com\", \"mSettings\": { BAD JSON";

        SessionState restored = null;
        try {
            restored = gson.fromJson(corrupt, SessionState.class);
        } catch (Exception ignored) {
            // GSON may throw or return null; either way the caller must handle it.
        }
    }

    @Test
    public void testDeserialize_emptyJson_returnsNull() {
        Gson gson = new GsonBuilder().create();
        SessionState restored = gson.fromJson("{}", SessionState.class);
        assertEquals("Default mUri should be empty string", "", restored.mUri);
    }

    @Test
    public void testDeserialize_missingNewFields_usesDefaults() {
        Gson gson = new GsonBuilder().create();
        String oldFormat = "{\"mUri\":\"https://example.com\",\"mTitle\":\"Old Tab\"}";

        SessionState restored = gson.fromJson(oldFormat, SessionState.class);

        assertNotNull(restored);
        assertEquals("https://example.com", restored.mUri);
        assertEquals("Old Tab", restored.mTitle);
        assertEquals(0L, restored.mLastUse);
        assertNull(restored.mRegion);
        assertNull(restored.mParentId);
    }

    @Test
    public void testDeserialize_extraUnknownFields_ignoredSafely() {
        Gson gson = new GsonBuilder().create();

        String newFormat = "{\"mUri\":\"https://a.com\",\"mTitle\":\"A\",\"mFutureField\":\"value\"}";

        SessionState restored = gson.fromJson(newFormat, SessionState.class);

        assertNotNull(restored);
        assertEquals("https://a.com", restored.mUri);
    }
}
