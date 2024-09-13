package com.igalia.wolvic;

public class PlatformSystemCheck extends SystemCheck {

    @Override
    public boolean isOSVersionCompatible() { return true; }

    @Override
    public String minSupportedVersion() { return ""; }
}
