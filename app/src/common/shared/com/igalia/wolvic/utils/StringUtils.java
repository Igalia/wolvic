package com.igalia.wolvic.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Locale;

public class StringUtils {
    static final String LOGTAG = SystemUtils.createLogtag(StringUtils.class);

    @NonNull
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static String getStringByLocale(Context context, int id, Locale locale) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(locale);
        return context.createConfigurationContext(configuration).getResources().getString(id);
    }

    public static String removeSpaces(@NonNull String aText) {
        return aText.replaceAll("\\s", "");
    }

    public static boolean isEmpty(String aString) {
        return aString == null || aString.length() == 0;
    }

    public static boolean isEmpty(CharSequence aSequence) {
        return aSequence == null || aSequence.length() == 0;
    }


    public static String getLastCharacter(String aText) {
        if (!isEmpty(aText)) {
            return aText.substring(aText.length() - 1);
        }

        return "";
    }

    public static String removeLastCharacter(String aText) {
        if (!isEmpty(aText)) {
            return aText.substring(0, aText.length() - 1);
        }
        return "";
    }

    public static String removeRange(@NonNull String aText, int aStart, int aEnd) {
        String start = "";
        if (aStart > 0) {
            start = aText.substring(0, aStart);
        }
        String end = "";
        if (aEnd < aText.length() - 1) {
            end = aText.substring(aEnd);
        }
        return start + end;
    }

    public static boolean contains(String[] aTarget, String aText) {
        for (String str: aTarget) {
            if (str.equals(aText)) {
                return true;
            }
        }

        return false;
    }

    public static long charCount(@NonNull String aString, char target) {
        return aString.chars().filter(ch -> ch == target).count();
    }

    @NonNull
    public static String capitalize(@NonNull String input) {
        try {
            return input.substring(0, 1).toUpperCase() + input.substring(1);
        } catch (StringIndexOutOfBoundsException e) {
            Log.e(LOGTAG, "String index is out of bound at capitalize(). " + e);
            return input;
        }
    }

    public static int compareVersions(@NonNull String version1, @NonNull String version2) {
        if (version1.isEmpty() && version2.isEmpty())
            return 0;
        if (version1.isEmpty())
            return -1;
        if (version2.isEmpty())
            return 1;

        String[] components1 = version1.split("\\.");
        String[] components2 = version2.split("\\.");
        int len = Math.max(components1.length, components2.length);

        for (int i = 0; i < len; i++) {
            String component1 = i < components1.length ? components1[i] : "0";
            String component2 = i < components2.length ? components2[i] : "0";
            try {
                int num1 = Integer.parseInt(component1);
                int num2 = Integer.parseInt(component2);
                int comparison = Integer.compare(num1, num2);
                if (comparison != 0) {
                    return comparison;
                }
            } catch (NumberFormatException e) {
                int comparison = component1.compareTo(component2);
                if (comparison != 0) {
                    return comparison;
                }
            }
        }
        return 0;
    }
}
