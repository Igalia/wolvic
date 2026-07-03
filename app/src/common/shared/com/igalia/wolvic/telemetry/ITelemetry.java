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
    // Increments a counter metric — for aggregate "how many X" questions.
    // Attributes should be low-cardinality (no ids). bundle may be null.
    void count(String name, Bundle bundle);
    // Emits a discrete event (log record) that retains session context, so
    // per-session journeys/funnels can be reconstructed. bundle may be null.
    void event(String name, Bundle bundle);
}
