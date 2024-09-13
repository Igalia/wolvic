package com.igalia.wolvic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public abstract class SystemCheck {
    public abstract boolean isOSVersionCompatible();
    public abstract String minSupportedVersion();

    protected String getSystemProperty(String key) {
        String value = null;
        try {
            Process process = Runtime.getRuntime().exec("getprop " + key);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            value = reader.readLine();
            reader.close();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return value;
    }
}
