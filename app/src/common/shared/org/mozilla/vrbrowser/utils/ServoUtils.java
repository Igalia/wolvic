package org.mozilla.vrbrowser.utils;

import android.content.Context;
import android.util.Log;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.VRBrowserActivity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class ServoUtils {
    private static final String SESSION_CLASSNAME = "org.mozilla.servo.ServoSession";
    private static final String ALLOWLIST_CLASSNAME = "org.mozilla.servo.ServoAllowList";
    private static final String LOGTAG = "ServoUtils";
    private static Object mServoAllowList = null;
    private static long mVRContext;

    public static boolean isServoAvailable() {
        try {
            Class.forName(SESSION_CLASSNAME);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static void setExternalContext(long aContext) {
      mVRContext = aContext;
    }

    public static boolean isInstanceOfServoSession(Object obj) {
        try {
            return Class.forName(SESSION_CLASSNAME).isInstance(obj);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static GeckoSession createServoSession(Context context) {
        boolean layersEnabled = SettingsStore.getInstance(context).getLayersEnabled();
        try {
            Class clazz = Class.forName(SESSION_CLASSNAME);
            Constructor<?> constructor = clazz.getConstructor(Context.class, long.class, boolean.class);
            return (GeckoSession) constructor.newInstance(context, mVRContext, layersEnabled);
        } catch (Exception e) {
            Log.e(LOGTAG, "Can't load or instanciate ServoSession: " + e);
            return null;
        }
    }

    public static boolean isUrlInServoAllowList(Context context, String url) {
        if (isServoAvailable()) {
            try {
                Class clazz = Class.forName(ALLOWLIST_CLASSNAME);
                if (mServoAllowList == null) {
                    Constructor<?> constructor = clazz.getConstructor(Context.class);
                    mServoAllowList = constructor.newInstance(context);
                }
                Method isAllowed = clazz.getMethod("isAllowed", String.class);
                return (boolean) isAllowed.invoke(mServoAllowList, url);
            } catch (Exception e) {
                Log.e(LOGTAG, "Failed to call ServoAllowList::isAllowed: " + e);
                return false;
            }
        } else {
            return false;
        }
    }
}
