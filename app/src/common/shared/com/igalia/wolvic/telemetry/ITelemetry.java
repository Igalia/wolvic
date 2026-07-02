package com.igalia.wolvic.telemetry;

import android.os.Bundle;

public interface ITelemetry {
    void start();
    void stop();
    void customEvent(String name);
    void customEvent(String name, Bundle bundle);
    // Records an event that took a known amount of time, so backends that support it (e.g. spans)
    // can represent it with a real duration instead of two disconnected instantaneous events.
    void timedEvent(String name, long durationMillis, Bundle bundle);
}
