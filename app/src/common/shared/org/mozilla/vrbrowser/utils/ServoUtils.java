package org.mozilla.vrbrowser.utils;

import android.content.Context;
import android.util.Log;

import org.mozilla.geckoview.GeckoSession;

import java.lang.reflect.Constructor;

public class ServoUtils {
    private static final String CLASSNAME = "org.mozilla.servo.ServoSession";
    private static final String LOGTAG = "ServoUtils";

    public static boolean isServoAvailable() {
        try {
            Class.forName(CLASSNAME);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isInstanceOfServoSession(Object obj) {
        try {
            return Class.forName(CLASSNAME).isInstance(obj);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static GeckoSession createServoSession(Context context) {
        try {
            Class servoClass = Class.forName(CLASSNAME);
            Constructor<?> constructor = servoClass.getConstructor(Context.class);
            return (GeckoSession) constructor.newInstance(context);
        } catch (Exception e) {
            Log.e(LOGTAG, "Can't load or instanciate ServoSession: " + e);
            return null;
        }
    }
}
