package com.igalia.wolvic.browser.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.util.AtomicFile;

import androidx.test.core.app.ApplicationProvider;

import com.igalia.wolvic.TestApplication;
import com.igalia.wolvic.browser.engine.SessionState;
import com.igalia.wolvic.ui.widgets.Windows;
import com.igalia.wolvic.utils.TestFileUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = TestApplication.class)
public class WindowsStateStoreTest {

    private Context mContext;
    private File mFile;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mFile = new File(mContext.getFilesDir(), WindowsStateStore.FILENAME);

        new AtomicFile(mFile).delete();
    }

    @Test
    public void readsNothingWithoutAFile() {
        assertNull(new WindowsStateStore(mContext).read());
    }

    @Test
    public void readsBackWhatWasWritten() {
        WindowsStateStore store = new WindowsStateStore(mContext);
        store.write(createState("https://wolvic.com/"));

        WindowsState restored = store.read();

        assertNotNull(restored);
        assertTrue(restored.privateMode);
        assertEquals(Windows.WindowPlacement.LEFT, restored.focusedWindowPlacement);

        assertEquals(1, restored.tabs.size());
        assertEquals("https://wolvic.com/", restored.tabs.get(0).mUri);
        assertEquals("Wolvic", restored.tabs.get(0).mTitle);

        assertEquals(1, restored.regularWindowsState.size());

        WindowState window = restored.regularWindowsState.get(0);
        assertEquals(Windows.WindowPlacement.LEFT, window.placement);
        assertEquals(800, window.textureWidth);
        assertEquals(450, window.textureHeight);
        assertEquals(4.0f, window.worldWidth, 0.0f);
        assertEquals(0, window.tabIndex);
        assertEquals(Windows.ContentType.HISTORY, window.contentType);
    }

    @Test
    public void readsTheFormatOfPreviousVersions() throws IOException {
        String json = TestFileUtils.INSTANCE.readTextFile(getClass().getClassLoader(), "persistence/windows_state.json");
        Files.write(mFile.toPath(), json.getBytes(StandardCharsets.UTF_8));

        WindowsState restored = new WindowsStateStore(mContext).read();

        assertNotNull(restored);
        assertFalse(restored.privateMode);
        assertEquals(Windows.WindowPlacement.FRONT, restored.focusedWindowPlacement);

        assertEquals(3, restored.tabs.size());
        assertEquals("https://example.com/", restored.tabs.get(0).mUri);
        assertEquals("https://www.mozilla.org/", restored.tabs.get(1).mUri);
        assertEquals("https://wolvic.com/en/start/", restored.tabs.get(2).mUri);
        assertNotNull(restored.tabs.get(0).mSettings);

        assertEquals(2, restored.regularWindowsState.size());
        assertEquals(Windows.WindowPlacement.FRONT, restored.regularWindowsState.get(0).placement);
        assertEquals(1, restored.regularWindowsState.get(0).tabIndex);
        assertEquals(Windows.WindowPlacement.LEFT, restored.regularWindowsState.get(1).placement);
        assertEquals(0, restored.regularWindowsState.get(1).tabIndex);
        assertEquals(Windows.ContentType.WEB_CONTENT, restored.regularWindowsState.get(1).contentType);
    }

    @Test
    public void keepsThePreviousStateWhenAWriteIsInterrupted() throws IOException {
        new WindowsStateStore(mContext).write(createState("https://wolvic.com/"));

        try (FileOutputStream interrupted = new AtomicFile(mFile).startWrite()) {
            interrupted.write("{\"tabs\": [".getBytes(StandardCharsets.UTF_8));
        }

        WindowsState restored = new WindowsStateStore(mContext).read();

        assertNotNull(restored);
        assertEquals("https://wolvic.com/", restored.tabs.get(0).mUri);
    }

    @Test
    public void discardsAnUnreadableState() throws IOException {
        Files.write(mFile.toPath(), "{\"tabs\": [".getBytes(StandardCharsets.UTF_8));

        assertNull(new WindowsStateStore(mContext).read());
        assertFalse(mFile.exists());
    }

    private static WindowsState createState(String aUri) {
        SessionState tab = new SessionState();
        tab.mUri = aUri;
        tab.mTitle = "Wolvic";

        WindowState window = new WindowState();
        window.placement = Windows.WindowPlacement.LEFT;
        window.textureWidth = 800;
        window.textureHeight = 450;
        window.worldWidth = 4.0f;
        window.tabIndex = 0;
        window.contentType = Windows.ContentType.HISTORY;

        WindowsState state = new WindowsState();
        state.privateMode = true;
        state.focusedWindowPlacement = Windows.WindowPlacement.LEFT;
        state.tabs.add(tab);
        state.regularWindowsState.add(window);

        return state;
    }
}
