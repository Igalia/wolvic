package com.igalia.wolvic.utils;

import android.util.Log;

import androidx.annotation.IntDef;

import com.igalia.wolvic.BuildConfig;

public class DeviceType {
    // These values need to match those in Device.h
    @IntDef(value = {Unknown, OculusGo, OculusQuest, ViveFocus, ViveFocusPlus, PicoNeo2, PicoG2, PicoNeo3, OculusQuest2, HVR3DoF, HVR6DoF, PicoXR, MetaQuestPro, LynxR1, LenovoA3})
    public @interface Type {}
    public static final int Unknown = 0;
    public static final int OculusGo = 1;
    public static final int OculusQuest = 2;
    public static final int ViveFocus = 3;
    public static final int ViveFocusPlus = 4;
    public static final int PicoNeo2 = 6;
    public static final int PicoG2 = 7;
    public static final int PicoNeo3 = 8;
    public static final int OculusQuest2 = 9;
    public static final int HVR3DoF = 10;
    public static final int HVR6DoF = 11;
    public static final int PicoXR = 12;
    public static final int MetaQuestPro = 13;
    public static final int LynxR1 = 14;
    public static final int LenovoA3 = 15;

    private static @Type int mType = Unknown;

    public static void setType(@Type int aType) {
        String name;
        switch (aType) {
            case OculusGo:
                name = "Oculus Go";
                break;
            case OculusQuest:
                name = "Oculus Quest";
                break;
            case OculusQuest2:
                name = "Oculus Quest 2";
                break;
            case MetaQuestPro:
                name = "Meta Quest Pro";
            case ViveFocus:
                name = "Vive Focus";
                break;
            case ViveFocusPlus:
                name = "Vive Focus Plus";
                break;
            case PicoNeo2:
                name = "Pico Neo 2";
                break;
            case PicoNeo3:
                name = "Pico Neo 3";
                break;
            case PicoG2:
                name = "Pico G2";
                break;
            case PicoXR:
                name = "Pico XR";
                break;
            case LynxR1:
                name = "Lynx-R1";
                break;
            case LenovoA3:
                name = "Lenovo A3";
                break;
            default:
                name = "Unknown Type";
        }
        Log.d("VRB", "Setting device type to: " + name);
        mType = aType;
    }
    public static @Type int getType() {
        return mType;
    }

    public static boolean isOculusBuild() {
        return BuildConfig.FLAVOR_platform.toLowerCase().contains("oculusvr");
    }

    public static boolean isOculus6DOFBuild() {
        return BuildConfig.FLAVOR_platform.equalsIgnoreCase("oculusvr") || BuildConfig.FLAVOR_platform.equalsIgnoreCase("oculusvrStore");
    }

    public static boolean isWaveBuild() {
        return BuildConfig.FLAVOR_platform.toLowerCase().contains("wavevr");
    }

    public static boolean isHVRBuild() {
        return BuildConfig.FLAVOR_platform.toLowerCase().contains("hvr");
    }

    public static boolean isPicoVR() {
        return BuildConfig.FLAVOR_platform.toLowerCase().contains("picovr");
    }

    public static boolean isPicoXR() {
        return BuildConfig.FLAVOR_platform.toLowerCase().contains("picoxr");
    }

    public static boolean isLynx() {
        return BuildConfig.FLAVOR_platform.toLowerCase().contains("lynx");
    }

    public static boolean isSnapdragonSpaces() {
        return BuildConfig.FLAVOR_platform.toLowerCase().contains("spaces");
    }

    public static String getDeviceTypeId() {
        String type = BuildConfig.FLAVOR_platform;
        if (DeviceType.isOculusBuild()) {
            type = "oculusvr";
        } else if (DeviceType.isPicoVR()) {
            type = "picovr";
        } else if (DeviceType.isPicoXR()) {
            type = "picoxr";
        } else if (DeviceType.isWaveBuild()) {
            type = "wavevrStore";
        } else if (DeviceType.isLynx()) {
            type = "lynx";
        } else if (DeviceType.isSnapdragonSpaces()) {
            type = "spaces";
        }

        return type;
    }
}
