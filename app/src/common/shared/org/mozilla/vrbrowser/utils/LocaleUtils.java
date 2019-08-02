package org.mozilla.vrbrowser.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.ui.adapters.Language;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR1;
import static android.os.Build.VERSION_CODES.N;

public class LocaleUtils {

    private static Locale mSystemLocale;
    private static HashMap<String, Language> mLanguagesCache;

    public static void init(Context context) {
        getAllLanguages();
        SessionStore.get().setLocales(
                LocaleUtils.getLanguageIdsFromList(getPreferredLanguages(context)));
    }

    public static void saveSystemLocale() {
        mSystemLocale = Locale.getDefault();
    }

    @NonNull
    public static String getSystemLocale() {
        return mSystemLocale.toLanguageTag();
    }

    @NonNull
    public static String getCurrentLocale() {
        return Locale.getDefault().toLanguageTag();
    }

    public static HashMap<String, Language> getAllLanguages() {
        if (mLanguagesCache != null)
            return mLanguagesCache;

        Locale[] locales = Locale.getAvailableLocales();
        mLanguagesCache = new HashMap<>();
        for(Locale temp : locales) {
            String languageId = temp.toLanguageTag();
            String displayName = temp.getDisplayName().substring(0, 1).toUpperCase() + temp.getDisplayName().substring(1);
            if (languageId.equals(getCurrentLocale()))
                displayName = "(Default) " + displayName;
            Log.d("Locale", " [" + languageId +  "]");
            mLanguagesCache.put(languageId, new Language(languageId, displayName + " [" + languageId + "]"));
        }

        return mLanguagesCache;
    }

    public static Language getCurrentLocaleLanguage() {
        return mLanguagesCache.get(getCurrentLocale());
    }

    public static List<String> getLanguageIdsFromList(@NonNull final List<Language> languages) {
        List<String> result = new ArrayList<>();
        for (Language language : languages) {
            result.add(language.getId());
        }

        return result;
    }

    public static List<Language> getPreferredLanguages(@NonNull Context aContext) {
        HashMap<String, Language> languages = getAllLanguages();
        List<String> savedLanguages = SettingsStore.getInstance(aContext).getContentLocales();
        List<Language> preferredLanguages = new ArrayList<>();
        for (String language : savedLanguages)
            preferredLanguages.add(languages.get(language));

        if (!savedLanguages.stream().anyMatch(str -> str.trim().equals(getCurrentLocale())))
            preferredLanguages.add(getCurrentLocaleLanguage());

        return preferredLanguages;
    }

    public static List<Language> getAvailableLanguages(@NonNull Context aContext) {
        HashMap<String, Language> languages = getAllLanguages();
        List<String> savedLanguages = SettingsStore.getInstance(aContext).getContentLocales();
        List<Language> availableLanguages = languages.values().stream()
                .filter((language) ->
                        !(language.getId().equals(getCurrentLocale()) ||
                        savedLanguages.stream().anyMatch(str -> str.trim().equals(language.getId()))))
                .sorted((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()))
                .collect(Collectors.toList());

        return availableLanguages;
    }

    @NonNull
    public static List<String> getSupportedVoiceLanguages(@NonNull Context aContext) {
        return Arrays.asList(aContext.getResources().getStringArray(
                R.array.developer_options_voice_search_languages_values));
    }

    @NonNull
    public static List<String> getSupportedDisplayLanguages(@NonNull Context aContext) {
        return Arrays.asList(aContext.getResources().getStringArray(
                R.array.developer_options_display_languages_values));
    }

    @NonNull
    public static String getDefaultVoiceSearchLocale(@NonNull Context aContext) {
        String locale = getCurrentLocale();
        List<String> supportedLanguages = getSupportedVoiceLanguages(aContext);
        if (!supportedLanguages.contains(locale)) {
            return supportedLanguages.get(0);
        }

        return locale;
    }

    @NonNull
    public static String getVoiceSearchLocale(@NonNull Context aContext) {
        String locale = SettingsStore.getInstance(aContext).getVoiceSearchLocale();
        return mapOldLocaleToNew(locale);
    }

    public static void setVoiceSearchLocale(@NonNull Context context, @NonNull String locale) {
        SettingsStore.getInstance(context).setVoiceSearchLocale(locale);
    }

    @NonNull
    public static String getVoiceSearchLanguageString(@NonNull Context aContext) {
        String language = LocaleUtils.getVoiceSearchLocale(aContext);
        return getAllLanguages().get(language).getName();
    }

    @NonNull
    public static String getDefaultDisplayLocale(@NonNull Context aContext) {
        String locale = getCurrentLocale();
        List<String> supportedLanguages = getSupportedDisplayLanguages(aContext);
        if (!supportedLanguages.contains(locale)) {
            return supportedLanguages.get(0);
        }

        return locale;
    }

    @NonNull
    public static String getDisplayLocale(Context context) {
        String locale = SettingsStore.getInstance(context).getDisplayLocale();
        return mapOldLocaleToNew((locale));
    }

    public static void setDisplayLocale(@NonNull Context context, @NonNull String locale) {
        SettingsStore.getInstance(context).setDisplayLocale(locale);
    }

    @NonNull
    public static String getDisplayCurrentLanguageString() {
        return getAllLanguages().get(getCurrentLocale()).getName();
    }

    public static Context setLocale(@NonNull Context context) {
        return updateResources(context, SettingsStore.getInstance(context).getDisplayLocale());
    }

    private static Context updateResources(@NonNull Context context, @NonNull String language) {
        String[] localeStr = language.split("-");
        Locale locale = new Locale(localeStr[0], localeStr.length > 1 ? localeStr[1] : "");
        Locale.setDefault(locale);

        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        if (Build.VERSION.SDK_INT >= JELLY_BEAN_MR1) {
            config.setLocale(locale);
            context = context.createConfigurationContext(config);
        } else {
            config.locale = locale;
            res.updateConfiguration(config, res.getDisplayMetrics());
        }
        return context;
    }

    public static Locale getLocale(@NonNull Resources res) {
        Configuration config = res.getConfiguration();
        return (Build.VERSION.SDK_INT >= N) ? config.getLocales().get(0) : config.locale;
    }

    public static String mapOldLocaleToNew(@NonNull String locale) {
        if (locale.equalsIgnoreCase("cmn-Hant-TW"))
            locale = "zh-Hant-TW";
        else if (locale.equalsIgnoreCase("cmn-Hans-CN"))
            locale = "zh-Hans-CN";

        return locale;
    }

    public static String mapToMozillaSpeechLocales(@NonNull String locale) {
        if (locale.equalsIgnoreCase("zh-Hant-TW"))
            locale = "cmn-Hant-TW";
        else if (locale.equalsIgnoreCase("zh-Hans-CN"))
            locale = "cmn-Hans-CN";

        return locale;
    }

}
