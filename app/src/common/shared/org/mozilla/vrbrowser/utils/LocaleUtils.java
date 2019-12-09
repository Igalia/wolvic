package org.mozilla.vrbrowser.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.ui.adapters.Language;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR1;
import static android.os.Build.VERSION_CODES.N;

public class LocaleUtils {

    private static Locale mSystemLocale;
    private static HashMap<String, Language> mLanguagesCache;

    public static void init() {
        getAllLanguages();
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

    private static HashMap<String, Language> getAllLanguages() {
        if (mLanguagesCache != null) {
            return mLanguagesCache;
        }

        Locale[] locales = Locale.getAvailableLocales();
        mLanguagesCache = new HashMap<>();
        for(Locale temp : locales) {
            String languageId = temp.toLanguageTag();
            String displayName = temp.getDisplayName().substring(0, 1).toUpperCase() + temp.getDisplayName().substring(1);
            Language locale = new Language(languageId, displayName + " [" + languageId + "]");
            mLanguagesCache.put(languageId, locale);
        }

        return mLanguagesCache;
    }

    public static void resetLanguages() {
        String currentLocale = getCurrentLocale();
        mLanguagesCache.values().stream().forEach((language) -> {
            language.setPreferred(language.getId().equals(currentLocale));
        });
    }

    public static Language getCurrentLocaleLanguage() {
        return mLanguagesCache.get(getCurrentLocale());
    }

    public static List<String> getLocalesFromLanguages(@NonNull final List<Language> languages) {
        List<String> result = new ArrayList<>();
        for (Language language : languages) {
            result.add(language.getId());
        }

        return result;
    }

    public static List<String> getPreferredLocales(@NonNull Context context) {
        return LocaleUtils.getLocalesFromLanguages(LocaleUtils.getPreferredLanguages(context));
    }

    public static List<Language> getPreferredLanguages(@NonNull Context aContext) {
        HashMap<String, Language> languages = getAllLanguages();
        List<String> savedLanguages = SettingsStore.getInstance(aContext).getContentLocales();
        List<Language> preferredLanguages = new ArrayList<>();
        if (savedLanguages != null) {
            for (String language : savedLanguages) {
                Language lang = languages.get(language);
                lang.setPreferred(true);
                preferredLanguages.add(lang);
            }

        } else {
            Language currentLanguage = getCurrentLocaleLanguage();
            currentLanguage.setPreferred(true);
            preferredLanguages.add(currentLanguage);
        }

        return preferredLanguages;
    }

    public static List<Language> getAvailableLanguages() {
        HashMap<String, Language> languages = getAllLanguages();
        return languages.values().stream()
                .sorted((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()))
                .collect(Collectors.toList());
    }

    @NonNull
    public static String getVoiceSearchLocale(@NonNull Context aContext) {
        return SettingsStore.getInstance(aContext).getVoiceSearchLocale();
    }

    public static void setVoiceSearchLocale(@NonNull Context context, @NonNull String locale) {
        SettingsStore.getInstance(context).setVoiceSearchLocale(locale);
    }

    @NonNull
    public static String getVoiceSearchLanguage(@NonNull Context aContext) {
        String language = LocaleUtils.getVoiceSearchLocale(aContext);
        return getAllLanguages().get(language).getName();
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
    public static String getDisplayLanguage() {
        return getAllLanguages().get(getCurrentLocale()).getName();
    }

    public static Context setLocale(@NonNull Context context) {
        return updateResources(context, SettingsStore.getInstance(context).getDisplayLocale());
    }

    private static Context updateResources(@NonNull Context context, @NonNull String language) {
        Locale locale = Locale.forLanguageTag(language);
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
        if (locale.equalsIgnoreCase("cmn-Hant-TW")) {
            locale = "zh-Hant-TW";
        } else if (locale.equalsIgnoreCase("cmn-Hans-CN")) {
            locale = "zh-Hans-CN";
        }

        return locale;
    }

    public static String mapToMozillaSpeechLocales(@NonNull String locale) {
        if (locale.equalsIgnoreCase("zh-Hant-TW")) {
            locale = "cmn-Hant-TW";
        } else if (locale.equalsIgnoreCase("zh-Hans-CN")) {
            locale = "cmn-Hans-CN";
        }

        return locale;
    }

    public static class LocalizedLanguage {
        public @StringRes int name;
        public Locale locale;

        private LocalizedLanguage() {}

        public static LocalizedLanguage create(@StringRes int name, @NonNull Locale locale) {
            LocalizedLanguage language = new LocalizedLanguage();
            language.name = name;
            language.locale = locale;

            return language;
        }
    }

    private static List<LocalizedLanguage> localizedSupportedLanguages = Stream.of(
            LocalizedLanguage.create(R.string.settings_language_english, new Locale("en", "US")),
            LocalizedLanguage.create(R.string.settings_language_traditional_chinese, new Locale.Builder().setLanguage("zh").setScript("Hant").setRegion("TW").build()),
            LocalizedLanguage.create(R.string.settings_language_simplified_chinese, new Locale.Builder().setLanguage("zh").setScript("Hans").setRegion("CN").build()),
            LocalizedLanguage.create(R.string.settings_language_japanese, new Locale("ja", "JP")),
            LocalizedLanguage.create(R.string.settings_language_french, new Locale("fr", "FR")),
            LocalizedLanguage.create(R.string.settings_language_german, new Locale("de", "DE")),
            LocalizedLanguage.create(R.string.settings_language_spanish, new Locale("es", "ES")),
            LocalizedLanguage.create(R.string.settings_language_russian, new Locale("ru", "RU")),
            LocalizedLanguage.create(R.string.settings_language_korean, new Locale("ko", "KR")),
            LocalizedLanguage.create(R.string.settings_language_italian, new Locale("it", "IT")),
            LocalizedLanguage.create(R.string.settings_language_danish, new Locale("da", "DK")),
            LocalizedLanguage.create(R.string.settings_language_polish, new Locale("pl", "PL")),
            LocalizedLanguage.create(R.string.settings_language_norwegian, new Locale("nb", "NO")),
            LocalizedLanguage.create(R.string.settings_language_swedish, new Locale("sv", "SE")),
            LocalizedLanguage.create(R.string.settings_language_finnish, new Locale("fi", "FI")),
            LocalizedLanguage.create(R.string.settings_language_dutch, new Locale("nl", "NL"))
    ).collect(Collectors.toList());

    public static String[] getSupportedLocalizedLanguages(@NonNull Context context) {
        return LocaleUtils.localizedSupportedLanguages.stream().map(
                item -> StringUtils.capitalize(StringUtils.getStringByLocale(context, item.name, item.locale))).
                collect(Collectors.toList()).toArray(new String[]{});
    }

    public static List<String> getSupportedLocales() {
        return LocaleUtils.localizedSupportedLanguages.stream().map(
                item -> item.locale.toLanguageTag()).
                collect(Collectors.toList());
    }

    public static int getIndexForSupportedLocale(@NonNull String locale) {
        Optional<LocalizedLanguage> locLang = localizedSupportedLanguages.stream().filter(item -> item.locale.toLanguageTag().equals(locale)).findFirst();
        return locLang.map(localizedLanguage -> localizedSupportedLanguages.indexOf(localizedLanguage)).orElse(0);
    }

    public static String getSupportedLocalizedLanguageForIndex(@NonNull Context context, int index) {
        return StringUtils.capitalize(
                StringUtils.getStringByLocale(
                        context,
                        localizedSupportedLanguages.get(index).name,
                        localizedSupportedLanguages.get(index).locale));
    }

    public static String getSupportedLocaleForIndex(int index) {
        return localizedSupportedLanguages.get(index).locale.toLanguageTag();
    }

    public static String getDefaultSupportedLocale() {
        String locale = getCurrentLocale();
        List<String> supportedLocales = getSupportedLocales();
        if (!supportedLocales.contains(locale)) {
            return supportedLocales.get(0);
        }

        return locale;
    }

}
