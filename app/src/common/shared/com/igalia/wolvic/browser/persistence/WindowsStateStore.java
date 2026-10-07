package com.igalia.wolvic.browser.persistence;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.igalia.wolvic.utils.SystemUtils;

import java.io.File;

import mozilla.components.support.ktx.util.AtomicFileKt;

public class WindowsStateStore {

    private static final String LOGTAG = SystemUtils.createLogtag(WindowsStateStore.class);

    static final String FILENAME = "windows_state.json";

    private final AtomicFile mFile;
    private final Gson mGson = new GsonBuilder().setPrettyPrinting().create();

    public WindowsStateStore(@NonNull Context aContext) {
        mFile = new AtomicFile(new File(aContext.getFilesDir(), FILENAME));
    }

    @Nullable
    public WindowsState read() {
        try {
            WindowsState state = AtomicFileKt.readAndDeserialize(mFile, json -> mGson.fromJson(json, WindowsState.class));

            if (state != null) {
                Log.d(LOGTAG, "Windows state restored");
            }

            return state;

        } catch (Exception e) {
            Log.e(LOGTAG, "Error restoring windows state, tabs will not be restored: " + e.getLocalizedMessage());
            mFile.delete();

            return null;
        }
    }

    @WorkerThread
    public void write(@NonNull WindowsState aState) {
        String json = mGson.toJson(aState);

        if (AtomicFileKt.writeString(mFile, () -> json)) {
            Log.d(LOGTAG, "Windows state saved");
        } else {
            Log.e(LOGTAG, "Error saving windows state, the previous one is kept");
        }
    }
}
