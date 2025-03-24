package com.igalia.wolvic.utils;

import static android.os.Build.VERSION_CODES.N;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.ui.adapters.Language;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LocaleUtils {

    public static final String DEFAULT_LANGUAGE_ID = "default";
    public static final String FALLBACK_LANGUAGE_TAG = "en-US";

    private static HashMap<String, Language> mLanguagesCache;
    private static Map<String, Language> mSupportedLanguagesCache;

    public static Context init(@NonNull Context aContext) {
        getLanguages(aContext);
        getSupportedLocalizedLanguages(aContext);

        String languageTag;
        String displayId = getDisplayLanguageId(aContext);
        if (displayId.equals(DEFAULT_LANGUAGE_ID)) {
            languageTag = getClosestSupportedLanguageTag(getDeviceLocale().toLanguageTag());

        } else {
            languageTag = getDisplayLanguageTag(aContext);
        }

        Language language = mLanguagesCache.get(languageTag);
        if (language == null) {
            language = mLanguagesCache.get(FALLBACK_LANGUAGE_TAG);
        }

        return setLocale(aContext, language);
    }

    public static Context update(@NonNull Context aContext, @NonNull Language language) {
        Context newContext = setLocale(aContext, language);
        getLanguages(newContext);
        getSupportedLocalizedLanguages(newContext);
        setPreferredLanguages(newContext, getPreferredLanguages(newContext));

        return newContext;
    }

    private static HashMap<String, Language> getLanguages(@NonNull Context context) {
        Locale[] locales = Locale.getAvailableLocales();
        mLanguagesCache = new LinkedHashMap<>();
        mLanguagesCache.put(DEFAULT_LANGUAGE_ID, new Language(
                getDeviceLocale(),
                context.getString(R.string.settings_language_follow_device)));

        Stream.of(locales)
                .sorted((o1, o2) -> o1.getDisplayName().compareTo(o2.getDisplayName()))
                .forEachOrdered(item -> {
                    Language locale = new Language(item);
                    mLanguagesCache.put(item.toLanguageTag(), locale);
                });

        return mLanguagesCache;
    }

    private static void resetLanguages() {
        mLanguagesCache.values().forEach((language) -> {
            if (language == getDeviceLanguage()) {
                language.setPreferred(true);

            } else {
                language.setPreferred(false);
            }
        });
    }

    private static Language getDeviceLanguage() {
        return mLanguagesCache.get(getDeviceLocale().toLanguageTag());
    }

    private static Locale getDeviceLocale() {
        return Resources.getSystem().getConfiguration().getLocales().get(0);
    }

    @NonNull
    private static List<String> getLanguageTagsForLanguages(final List<Language> languages) {
        List<String> result = new ArrayList<>();
        if (languages != null) {
            for (Language language : languages) {
                result.add(language.getLanguageTag());
            }
        }

        return result;
    }

    @NonNull
    private static List<String> getIdsForLanguages(final List<Language> languages) {
        if (languages != null) {
            return mLanguagesCache
                    .entrySet()
                    .stream()
                    .filter(entry -> languages.contains(entry.getValue()))
                    .sorted((o1, o2) -> languages.indexOf(o1.getValue()) - languages.indexOf(o2.getValue()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    @NonNull
    private static List<Language> getLanguagesForIds(final List<String> languageIds) {
        if (languageIds != null) {
            return mLanguagesCache
                    .entrySet()
                    .stream()
                    .filter(entry -> languageIds.contains(entry.getKey()))
                    .sorted((o1, o2) -> languageIds.indexOf(o1.getKey()) - languageIds.indexOf(o2.getKey()))
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    @NonNull
    public static String getDefaultLanguageId() {
        return DEFAULT_LANGUAGE_ID;
    }

    // Preferred and Available language methods

    @NonNull
    public static List<String> getPreferredLanguageTags(@NonNull Context context) {
        return LocaleUtils.getLanguageTagsForLanguages(LocaleUtils.getPreferredLanguages(context));
    }

    @NonNull
    public static List<Language> getPreferredLanguages(@NonNull Context aContext) {
        // We can't us stream here because an Android 24/25 bug the makes the stream implementation not top respect the order when iterating
        // https://android.googlesource.com/platform/libcore/+/7ae7ae73754c8b82a2e396098e35553d404c69ef%5E%21/#F0
        List<String> savedLanguageIds = SettingsStore.getInstance(aContext).getContentLocales();
        getLanguages(aContext);
        List<Language> preferredLanguages = getLanguagesForIds(savedLanguageIds);
        preferredLanguages.forEach(language -> language.setPreferred(true));

        if (savedLanguageIds == null || savedLanguageIds.isEmpty()) {
            Language lang = mLanguagesCache.get(DEFAULT_LANGUAGE_ID);
            if (lang != null) {
                lang.setPreferred(true);
                preferredLanguages.add(lang);
            }
        }

        return preferredLanguages;
    }

    public static void setPreferredLanguages(@NonNull Context context, List<Language> languages) {
        SessionStore.get().setLocales(
                LocaleUtils.getLanguageTagsForLanguages(languages));
        SettingsStore.getInstance(context).setContentLocales(
                LocaleUtils.getIdsForLanguages(languages));
    }

    public static void resetPreferredLanguages(@NonNull Context context) {
        SettingsStore.getInstance(context).setContentLocales(Collections.emptyList());
        SessionStore.get().setLocales(Collections.emptyList());
        resetLanguages();
    }

    public static List<Language> getAvailableLanguages(@NonNull Context aContext) {
        return new ArrayList<>(mLanguagesCache.values());
    }

    // Voice Language Methods

    @NonNull
    public static String getVoiceSearchLanguageId(Context context) {
        String languageId = SettingsStore.getInstance(context).getVoiceSearchLocale();
        if (languageId == null) {
            languageId = DEFAULT_LANGUAGE_ID;
        }
        return languageId;
    }

    public static String getVoiceLanguageName(@NonNull Context aContext, @NonNull String language) {
        if (language.equals(LocaleUtils.DEFAULT_LANGUAGE_ID)) {
            return aContext.getString(R.string.settings_language_follow_device);
        } else {
            Locale locale = Locale.forLanguageTag(language);
            return StringUtils.capitalize(locale.getDisplayLanguage(locale));
        }
    }

    public static void setVoiceSearchLanguageId(@NonNull Context context, @NonNull String languageId) {
        SettingsStore.getInstance(context).setVoiceSearchLocale(languageId);
    }

    // Display Language Methods

    @NonNull
    public static String getDisplayLanguageId(@NonNull Context context) {
        String languageId = SettingsStore.getInstance(context).getDisplayLocale();
        if (languageId == null) {
            languageId = DEFAULT_LANGUAGE_ID;
        }
        return languageId;
    }

    @NonNull
    private static String getDisplayLanguageTag(@NonNull Context aContext) {
        String languageId = getDisplayLanguageId(aContext);
        Language language = mSupportedLanguagesCache.get(languageId);
        if (language != null) {
            return language.getLanguageTag();
        }

        return getClosestSupportedLanguageTag(languageId);
    }

    public static Language getDisplayLanguage(@NonNull Context aContext) {
        String languageId = getDisplayLanguageId(aContext);
        Language language = getSupportedLocalizedLanguages(aContext).get(languageId);
        if (language == null) {
            language = mSupportedLanguagesCache.get(FALLBACK_LANGUAGE_TAG);
        }
        return language;
    }

    public static void setDisplayLanguageId(@NonNull Context context, @NonNull String languageId) {
        SettingsStore.getInstance(context).setDisplayLocale(languageId);
    }

    private static Context setLocale(@NonNull Context context, @NonNull Language language) {
        return updateResources(context, language.getLanguageTag());
    }

    private static Context updateResources(@NonNull Context context, @NonNull String language) {
        Locale locale = Locale.forLanguageTag(language);
        Locale.setDefault(locale);

        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        config.setLocale(locale);
        context = context.createConfigurationContext(config);
        return context;
    }

    public static Locale getLocale(@NonNull Resources res) {
        Configuration config = res.getConfiguration();
        return config.getLocales().get(0);
    }

    public static String mapToMozillaSpeechLocales(@NonNull String locale) {
        if (locale.equalsIgnoreCase("zh-Hant-TW")) {
            locale = "cmn-Hant-TW";
        } else if (locale.equalsIgnoreCase("zh-Hans-CN")) {
            locale = "cmn-Hans-CN";
        }

        return locale;
    }

    private static Map<String, Language> getSupportedLocalizedLanguages(@NonNull Context context) {
        mSupportedLanguagesCache = new LinkedHashMap<String, Language>() {{
            Locale locale = new Locale("en","US");
            put(locale.toLanguageTag(), new Language(locale, StringUtils.getStringByLocale(context, R.string.settings_language_english_us, locale)));
            locale = new Locale("en","GB");
            put(locale.toLanguageTag(), new Language(locale, StringUtils.getStringByLocale(context, R.string.settings_language_english_uk, locale)));
            locale = new Locale.Builder().setLanguage("zh").setScript("Hans").setRegion("CN").build();
            put(locale.toLanguageTag(), new Language(locale, StringUtils.getStringByLocale(context, R.string.settings_language_simplified_chinese, locale)));
            locale = new Locale("ja","JP");
            put(locale.toLanguageTag(), new Language(locale, StringUtils.getStringByLocale(context, R.string.settings_language_japanese, locale)));
            locale = new Locale("fr","FR");
            put(locale.toLanguageTag(), new Language(locale, StringUtils.getStringByLocale(context, R.string.settings_language_french, locale)));
            locale = new Locale("de","DE");
            put(locale.toLanguageTag(), new Language(locale, StringUtils.getStringByLocale(context, R.string.settings_language_german, locale)));
            locale = new Locale("es","ES");
            put(locale.toLanguageTag(), new Language(locale, StringUtils.getStringByLocale(context, R.string.settings_language_spanish_spain, locale)));
            locale = new Locale("es");
            put(locale.toLanguageTag(), new Language(locale, StringUtils.getStringByLocale(context, R.string.settings_language_spanish, locale)));
            locale = new Locale("ru","RU");
            put(locale.toLanguageTag(), new Language(locale, StringUtils.getStringByLocale(context, R.string.settings_language_russian, locale)));
            locale = new Locale("ko","KR");
            put(locale.toLanguageTag(), new Language(locale, StringUtils.getStringByLocale(context, R.string.settings_language_korean, locale)));
            locale = new Locale("da","DK");
            put(locale.toLanguageTag(), new Language(locale, StringUtils.getStringByLocale(context, R.string.settings_language_danish, locale)));
            locale = new Locale("fi","FI");
            put(locale.toLanguageTag(), new Language(locale, StringUtils.getStringByLocale(context, R.string.settings_language_finnish, locale)));
            locale = new Locale("uk","UA");
            put(locale.toLanguageTag(), new Language(locale, StringUtils.getStringByLocale(context, R.string.settings_language_ukrainian, locale)));
            locale = new Locale("gl", "ES");
            put(locale.toLanguageTag(), new Language(locale, StringUtils.getStringByLocale(context, R.string.settings_language_galician, locale)));
            locale = new Locale("pt","PT");
            put(locale.toLanguageTag(), new Language(locale, StringUtils.getStringByLocale(context, R.string.settings_language_portuguese, locale)));
            locale = new Locale("pt","BR");
            put(locale.toLanguageTag(), new Language(locale, StringUtils.getStringByLocale(context, R.string.settings_language_portuguese_br, locale)));
        }};

        Locale locale = getDeviceLocale();
        String languageTag = getClosestSupportedLanguageTag(locale.toLanguageTag());
        locale = Locale.forLanguageTag(languageTag);
        Language defaultLanguage = new Language(locale, context.getString(R.string.settings_language_follow_device));

        LinkedHashMap<String, Language> map = new LinkedHashMap<>();
        map.put(DEFAULT_LANGUAGE_ID, defaultLanguage);
        map.putAll(mSupportedLanguagesCache);
        mSupportedLanguagesCache = map;

        return mSupportedLanguagesCache;
    }

    @NonNull
    public static String[] getSupportedLocalizedLanguagesStringArray(@NonNull Context context) {
        List<String> result = new ArrayList<>();
        getSupportedLocalizedLanguages(context).forEach((id, language) -> {
            result.add(language.getDisplayName());
        });

        return result.toArray(new String[]{});
    }

    public static int getIndexForSupportedLanguageId(@NonNull String languageId) {
        ArrayList<String> keys = new ArrayList<>(mSupportedLanguagesCache.keySet());
        if (keys.contains(languageId)) {
            return keys.indexOf(languageId);
        }

        throw new IllegalStateException("Non existing index in the supported languages list");
    }

    public static String getSupportedLanguageIdForIndex(int index) {
        ArrayList<String> keys = new ArrayList<>(mSupportedLanguagesCache.keySet());
        String key = keys.get(index);
        if (key != null) {
            return key;
        }
        return DEFAULT_LANGUAGE_ID;
    }

    /**
     * Ideally we would do this using the [Locale.filter] method but is only available in >=26
     **/
    private static String getClosestSupportedLanguageTag(@Nullable String languageTag) {
        try {
            Locale locale = Locale.forLanguageTag(languageTag);
            ArrayList<String> keys = new ArrayList<>(mSupportedLanguagesCache.keySet());

            Optional<String> language = keys.stream().filter(item -> item.equals(locale.toLanguageTag())).findFirst();

            if (!language.isPresent()) {
                language = keys.stream().filter(item -> {
                    Locale itemLocale = Locale.forLanguageTag(item);
                    return itemLocale.getLanguage().equals(locale.getLanguage()) &&
                            itemLocale.getScript().equals(locale.getScript()) &&
                            itemLocale.getCountry().equals(locale.getCountry()) &&
                            itemLocale.getVariant().equals(locale.getVariant());
                }).findFirst();
            }
            if (!language.isPresent()) {
                language = keys.stream().filter(item -> {
                    Locale itemLocale = Locale.forLanguageTag(item);
                    return itemLocale.getLanguage().equals(locale.getLanguage()) &&
                            itemLocale.getScript().equals(locale.getScript()) &&
                            itemLocale.getCountry().equals(locale.getCountry());
                }).findFirst();
            }
            if (!language.isPresent()) {
                language = keys.stream().filter(item -> {
                    Locale itemLocale = Locale.forLanguageTag(item);
                        return itemLocale.getLanguage().equals(locale.getLanguage()) &&
                                itemLocale.getCountry().equals(locale.getCountry());
                }).findFirst();
            }
            if (!language.isPresent()) {
                language = keys.stream().filter(item -> {
                    Locale itemLocale = Locale.forLanguageTag(item);
                    return itemLocale.getLanguage().equals(locale.getLanguage());
                }).findFirst();
            }

            return language.orElse(FALLBACK_LANGUAGE_TAG);

        } catch (NullPointerException ignored) {}

        // If there is no closest supported locale we fallback to en-US
        return FALLBACK_LANGUAGE_TAG;
    }

    // Returns the closest language (among those supported) for the given locale, or the fallback.
    public static String getClosestLanguageForLocale(Locale locale, List<String> supported, String fallback) {
        final String defaultLanguageTag = locale.toLanguageTag();
        if (supported.contains(defaultLanguageTag)) {
            return defaultLanguageTag;
        }

        final String defaultLanguage = locale.getLanguage();
        if (supported.contains(defaultLanguage)) {
            return defaultLanguage;
        }

        Optional<String> closestLanguage = supported.stream().filter(s -> s.startsWith(defaultLanguage)).findFirst();
        if (closestLanguage.isPresent()) {
            return closestLanguage.get();
        }

        return fallback;
    }
}
