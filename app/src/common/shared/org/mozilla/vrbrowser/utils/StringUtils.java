package org.mozilla.vrbrowser.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

import androidx.annotation.NonNull;

import java.util.Locale;

public class StringUtils {
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
}
