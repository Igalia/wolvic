package com.igalia.wolvic.telemetry;

import android.app.Application;
import android.os.Bundle;

import com.igalia.wolvic.BuildConfig;
import com.igalia.wolvic.VRBrowserApplication;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.android.OpenTelemetryRum;
import io.opentelemetry.android.OpenTelemetryRumBuilder;
import io.opentelemetry.android.config.OtelRumConfig;
import io.opentelemetry.android.features.diskbuffering.DiskBufferingConfig;
import io.opentelemetry.android.instrumentation.activity.ActivityLifecycleInstrumentation;
import io.opentelemetry.android.instrumentation.sessions.SessionInstrumentation;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.logging.SystemOutLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporterBuilder;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporterBuilder;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;

public class OpenTelemetry implements ITelemetry {
    private final Application mApplication;
    private OpenTelemetryRum mRUM;
    private OpenTelemetryRumBuilder mRUMBuilder;
    private final String INSTRUMENTATION_SCOPE_NAME = BuildConfig.APPLICATION_ID;
    private final String INSTRUMENTATION_SCOPE_VERSION = BuildConfig.VERSION_NAME;
    private final Executor mDiskIOExecutor;

    private static final String OTLP_TRACES_PATH = "/v1/traces";
    private static final String OTLP_METRICS_PATH = "/v1/metrics";
    private static final String OTLP_LOGS_PATH = "/v1/logs";

    public OpenTelemetry(Application app) {
        mApplication = app;
        mDiskIOExecutor = ((VRBrowserApplication) mApplication).getExecutors().diskIO();
    }

    private void initializeOpenTelemetryAndroid() {
        // DiskBufferingConfig replaced the builder with static factories in 1.x.
        // Args: enabled, maxCacheSize (bytes), maxFileAgeForWriteMillis.
        DiskBufferingConfig diskBufferingConfig = DiskBufferingConfig.create(
                true, 10 * 1024 * 1024, 1000L * 5);
        OtelRumConfig config = new OtelRumConfig()
                .setDiskBufferingConfig(diskBufferingConfig);
        mRUMBuilder = new OpenTelemetryRumBuilder(mApplication, config)
                .addSpanExporterCustomizer(exporter -> createSpanExporter())
                .addMetricExporterCustomizer(exporter -> createMetricExporter())
                .addLogRecordExporterCustomizer(exporter -> createLogRecordExporter())
                .addInstrumentation(new SessionInstrumentation())
                .addInstrumentation(new ActivityLifecycleInstrumentation());
        try {
            mRUM = mRUMBuilder.build();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private SpanExporter createSpanExporter() {
        if (BuildConfig.DEBUG) {
            return LoggingSpanExporter.create();
        }
        String url = otlpUrl(OTLP_TRACES_PATH);
        if (url == null) {
            return SpanExporter.composite();
        }
        OtlpHttpSpanExporterBuilder builder = OtlpHttpSpanExporter.builder().setEndpoint(url);
        return builder.build();
    }

    private MetricExporter createMetricExporter() {
        if (BuildConfig.DEBUG) {
            return LoggingMetricExporter.create();
        }
        String url = otlpUrl(OTLP_METRICS_PATH);
        if (url == null) {
            return noopMetricExporter();
        }
        OtlpHttpMetricExporterBuilder builder = OtlpHttpMetricExporter.builder().setEndpoint(url);
        return builder.build();
    }

    private LogRecordExporter createLogRecordExporter() {
        if (BuildConfig.DEBUG) {
            return SystemOutLogRecordExporter.create();
        }
        String url = otlpUrl(OTLP_LOGS_PATH);
        if (url == null) {
            return LogRecordExporter.composite();
        }
        OtlpHttpLogRecordExporterBuilder builder = OtlpHttpLogRecordExporter.builder().setEndpoint(url);
        return builder.build();
    }

    private String otlpUrl(String signalPath) {
        String base = BuildConfig.OTEL_ENDPOINT;
        if (base == null || base.isEmpty()) {
            return null;
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + signalPath;
    }

    // No public no-op MetricExporter exists (unlike Span/LogRecordExporter.composite()).
    private static MetricExporter noopMetricExporter() {
        return new MetricExporter() {
            @Override
            public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
                return AggregationTemporality.CUMULATIVE;
            }

            @Override
            public CompletableResultCode export(Collection<MetricData> metrics) {
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode flush() {
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode shutdown() {
                return CompletableResultCode.ofSuccess();
            }
        };
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

    @Override
    public void count(String name, Bundle bundle) {
        final OpenTelemetryRum rum = mRUM;
        if (rum == null) {
            return;
        }
        runOnDiskIO(() -> rum.getOpenTelemetry()
                .getMeter(INSTRUMENTATION_SCOPE_NAME)
                .counterBuilder(name)
                .build()
                .add(1, toAttributes(bundle)));
    }

    @Override
    public void event(String name, Bundle bundle) {
        final OpenTelemetryRum rum = mRUM;
        if (rum == null) {
            return;
        }
        // otel-android attaches session.id, so per-session funnels can be reconstructed downstream.
        runOnDiskIO(() -> rum.emitEvent(name, "", toAttributes(bundle)));
    }

    private SpanBuilder newSpanBuilder(OpenTelemetryRum rum, String name, Bundle bundle) {
        return rum.getOpenTelemetry()
                .getTracer(INSTRUMENTATION_SCOPE_NAME, INSTRUMENTATION_SCOPE_VERSION)
                .spanBuilder(name)
                .setAllAttributes(toAttributes(bundle));
    }

    // Converts a Bundle to typed OpenTelemtry attributes (bool/long/double/string) skipping nulls.
    private Attributes toAttributes(Bundle bundle) {
        if (bundle == null) {
            return Attributes.empty();
        }
        AttributesBuilder builder = Attributes.builder();
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Boolean) {
                builder.put(AttributeKey.booleanKey(key), (Boolean) value);
            } else if (value instanceof Integer || value instanceof Long
                    || value instanceof Short || value instanceof Byte) {
                builder.put(AttributeKey.longKey(key), ((Number) value).longValue());
            } else if (value instanceof Float || value instanceof Double) {
                builder.put(AttributeKey.doubleKey(key), ((Number) value).doubleValue());
            } else {
                builder.put(AttributeKey.stringKey(key), value.toString());
            }
        }
        return builder.build();
    }
}