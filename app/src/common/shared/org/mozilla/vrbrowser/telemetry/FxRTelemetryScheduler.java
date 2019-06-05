package org.mozilla.vrbrowser.telemetry;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import org.mozilla.telemetry.config.TelemetryConfiguration;
import org.mozilla.telemetry.schedule.TelemetryScheduler;
import org.mozilla.telemetry.schedule.jobscheduler.TelemetryJobService;

/**
 * Replacement for the AC Telemetry Scheduler that avoids using the RECEIVE_BOOT_COMPLETED permission.
 */
public class FxRTelemetryScheduler implements TelemetryScheduler {

    private static final int DEFAULT_JOB_ID = 42;

    private final int jobId;

    public FxRTelemetryScheduler() {
        this(DEFAULT_JOB_ID);
    }

    public FxRTelemetryScheduler(int jobId) {
        this.jobId = jobId;
    }

    @Override
    public void scheduleUpload(TelemetryConfiguration configuration) {
        final ComponentName jobService = new ComponentName(configuration.getContext(), TelemetryJobService.class);

        // The only difference between this and the one provided by the Telemetry component is that we don't
        // use the setPersisted flag as it requires the RECEIVE_BOOT_COMPLETED permission and it was removed
        // here: https://github.com/MozillaReality/FirefoxReality-SecureBugs/issues/3
        final JobInfo jobInfo = new JobInfo.Builder(jobId, jobService)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(configuration.getInitialBackoffForUpload(), JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build();

        final JobScheduler scheduler = (JobScheduler) configuration.getContext()
                .getSystemService(Context.JOB_SCHEDULER_SERVICE);

        scheduler.schedule(jobInfo);
    }
}