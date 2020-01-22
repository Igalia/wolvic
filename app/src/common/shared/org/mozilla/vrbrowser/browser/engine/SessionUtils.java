package org.mozilla.vrbrowser.browser.engine;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

import org.mozilla.gecko.GeckoProfile;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

class SessionUtils {

    private static final String LOGTAG = SystemUtils.createLogtag(SessionUtils.class);

    public static boolean isLocalizedContent(@Nullable String url) {
        return url != null && (url.startsWith("about:") || url.startsWith("data:"));
    }

    public static void vrPrefsWorkAround(Context aContext, Bundle aExtras) {
        File path = GeckoProfile.initFromArgs(aContext, null).getDir();
        String prefFileName = path.getAbsolutePath() + File.separator + "user.js";
        Log.i(LOGTAG, "Creating file: " + prefFileName);
        try (FileOutputStream out = new FileOutputStream(prefFileName)) {
            out.write("pref(\"dom.vr.enabled\", true);\n".getBytes());
            out.write("pref(\"dom.vr.external.enabled\", true);\n".getBytes());
            out.write("pref(\"webgl.enable-surface-texture\", true);\n".getBytes());
            // Enable MultiView draft extension
            out.write("pref(\"webgl.enable-draft-extensions\", true);\n".getBytes());
            out.write("pref(\"apz.allow_double_tap_zooming\", false);\n".getBytes());
            out.write("pref(\"dom.webcomponents.customelements.enabled\", true);\n".getBytes());
            out.write("pref(\"javascript.options.ion\", true);\n".getBytes());
            out.write("pref(\"media.webspeech.synth.enabled\", false);\n".getBytes());
            // Prevent autozoom when giving a form field focus.
            out.write("pref(\"formhelper.autozoom\", false);\n".getBytes());
            // Disable WebRender until it works with FxR
            out.write("pref(\"gfx.webrender.force-disabled\", true);\n".getBytes());
            out.write("pref(\"signon.rememberSignons\", false);\n".getBytes());
            int msaa = SettingsStore.getInstance(aContext).getMSAALevel();
            if (msaa > 0) {
                int msaaLevel = msaa == 2 ? 4 : 2;
                out.write(("pref(\"gl.msaa-level\"," + msaaLevel + ");\n").getBytes());
            }
            addOptionalPref(out, "dom.vr.require-gesture", aExtras);
            addOptionalPref(out, "privacy.reduceTimerPrecision", aExtras);
            if (aExtras != null && aExtras.getBoolean("media.autoplay.enabled", false)) {
                // Enable playing audios without gesture (used for gfx automated testing)
                out.write("pref(\"media.autoplay.enabled.user-gestures-needed\", false);\n".getBytes());
                out.write("pref(\"media.autoplay.enabled.ask-permission\", false);\n".getBytes());
                out.write("pref(\"media.autoplay.default\", 0);\n".getBytes());
            }
        } catch (FileNotFoundException e) {
            Log.e(LOGTAG, "Unable to create file: '" + prefFileName + "' got exception: " + e.toString());
        } catch (IOException e) {
            Log.e(LOGTAG, "Unable to write file: '" + prefFileName + "' got exception: " + e.toString());
        }
    }

    private static void addOptionalPref(FileOutputStream out, String aKey, Bundle aExtras) throws IOException {
        if (aExtras != null && aExtras.containsKey(aKey)) {
            boolean value = aExtras.getBoolean(aKey);
            out.write(String.format("pref(\"%s\", %s);\n", aKey, value ? "true" : "false").getBytes());
        }
    }
}
