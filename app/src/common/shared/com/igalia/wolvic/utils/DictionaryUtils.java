package com.igalia.wolvic.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.BuildConfig;
import com.igalia.wolvic.browser.SettingsStore;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DictionaryUtils {
    public static final String EXTERNAL_DICS_SUFFIX = "_wordlist.db";
    public static final String BUILTIN_DICS_PREFIX = "databases/";
    public static final Map<String, String[]> BUILTIN_DICS_MAP = Map.of("zh_CN", new String[]{"google_pinyin.db"}, "zh_TW", new String[]{"zhuyin_words.db", "zhuyin_phrases.db"});

    /**
     * Returns a path in the database path that stores the remote dictionaries.
     *
     * @param context An activity context.
     * @param lang    The dictionary language. This maps to the Remote properties JSON "lang" dictionary property
     *                for external dictionaries and the dictionary value list in option_value.xml for builtin ones.
     * @return The location of the dictionary in the devices memory.
     */
    @Nullable
    public static String getExternalDicPath(@NonNull Context context, @NonNull String lang) {
        File file = new File(context.getDatabasePath(getExternalDicFullName(lang)).getAbsolutePath());
        if (!file.exists() || !file.isFile()) return null;
        return file.getAbsolutePath();
    }

    /**
     * Returns a path for a builtin dictionary.
     *
     * @return The relative path to the builtin dictionary
     */
    @NonNull
    public static String getBuiltinDicPath() {
        return BUILTIN_DICS_PREFIX;
    }

    public static String[] getBuiltinDicNames(@NonNull String lang) {
        if (!isBuiltinDictionary(lang)) {
            return new String[]{};
        }
        return BUILTIN_DICS_MAP.get(lang);
    }

    @NonNull
    public static String getExternalDicFullName(@NonNull String lang) {
        return lang + EXTERNAL_DICS_SUFFIX;
    }

    /**
     * Check if the dictionary is builtin.
     *
     * @param lang The dictionary language. This maps to the dictionary value list in option_value.xml for builtin ones.
     * @return true if the dictionary is builtin, false otherwise.
     */
    public static boolean isBuiltinDictionary(@NonNull String lang) {
        return BUILTIN_DICS_MAP.containsKey(lang);
    }

    /**
     * Check if the dictionary is external.
     *
     * @param context An activity context.
     * @param lang    The dictionary language. This maps to the Remote properties JSON "lang" dictionary property
     *                for external dictionaries and the dictionary value list in option_value.xml for builtin ones.
     * @return true if the dictionary is external, false otherwise.
     */
    public static boolean isExternalDictionary(@NonNull Context context, @NonNull String lang) {
        return getExternalDictionaryByLang(context, lang) != null;
    }

    /**
     * Returns the external dictionary based for a given dictionary language. It returns the dictionary
     * for the current version if the dictionary exists, otherwise it returns the dictionary with that language
     * for the most recent previous version.
     *
     * @param context An activity context.
     * @param lang    The dictionary language. This maps to the Remote properties JSON "lang" dictionary property.
     * @return The Remote dictionaries list or null if they couldn't be found.
     */
    @Nullable
    public static Dictionary getExternalDictionaryByLang(@NonNull Context context, @NonNull String lang) {
        return getExternalDictionaryByLang(context, lang, BuildConfig.VERSION_NAME);
    }

    /**
     * Returns the URL to the dictionary's payload.
     *
     * @param dic An Dictionary data structure
     * @return The appropriated URL to the dictionary's payload.
     */
    @NonNull
    public static String getDictionaryPayload(Dictionary dic) {
        return dic.getPayload();
    }

    /**
     * Returns the external dictionary based for a given dictionary language. It returns the dictionary
     * for the current version if the dictionary exists, otherwise it returns the dictionary with that language
     * for the most recent previous version.
     *
     * @param context     An activity context.
     * @param lang        The dictionary language. This maps to the Remote properties JSON "lang" dictionary property.
     * @param versionName The target version name string.
     * @return The Remote dictionaries list or null if they couldn't be found.
     */
    @Nullable
    public static Dictionary getExternalDictionaryByLang(@NonNull Context context, @NonNull String lang, @NonNull String versionName) {
        Map<String, RemoteProperties> properties = SettingsStore.getInstance(context).getRemoteProperties();
        if (properties == null) {
            return null;
        }

        // If there are dictionaries for the current version we return those,
        // otherwise return the ones from the most recent version
        if (properties.containsKey(versionName)) {
            RemoteProperties versionProperties = properties.get(versionName);
            if (versionProperties != null && versionProperties.getDictionaries() != null) {
                return Arrays.stream(versionProperties.getDictionaries()).filter(dictionary -> lang.equals(dictionary.getLang())).findFirst().orElse(null);
            }
        }

        Set<String> keys = properties.keySet();
        List<String> keysList = keys.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        for (String key : keysList) {
            RemoteProperties props = properties.get(key);
            if (props != null && props.getDictionaries() != null) {
                return Arrays.stream(props.getDictionaries()).filter(dictionary -> lang.equals(dictionary.getLang())).findFirst().orElse(null);
            }
        }

        return null;
    }

    /**
     * Returns the external dictionary based for a given dictionary payload url. It returns the dictionary
     * for the current version if the dictionary exists, otherwise it returns the dictionary with that payload url
     * for the most recent previous version.
     *
     * @param context    An activity context.
     * @param payloadUrl The payload url for the dictionary. This maps to the Remote properties JSON "payload" dictionary property.
     * @return The Remote dictionaries list or null if they couldn't be found.
     */
    @Nullable
    public static Dictionary getExternalDictionaryByPayload(@NonNull Context context, @NonNull String payloadUrl) {
        return getExternalDictionaryByPayload(context, payloadUrl, BuildConfig.VERSION_NAME);
    }

    /**
     * Returns the external dictionary based for a given dictionary payload url. It returns the dictionary
     * for the current version if the dictionary exists, otherwise it returns the dictionary with that payload url
     * for the most recent previous version.
     *
     * @param context     An activity context.
     * @param payloadUrl  The payload url for the dictionary. This maps to the Remote properties JSON "payload" dictionary property.
     * @param versionName The target version name string.
     * @return The Remote dictionaries list or null if they couldn't be found.
     */
    @Nullable
    public static Dictionary getExternalDictionaryByPayload(@NonNull Context context, @NonNull String payloadUrl, @NonNull String versionName) {
        Map<String, RemoteProperties> properties = SettingsStore.getInstance(context).getRemoteProperties();
        if (properties == null) {
            return null;
        }

        // If there are dictionaries for the current version we return those,
        // otherwise return the ones from the most recent version
        if (properties.containsKey(versionName)) {
            RemoteProperties versionProperties = properties.get(versionName);
            if (versionProperties != null && versionProperties.getDictionaries() != null) {
                return Arrays.stream(versionProperties.getDictionaries()).filter(dictionary -> payloadUrl.equals(getDictionaryPayload(dictionary))).findFirst().orElse(null);
            }
        }

        Set<String> keys = properties.keySet();
        List<String> keysList = keys.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        for (String key : keysList) {
            RemoteProperties props = properties.get(key);
            if (props != null && props.getDictionaries() != null) {
                return Arrays.stream(props.getDictionaries()).filter(dictionary -> payloadUrl.equals(getDictionaryPayload(dictionary))).findFirst().orElse(null);
            }
        }

        return null;
    }
}
