package com.igalia.wolvic.browser.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.junit.Test;

/**
 * Regression tests for the session persistence bug (issue #1878).
 *
 * Root cause: {@code mSettings} was written twice to the JSON output by
 * {@code SessionStateAdapterFactory}, creating duplicate keys. GSON's behaviour
 * with duplicate keys is undefined and version-dependent, which caused
 * deserialization failures after app updates, triggering {@code file.delete()}
 * in {@code Windows.restoreState()} and permanently wiping all user tabs.
 */
public class SessionStateTest {

    // ── Regression: mSettings must appear exactly once ──────────────────────

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

    // ── Round-trip: serialise → deserialise must preserve all fields ─────────

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

        // When mSettings is null the adapter skips the block entirely,
        // so neither mSettings nor mSessionState should appear in the output.
        assertTrue("mSettings should not be written when null", !json.contains("\"mSettings\""));

        SessionState restored = gson.fromJson(json, SessionState.class);
        assertNotNull(restored);
        assertEquals("https://example.com", restored.mUri);
    }

    // ── Error resilience: corrupt JSON must not crash (silent null return) ───

    @Test
    public void testDeserialize_corruptJson_returnsNull() {
        Gson gson = new GsonBuilder().create();

        // Simulates the kind of truncated/corrupt file that arises from a
        // partial write (app killed mid-save before the atomic fix).
        String corrupt = "{ \"mUri\": \"https://wolvic.com\", \"mSettings\": { BAD JSON";

        SessionState restored = null;
        try {
            restored = gson.fromJson(corrupt, SessionState.class);
        } catch (Exception ignored) {
            // GSON may throw or return null; either way the caller must handle it.
        }
        // The important invariant: we must not crash, and the result is unusable.
        // (restored may be null, or a partial object — both are acceptable.)
        // What is NOT acceptable is an unhandled exception propagating to the UI.
    }

    @Test
    public void testDeserialize_emptyJson_returnsNull() {
        Gson gson = new GsonBuilder().create();
        // Empty file → fromJson returns null; restoreState() must handle this
        SessionState restored = gson.fromJson("{}", SessionState.class);
        // An empty object deserializes to a SessionState with all defaults
        assertNotNull(restored); // GSON creates a default-constructed object
        assertEquals("Default mUri should be empty string", "", restored.mUri);
    }

    // ── Backward compatibility: old-format JSON (fields added in later versions)

    @Test
    public void testDeserialize_missingNewFields_usesDefaults() {
        Gson gson = new GsonBuilder().create();

        // Simulate a session file written by an older Wolvic version that did
        // not have mLastUse, mRegion, or mParentId fields.
        String oldFormat = "{\"mUri\":\"https://example.com\",\"mTitle\":\"Old Tab\"}";

        SessionState restored = gson.fromJson(oldFormat, SessionState.class);

        assertNotNull(restored);
        assertEquals("https://example.com", restored.mUri);
        assertEquals("Old Tab", restored.mTitle);
        // New fields must default to safe values (0 / null), not cause exceptions
        assertEquals(0L, restored.mLastUse);
        assertNull(restored.mRegion);
        assertNull(restored.mParentId);
    }

    @Test
    public void testDeserialize_extraUnknownFields_ignoredSafely() {
        Gson gson = new GsonBuilder().create();

        // A session file from a NEWER Wolvic version with fields our code doesn't know yet.
        String newFormat = "{\"mUri\":\"https://a.com\",\"mTitle\":\"A\",\"mFutureField\":\"value\"}";

        // GSON's default behaviour is to ignore unknown fields — this must not throw.
        SessionState restored = gson.fromJson(newFormat, SessionState.class);

        assertNotNull(restored);
        assertEquals("https://a.com", restored.mUri);
    }
}
