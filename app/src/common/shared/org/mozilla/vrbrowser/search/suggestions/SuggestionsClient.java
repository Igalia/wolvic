package org.mozilla.vrbrowser.search.suggestions;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.TextHttpResponseHandler;

import java.util.List;
import java.util.function.Function;

import cz.msebera.android.httpclient.Header;
import mozilla.components.browser.search.SearchEngine;

public class SuggestionsClient {

    private static AsyncHttpClient client = new AsyncHttpClient();

    public static void getSuggestions(SearchEngine mEngine, String aQuery, Function<List<String>, Void> callback) {
        client.cancelAllRequests(true);
        client.get(aQuery, null, new TextHttpResponseHandler("ISO-8859-1") {
            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseString) {
                callback.apply(SuggestionParser.selectResponseParser(mEngine).apply(responseString));
            }
        });
    }
}
