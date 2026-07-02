package com.igalia.wolvic.telemetry;

import android.app.Application;
import android.os.Bundle;

import com.igalia.wolvic.BuildConfig;
import com.igalia.wolvic.VRBrowserApplication;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.android.OpenTelemetryRum;
import io.opentelemetry.android.OpenTelemetryRumBuilder;
import io.opentelemetry.android.config.OtelRumConfig;
import io.opentelemetry.android.features.diskbuffering.DiskBufferingConfiguration;
import io.opentelemetry.android.instrumentation.activity.ActivityLifecycleInstrumentation;
import io.opentelemetry.android.instrumentation.sessions.SessionInstrumentation;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;

public class OpenTelemetry implements ITelemetry {
    private final Application mApplication;
    private OpenTelemetryRum mRUM;
    private OpenTelemetryRumBuilder mRUMBuilder;
    private final String INSTRUMENTATION_SCOPE_NAME = BuildConfig.APPLICATION_ID;
    private final String INSTRUMENTATION_SCOPE_VERSION = BuildConfig.VERSION_NAME;
    private final Executor mDiskIOExecutor;

    public OpenTelemetry(Application app) {
        mApplication = app;
        mDiskIOExecutor = ((VRBrowserApplication) mApplication).getExecutors().diskIO();
    }

    private void initializeOpenTelemetryAndroid() {
        DiskBufferingConfiguration diskBufferingConfiguration = DiskBufferingConfiguration.builder()
                .setEnabled(true)
                .setMaxCacheSize(10 * 1024 * 1024)
                .setMaxFileAgeForWriteMillis(1000 * 5)
                .build();
        OtelRumConfig config = new OtelRumConfig()
                .setDiskBufferingConfiguration(diskBufferingConfiguration);
        mRUMBuilder = OpenTelemetryRum.builder(mApplication, config)
                .addSpanExporterCustomizer(exporter -> LoggingSpanExporter.create())
                .addInstrumentation(new SessionInstrumentation())
                .addInstrumentation(new ActivityLifecycleInstrumentation());
        try {
            mRUM = mRUMBuilder.build();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void runOnDiskIO(Runnable runnable) { mDiskIOExecutor.execute(runnable); }

    @Override
    public void start() {
        assert mRUM == null;
        initializeOpenTelemetryAndroid();
    }

    @Override
    public void stop() {
        mRUM = null;
        mRUMBuilder = null;
    }

    @Override
    public void customEvent(String name) {
        // Capture mRUM as stop() may null it on the main thread before the disk-IO runnable runs.
        final OpenTelemetryRum rum = mRUM;
        if (rum == null) {
            return;
        }
        runOnDiskIO(() -> {
            rum.getOpenTelemetry().getTracer(INSTRUMENTATION_SCOPE_NAME, INSTRUMENTATION_SCOPE_VERSION)
                    .spanBuilder(name)
                    .startSpan()
                    .end();
        });
    }

    @Override
    public void customEvent(String name, Bundle bundle) {
        final OpenTelemetryRum rum = mRUM;
        if (rum == null) {
            return;
        }
        runOnDiskIO(() -> newSpanBuilder(rum, name, bundle).startSpan().end());
    }

    @Override
    public void timedEvent(String name, long durationMillis, Bundle bundle) {
        final OpenTelemetryRum rum = mRUM;
        if (rum == null) {
            return;
        }
        runOnDiskIO(() -> {
            long endMillis = System.currentTimeMillis();
            long startMillis = endMillis - Math.max(0, durationMillis);
            Span span = newSpanBuilder(rum, name, bundle)
                    .setStartTimestamp(startMillis, TimeUnit.MILLISECONDS)
                    .startSpan();
            span.end(endMillis, TimeUnit.MILLISECONDS);
        });
    }

    private SpanBuilder newSpanBuilder(OpenTelemetryRum rum, String name, Bundle bundle) {
        SpanBuilder spanBuilder = rum.getOpenTelemetry()
                .getTracer(INSTRUMENTATION_SCOPE_NAME, INSTRUMENTATION_SCOPE_VERSION)
                .spanBuilder(name);
        if (bundle != null) {
            for (String key : bundle.keySet()) {
                Object value = bundle.get(key);
                if (value == null) {
                    continue;
                }
                if (value instanceof Boolean) {
                    spanBuilder.setAttribute(key, (Boolean) value);
                } else if (value instanceof Integer || value instanceof Long
                        || value instanceof Short || value instanceof Byte) {
                    spanBuilder.setAttribute(key, ((Number) value).longValue());
                } else if (value instanceof Float || value instanceof Double) {
                    spanBuilder.setAttribute(key, ((Number) value).doubleValue());
                } else {
                    spanBuilder.setAttribute(key, value.toString());
                }
            }
        }
        return spanBuilder;
    }
}