package com.igalia.wolvic.browser.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.igalia.wolvic.TestApplication;
import com.igalia.wolvic.browser.api.WSessionState;
import com.igalia.wolvic.utils.TestFileUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Objects;

@RunWith(RobolectricTestRunner.class)
@Config(application = TestApplication.class)
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
    public void testSerializationPreservesSessionState() {
        Gson gson = new GsonBuilder().create();

        SessionState state = new SessionState();

        state.mUri = "https://wolvic.com/b";
        state.mSettings = new SessionSettings();
        state.mSessionState = WSessionState.fromJson(TestFileUtils.INSTANCE.readTextFile(
                Objects.requireNonNull(getClass().getClassLoader()), "session/sessionState.json"));

        SessionState restored = gson.fromJson(gson.toJson(state), SessionState.class);

        assertNotNull("mSessionState should be restored", restored.mSessionState);
        assertEquals(state.mSessionState.toJson(), restored.mSessionState.toJson());
    }
}
