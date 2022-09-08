package com.igalia.wolvic.browser.api.impl;

import androidx.annotation.NonNull;

import com.igalia.wolvic.browser.api.WRuntime;
import com.igalia.wolvic.crashreporting.CrashReporterService;

import org.mozilla.geckoview.GeckoRuntime;

public class CrashReporterServiceImpl extends CrashReporterService {
    @NonNull
    @Override
    protected WRuntime.CrashReportIntent createCrashReportIntent() {
        return new WRuntime.CrashReportIntent(GeckoRuntime.ACTION_CRASHED, GeckoRuntime.EXTRA_MINIDUMP_PATH, GeckoRuntime.EXTRA_EXTRAS_PATH, GeckoRuntime.EXTRA_CRASH_PROCESS_TYPE);
    }
}
