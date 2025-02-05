package com.igalia.wolvic.telemetry;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import android.app.Application;
import android.os.Bundle;

import com.igalia.wolvic.BuildConfig;
import com.igalia.wolvic.VRBrowserApplication;

import java.util.concurrent.Executor;

import io.opentelemetry.android.OpenTelemetryRum;
import io.opentelemetry.android.OpenTelemetryRumBuilder;
import io.opentelemetry.android.config.OtelRumConfig;
import io.opentelemetry.android.features.diskbuffering.DiskBufferingConfiguration;
import io.opentelemetry.android.instrumentation.activity.ActivityLifecycleInstrumentation;
import io.opentelemetry.android.instrumentation.sessions.SessionInstrumentation;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;

public class OpenTelemetry implements ITelemetry {
    private final Application application;
    private OpenTelemetryRum rum;
    private OpenTelemetryRumBuilder rumBuilder;
    private final String instrumentationScopeName = BuildConfig.APPLICATION_ID;
    private final String instrumentationScopeVersion = "1.0.0";
    private final Executor diskIOExecutor;

    public OpenTelemetry(Application app) {
        application = app;
        diskIOExecutor = ((VRBrowserApplication) application).getExecutors().diskIO();
    }

    private void initializeOpenTelemetryAndroid() {
        DiskBufferingConfiguration diskBufferingConfiguration = DiskBufferingConfiguration.builder()
                .setEnabled(true)
                .setMaxCacheSize(10 * 1024 * 1024)
                .setMaxFileAgeForWriteMillis(1000 * 5)
                .build();
        OtelRumConfig config = new OtelRumConfig()
                .setDiskBufferingConfiguration(diskBufferingConfiguration);
        rumBuilder = OpenTelemetryRum.builder(application, config)
                .addSpanExporterCustomizer(exporter -> LoggingSpanExporter.create())
                .addInstrumentation(new SessionInstrumentation())
                .addInstrumentation(new ActivityLifecycleInstrumentation());
        try {
            rum = rumBuilder.build();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void runOnDiskIO(Runnable runnable) { diskIOExecutor.execute(runnable); }

    @Override
    public void start() {
        assert rum == null;
        initializeOpenTelemetryAndroid();
    }

    @Override
    public void stop() {
        rum = null;
        rumBuilder = null;
    }

    @Override
    public void customEvent(String name) {
        assert rum != null;
        runOnDiskIO(() -> {
            rum.getOpenTelemetry().getTracer(instrumentationScopeName, instrumentationScopeVersion)
                    .spanBuilder(name)
                    .startSpan()
                    .end();
        });
    }

    @Override
    public void customEvent(String name, Bundle bundle) {
        assert rum != null;
        runOnDiskIO(() -> {
            SpanBuilder spanBuilder = rum.getOpenTelemetry().getTracer(instrumentationScopeName, instrumentationScopeVersion)
                    .spanBuilder(name);
            for (String key : bundle.keySet()) {
                spanBuilder.setAttribute(key, bundle.get(key).toString());
            }
            spanBuilder.startSpan().end();
        });
    }
}