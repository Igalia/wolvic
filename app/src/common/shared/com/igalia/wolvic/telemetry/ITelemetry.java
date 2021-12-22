package com.igalia.wolvic.telemetry;

import android.os.Bundle;

public interface ITelemetry {
    void start();
    void stop();
    void customEvent(String name);
    void customEvent(String name, Bundle bundle);
}
