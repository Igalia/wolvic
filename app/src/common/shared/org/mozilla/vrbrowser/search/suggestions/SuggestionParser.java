package org.mozilla.vrbrowser.search.suggestions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import mozilla.components.browser.search.SearchEngine;

public class SuggestionParser {

    private static final String AZERDICT = "Azerdict";
    private static final String DAUM = "다음지도";
    private static final String QWANT = "Qwant";

    public static Function<String, List<String>> selectResponseParser(SearchEngine mEngine) {
        if (mEngine.getName().equals(AZERDICT)) {
            return azerdictResponseParser;

        } else if (mEngine.getName().equals(DAUM)) {
            return daumResponseParser;

        } else if (mEngine.getName().equals(QWANT)) {
            return qwantResponseParser;
        }

        return defaultResponseParser;
    }

    private static Function<String, List<String>> defaultResponseParser = buildJSONArrayParser(1);
    private static Function<String, List<String>> azerdictResponseParser = buildJSONObjectParser("suggestions");
    private static Function<String, List<String>> daumResponseParser = buildJSONObjectParser("items");
    private static Function<String, List<String>> qwantResponseParser = buildQwantParser();

    private static Function<String, List<String>> buildJSONArrayParser(int ressultsIndex) {
        return input -> {
            List<String> list = new ArrayList<>();
            try {
                JSONArray root = new JSONArray(input);
                JSONArray array = root.getJSONArray(ressultsIndex);
                if (array != null) {
                    int len = array.length();
                    for (int i=0; i<len; i++){
                        list.add(array.get(i).toString());
                    }
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }

            return list;
        };
    }

    private static Function<String, List<String>> buildJSONObjectParser(String resultsKey) {
        return input -> {
            List<String> list = new ArrayList<>();
            try {
                JSONObject root = new JSONObject(input);
                JSONArray array = root.getJSONArray(resultsKey);
                if (array != null) {
                    int len = array.length();
                    for (int i=0; i<len; i++){
                        list.add(array.get(i).toString());
                    }
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }

            return list;
        };
    }

    private static Function<String, List<String>> buildQwantParser() {
        return input -> {
            List<String> list = new ArrayList<>();
            try {
                JSONObject root = new JSONObject(input);
                JSONObject data = root.getJSONObject("data");
                JSONArray items = data.getJSONArray("items");
                if (items != null) {
                    int len = items.length();
                    for (int i=0; i<len; i++){
                        list.add(items.get(i).toString());
                    }
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }

            return list;
        };
    }

}
