package com.igalia.wolvic.browser.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.igalia.wolvic.browser.api.WSessionState;

import org.junit.Test;

import java.io.StringReader;

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

    @Test
    public void testSessionStateReadsBackWhatWasWritten() {
        String stateJson = "{\"index\":1,\"history\":[{\"url\":\"https://wolvic.com\"}]}";
        SessionState state = new SessionState();
        state.mUri = "https://wolvic.com";
        state.mSettings = new SessionSettings();
        state.mSessionState = new WSessionState() {
            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public String toJson() {
                return stateJson;
            }
        };

        String json = new GsonBuilder().create().toJson(state);
        String written = JsonParser.parseString(json).getAsJsonObject().get("mSessionState").toString();
        String read = SessionState.ISessionStateAdapter.readJson(new JsonReader(new StringReader(written)));

        assertNotNull("the session state should be read back", read);
        assertEquals(JsonParser.parseString(stateJson), JsonParser.parseString(read));
    }
}
