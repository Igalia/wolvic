package org.mozilla.vrbrowser.input;

import android.util.Log;

public class DeviceType {
    // These values need to match those in Device.h
    public static final int Unknown = 0;
    public static final int OculusGo = 1;
    public static final int OculusQuest = 2;
    private static int mType = Unknown;
    public static void setType(int aType) {
        String name = "Unknown Type";
        if (aType == OculusGo) {
            name = "Oculus Go";
        } else if (aType == OculusQuest) {
            name = "Oculus Quest";
        }
        Log.d("VRB", "Setting device type to: " + name);
        mType = aType;
    }
    public static int getType() {
        return mType;
    }
}
