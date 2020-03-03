package org.mozilla.vrbrowser.geolocation;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import org.json.JSONObject;
import org.mozilla.geckoview.GeckoWebExecutor;
import org.mozilla.geckoview.WebRequest;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

class GeolocationClient {

    static void getGeolocation(@NonNull GeckoWebExecutor executor, String aQuery, Function<GeolocationData, Void> success, Function<String, Void> error) {
        new Handler(Looper.getMainLooper()).post(() -> {
            WebRequest request = new WebRequest.Builder(aQuery)
                    .method("GET")
                    .build();

            executor.fetch(request).then(webResponse -> {
                String body;
                if (webResponse != null) {
                    if (webResponse.body != null) {
                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        int nRead;
                        byte[] data = new byte[1024];
                        while ((nRead = webResponse.body.read(data, 0, data.length)) != -1) {
                            buffer.write(data, 0, nRead);
                        }
                        body = new String(buffer.toByteArray(), StandardCharsets.UTF_8);

                        if (webResponse.statusCode == 200) {
                            JSONObject reader = new JSONObject(body);
                            success.apply(GeolocationData.parse(reader.toString()));

                        } else {
                            // called when response HTTP status is not "200"
                            error.apply(String.format("Network Error: %s", webResponse.statusCode));
                        }

                    } else {
                        // WebResponse body is null
                        error.apply("Response body is null");
                    }

                } else {
                    // WebResponse is null
                    error.apply("Response is null");
                }
                return null;

            }).exceptionally(throwable -> {
                // Exception happened
                error.apply(String.format("Unknown network Error: %s", String.format("An exception happened during the request: %s", throwable.getMessage())));
                return null;
            });
        });
    }
}
