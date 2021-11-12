package org.mozilla.vrbrowser.browser.engine;

import android.content.Context;

import android.util.Log;

import androidx.annotation.Nullable;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

class SessionUtils {

    private static final String LOGTAG = SystemUtils.createLogtag(SessionUtils.class);

    private static final String PREFERENCES_FILENAME = "fxr_config.yaml";

    public static boolean isLocalizedContent(@Nullable String url) {
        return url != null && (url.startsWith("about:") || url.startsWith("data:"));
    }

    public static String prepareConfigurationPath(Context aContext) {
        File path = new File(aContext.getFilesDir(), PREFERENCES_FILENAME);

        InputStream yaml = aContext.getResources().openRawResource(R.raw.fxr_config);

        try (FileOutputStream outputStream = new FileOutputStream(path, false)) {
            int read;
            int bufferSize = 8192;
            byte[] bytes = new byte[bufferSize];
            while ((read = yaml.read(bytes)) != -1) {
                outputStream.write(bytes, 0, read);
            }
        } catch (Exception ex) {
            Log.e(LOGTAG, "Error copying preferences file " + PREFERENCES_FILENAME + ": " + ex.toString());
        }

        return path.getAbsolutePath();
    }

}
