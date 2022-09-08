package com.igalia.wolvic.browser.api.impl;

import androidx.annotation.NonNull;

import com.igalia.wolvic.browser.api.WRuntime;
import com.igalia.wolvic.crashreporting.CrashReporterService;

public class CrashReporterServiceImpl extends CrashReporterService {
    @NonNull
    @Override
    protected WRuntime.CrashReportIntent createCrashReportIntent() {
        // TODO: implement this.
        return new WRuntime.CrashReportIntent("", "", "", "");
    }
}
