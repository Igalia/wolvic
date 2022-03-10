package com.igalia.wolvic.browser.api;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.impl.ResultImpl;
import com.igalia.wolvic.browser.api.impl.RuntimeImpl;
import com.igalia.wolvic.browser.api.impl.SessionImpl;

public class WFactory {
    public static @NonNull WRuntime createRuntime(@NonNull Context context, @NonNull WRuntimeSettings settings) {
        return new RuntimeImpl(context, settings);
    }

    public static @NonNull WSession createSession() {
        return createSession(null);
    }

    public static @NonNull WSession createSession(@Nullable WSessionSettings settings) {
        return new SessionImpl(settings);
    }

    public static @NonNull <T> WResult<T> creteResult() {
        return new ResultImpl<>();
    }
}
