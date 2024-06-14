package com.igalia.wolvic.telemetry;

import android.content.Context;
import android.os.Bundle;

import com.huawei.hms.analytics.HiAnalytics;
import com.huawei.hms.analytics.HiAnalyticsInstance;
import com.huawei.hms.analytics.type.ReportPolicy;
import com.igalia.wolvic.telemetry.ITelemetry;

import java.util.HashSet;
import java.util.Set;

public class HVRTelemetry implements ITelemetry {

    public HVRTelemetry(Context ctx){
        mContext = ctx;
    }

    private void initialize() {
        mService = HiAnalytics.getInstance(mContext);
        ReportPolicy moveBackgroundPolicy = ReportPolicy.ON_MOVE_BACKGROUND_POLICY;
        // Create a policy that is used to report an event at the specified interval.
        ReportPolicy scheduledTimePolicy = ReportPolicy.ON_SCHEDULED_TIME_POLICY;
        // Set the event reporting interval to 600 seconds.
        scheduledTimePolicy.setThreshold(600);
        Set<ReportPolicy> reportPolicies = new HashSet<>();
        // Add the ON_SCHEDULED_TIME_POLICY and ON_MOVE_BACKGROUND_POLICY policies.
        reportPolicies.add(scheduledTimePolicy);
        reportPolicies.add(moveBackgroundPolicy);
        // Set the ON_MOVE_BACKGROUND_POLICY and ON_SCHEDULED_TIME_POLICY policies.
        mService.setReportPolicies(reportPolicies);
    }

    @Override
    public void start() {
        if (mService == null) {
            initialize();
        }
    }

    @Override
    public void stop() {
        mService = null;
    }

    @Override
    public void customEvent(String name) {
        customEvent(name, new Bundle());
    }

    @Override
    public void customEvent(String name, Bundle bundle) {
        if (mService != null) {
            mService.onEvent(name, bundle);
        }
    }

    private Context mContext;
    private HiAnalyticsInstance mService;
}
