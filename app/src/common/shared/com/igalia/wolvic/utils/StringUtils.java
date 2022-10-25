package com.igalia.wolvic.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.igalia.wolvic.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
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
}
