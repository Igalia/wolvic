package org.mozilla.vrbrowser.browser.engine;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.gecko.GeckoProfile;
import org.mozilla.vrbrowser.BuildConfig;
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

    private static void checkPrefsFile(Context aContext, @NonNull String path) {
        int lastVersionCode = SettingsStore.getInstance(aContext).getPrefsLastResetVersionCode();
        if (BuildConfig.VERSION_CODE > lastVersionCode) {
            try {
                File prefsFile = new File(path);
                if (prefsFile.exists()) {
                    if (prefsFile.delete()) {
                        Log.d(LOGTAG, "Deleted prefs cache: " + path);
                        SettingsStore.getInstance(aContext).setPrefsLastResetVersionCode(BuildConfig.VERSION_CODE);
                        return;
                    }
                    Log.e(LOGTAG, "Failed to delete prefs cache file.");
                } else {
                    // Probably the first run. Set the version code so we do not delete the newly created pref cache
                    // on the next run.
                    SettingsStore.getInstance(aContext).setPrefsLastResetVersionCode(BuildConfig.VERSION_CODE);
                }
            } catch (Exception e) {
                Log.e(LOGTAG, "Failed to delete prefs cache file. Caught: " + e.getLocalizedMessage());
            }
        } else {
            Log.d(LOGTAG, "Version code is unchanged.");
        }
    }

    public static void vrPrefsWorkAround(Context aContext) {
        if (EngineProvider.INSTANCE.isRuntimeCreated()) {
            throw new IllegalStateException("vrPrefsWorkAround must be called before creating the runtime");
        }

        File path = GeckoProfile.initFromArgs(aContext, null).getDir();

        if (BuildConfig.TASKCLUSTER_BUILD) {
            checkPrefsFile(aContext, path.getAbsolutePath() + File.separator + "prefs.js");
        }

        String prefFileName = path.getAbsolutePath() + File.separator + "user.js";
        Log.i(LOGTAG, "Creating file: " + prefFileName);
        try (FileOutputStream out = new FileOutputStream(prefFileName)) {
            out.write("user_pref(\"dom.vr.enabled\", true);\n".getBytes());
            out.write("user_pref(\"dom.vr.external.enabled\", true);\n".getBytes());
            out.write("user_pref(\"dom.vr.webxr.enabled\", true);\n".getBytes());
            out.write("user_pref(\"webgl.enable-surface-texture\", true);\n".getBytes());
            if (BuildConfig.DEBUG && SettingsStore.getInstance(aContext).isWebGLOutOfProcess()) {
                out.write("user_pref(\"webgl.out-of-process\", true);\n".getBytes());
            } else {
                out.write("user_pref(\"webgl.out-of-process\", false);\n".getBytes());
            }
            // Enable MultiView draft extension
            out.write("user_pref(\"webgl.enable-draft-extensions\", true);\n".getBytes());
            out.write("user_pref(\"dom.webcomponents.customelements.enabled\", true);\n".getBytes());
            out.write("user_pref(\"javascript.options.ion\", true);\n".getBytes());
            out.write("user_pref(\"media.webspeech.synth.enabled\", false);\n".getBytes());
            // Disable WebRender until it works with FxR
            out.write("user_pref(\"gfx.webrender.force-disabled\", true);\n".getBytes());
            // Disable web extension process until it is able to restart.
            out.write("user_pref(\"extensions.webextensions.remote\", false);\n".getBytes());
            out.write("user_pref(\"media.cubeb.output_voice_routing\", false);\n)".getBytes());
            int msaa = SettingsStore.getInstance(aContext).getMSAALevel();
            if (msaa > 0) {
                int msaaLevel = msaa == 2 ? 4 : 2;
                out.write(("user_pref(\"webgl.msaa-samples\"," + msaaLevel + ");\n").getBytes());
                out.write("user_pref(\"webgl.msaa-force\", true);\n".getBytes());
            } else {
                out.write("user_pref(\"webgl.msaa-force\", false);\n".getBytes());
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
            out.write(String.format("user_pref(\"%s\", %s);\n", aKey, value ? "true" : "false").getBytes());
        }
    }
}
