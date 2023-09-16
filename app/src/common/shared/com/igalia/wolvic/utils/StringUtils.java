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

    /**
     * The version code is composed like: yDDDHHmm
     *  * y   = Double digit year, with 16 substracted: 2017 -> 17 -> 1
     *  * DDD = Day of the year, pad with zeros if needed: September 6th -> 249
     *  * HH  = Hour in day (00-23)
     *  * mm  = Minute in hour
     *
     * For September 6th, 2017, 9:41 am this will generate the versionCode: 12490941 (1-249-09-41).
     *
     * @param aVersionCode Application version code minus the leading architecture digit.
     * @return String The converted date in the format yyyy-MM-dd
     */
    public static String versionCodeToDate(final @NonNull Context context, final int aVersionCode) {
        String versionCode = Integer.toString(aVersionCode - 100000000);

        String formatted;
        try {
            int year = Integer.parseInt(versionCode.substring(0, 1)) + 2016;
            int dayOfYear = Integer.parseInt(versionCode.substring(1, 4));

            GregorianCalendar cal = (GregorianCalendar)GregorianCalendar.getInstance();
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.DAY_OF_YEAR, dayOfYear);

            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            formatted = format.format(cal.getTime());

        } catch (StringIndexOutOfBoundsException | NumberFormatException e) {
            formatted = context.getString(R.string.settings_version_developer);
        }

        return formatted;
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
