package com.igalia.wolvic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.igalia.wolvic.utils.SystemUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

public class VersionCheckActivity extends Activity {
    private static final String META_OS_VERSION = "ro.vros.build.version";
    private static final int MIN_META_OS_VERSION_WITH_KHR_LOADER = 62;
    static final String LOGTAG = SystemUtils.createLogtag(VersionCheckActivity.class);
    private int minSupportedVersion = 0;
    private boolean browserActivityStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (isOSVersionCompatible()) {
            Intent receivedIntent = getIntent();
            Bundle extras = receivedIntent.getExtras();

            // Start VRBrowserActivity if OS version is compatible
            Intent intent = new Intent(this, VRBrowserActivity.class);
            if (extras != null)
                intent.putExtras(extras);

            startActivity(intent);
            browserActivityStarted = true;
            finish();
        } else {
            // Show dialog if OS version is incompatible
            showIncompatibleOSDialog();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!browserActivityStarted)
            System.exit(0);
    }

    private static String getSystemProperty(String key) {
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

    private boolean isOSVersionCompatible() {
        if (BuildConfig.FLAVOR_platform.equals("oculusvr")) {
            minSupportedVersion = MIN_META_OS_VERSION_WITH_KHR_LOADER;
            String osVersion = getSystemProperty(META_OS_VERSION);
            Log.i(LOGTAG, "Checking that OS version is at least " + minSupportedVersion + " (found " + osVersion + ")");
            try {
                if (osVersion == null || Integer.parseInt(osVersion) < MIN_META_OS_VERSION_WITH_KHR_LOADER)
                    return false;
            } catch (NumberFormatException e) {
                Log.e(LOGTAG, "Failed to parse OS version: " + osVersion);
                return false;
            }
        }
        return true;
    }

    private void showIncompatibleOSDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.incompatible_os_version_title)
            .setMessage(getString(R.string.incompatible_os_version_message, minSupportedVersion))
            .setOnDismissListener((dialog) -> finish())
            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }
}