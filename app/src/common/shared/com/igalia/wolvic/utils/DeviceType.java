package com.igalia.wolvic.utils;

import android.util.Log;

import androidx.annotation.IntDef;

import com.igalia.wolvic.BuildConfig;

public class DeviceType {
    // These values need to match those in Device.h
    @IntDef(value = {Unknown, OculusGo, OculusQuest, ViveFocus, ViveFocusPlus, PicoNeo2, PicoG2, PicoNeo3, HVR3DoF, HVR6DoF})
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

    // These values are not present in Device.h yet but are needed for the WebXR UI
    public static final int HVR3DoF = 10;
    public static final int HVR6DoF = 11;

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

    public static String getDeviceTypeId() {
        String type = BuildConfig.FLAVOR_platform;
        if (DeviceType.isOculusBuild()) {
            type = "oculusvr";
        } else if (DeviceType.isPicoVR()) {
            type = "picovr";
        } else if (DeviceType.isWaveBuild()) {
            type = "wavevrStore";
        }

        return type;
    }
}
