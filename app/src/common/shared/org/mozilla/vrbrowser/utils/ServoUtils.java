package org.mozilla.vrbrowser.utils;

import android.content.Context;
import android.util.Log;

import org.mozilla.geckoview.GeckoSession;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class ServoUtils {
    private static final String SESSION_CLASSNAME = "org.mozilla.servo.ServoSession";
    private static final String WHITELIST_CLASSNAME = "org.mozilla.servo.ServoWhiteList";
    private static final String LOGTAG = "ServoUtils";
    private static Object mServoWhiteList = null;

    public static boolean isServoAvailable() {
        try {
            Class.forName(SESSION_CLASSNAME);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isInstanceOfServoSession(Object obj) {
        try {
            return Class.forName(SESSION_CLASSNAME).isInstance(obj);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static GeckoSession createServoSession(Context context) {
        try {
            Class clazz = Class.forName(SESSION_CLASSNAME);
            Constructor<?> constructor = clazz.getConstructor(Context.class);
            return (GeckoSession) constructor.newInstance(context);
        } catch (Exception e) {
            Log.e(LOGTAG, "Can't load or instanciate ServoSession: " + e);
            return null;
        }
    }

    public static boolean isUrlInServoWhiteList(Context context, String url) {
        if (isServoAvailable()) {
            try {
                Class clazz = Class.forName(WHITELIST_CLASSNAME);
                if (mServoWhiteList == null) {
                    Constructor<?> constructor = clazz.getConstructor(Context.class);
                    mServoWhiteList = constructor.newInstance(context);
                }
                Method isAllowed = clazz.getMethod("isAllowed", String.class);
                return (boolean) isAllowed.invoke(mServoWhiteList, url);
            } catch (Exception e) {
                Log.e(LOGTAG, "Failed to call ServoWhiteList::isAllowed: " + e);
                return false;
            }
        } else {
            return false;
        }
    }
}
