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
    private boolean browserActivityStarted = false;
    private PlatformSystemCheck platformSystemCheck = new PlatformSystemCheck();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (platformSystemCheck.isOSVersionCompatible()) {
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


    private void showIncompatibleOSDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.incompatible_os_version_title)
            .setMessage(getString(R.string.incompatible_os_version_message, platformSystemCheck.minSupportedVersion()))
            .setOnDismissListener((dialog) -> finish())
            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }
}