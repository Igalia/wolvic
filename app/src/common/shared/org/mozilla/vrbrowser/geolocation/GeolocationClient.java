package org.mozilla.vrbrowser.geolocation;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;

import org.json.JSONObject;

import java.util.function.Function;

import cz.msebera.android.httpclient.Header;

public class GeolocationClient {

    private static final int RETRY_SLEEP = 5 * 1000;

    private static AsyncHttpClient client = new AsyncHttpClient();

    public static void getGeolocation(String aQuery, int retries, Function success, Function error) {
        client.cancelAllRequests(true);
        client.setMaxRetriesAndTimeout(retries, RETRY_SLEEP);
        client.get(aQuery, null, new JsonHttpResponseHandler("ISO-8859-1") {

            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                success.apply(GeolocationData.parse(response.toString()));
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                error.apply(errorResponse);
            }

        });
    }
}
