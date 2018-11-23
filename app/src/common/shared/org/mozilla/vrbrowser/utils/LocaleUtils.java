package org.mozilla.vrbrowser.utils;

import android.content.Context;

import org.mozilla.vrbrowser.R;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;

public class LocaleUtils {

    @NonNull
    public static String getCurrentLocale() {
        String cCode = Locale.getDefault().getCountry();
        String lCode = Locale.getDefault().getLanguage();
        return lCode + "-" + cCode;
    }

    @NonNull
    public static List<String> getSupportedVoiceLanguages(Context aContext) {
        return Arrays.asList(aContext.getResources().getStringArray(
                R.array.developer_options_voice_search_languages_values));
    }

    @NonNull
    public static String getDefaultVoiceSearchLanguage(Context aContext) {
        String language = getCurrentLocale();
        List<String> supportedLanguages = getSupportedVoiceLanguages(aContext);
        if (!supportedLanguages.contains(language)) {
            return supportedLanguages.get(0);
        }

        return language;
    }

}
