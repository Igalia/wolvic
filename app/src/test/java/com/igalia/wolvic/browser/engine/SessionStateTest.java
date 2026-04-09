package com.igalia.wolvic.browser.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
        
        // Add settings to trigger the fixed branch
        state.mSettings = new SessionSettings();
        
        // Serialize
        String json = gson.toJson(state);
        
        // Ensure "mSettings" only appears once in the JSON output
        int firstIndex = json.indexOf("\"mSettings\"");
        int lastIndex = json.lastIndexOf("\"mSettings\"");
        
        assertTrue("mSettings should be present", firstIndex != -1);
        assertEquals("mSettings should only appear once", firstIndex, lastIndex);
        
        // Deserialize
        SessionState restored = gson.fromJson(json, SessionState.class);
        assertNotNull(restored);
        assertEquals("https://wolvic.com", restored.mUri);
        assertEquals("Wolvic", restored.mTitle);
        assertNotNull(restored.mSettings);
    }
}
