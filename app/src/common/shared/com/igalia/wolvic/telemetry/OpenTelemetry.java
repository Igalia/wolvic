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
    private final Application mApplication;
    private OpenTelemetryRum mRUM;
    private OpenTelemetryRumBuilder mRUMBuilder;
    private final String INSTRUMENTATION_SCOPE_NAME = BuildConfig.APPLICATION_ID;
    private final String INSTRUMENTATION_SCOPE_VERSION = "1.0.0";
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
        assert mRUM != null;
        runOnDiskIO(() -> {
            mRUM.getOpenTelemetry().getTracer(INSTRUMENTATION_SCOPE_NAME, INSTRUMENTATION_SCOPE_VERSION)
                    .spanBuilder(name)
                    .startSpan()
                    .end();
        });
    }

    @Override
    public void customEvent(String name, Bundle bundle) {
        assert mRUM != null;
        runOnDiskIO(() -> {
            SpanBuilder spanBuilder = mRUM.getOpenTelemetry().getTracer(INSTRUMENTATION_SCOPE_NAME, INSTRUMENTATION_SCOPE_VERSION)
                    .spanBuilder(name);
            for (String key : bundle.keySet()) {
                spanBuilder.setAttribute(key, bundle.get(key).toString());
            }
            spanBuilder.startSpan().end();
        });
    }
}