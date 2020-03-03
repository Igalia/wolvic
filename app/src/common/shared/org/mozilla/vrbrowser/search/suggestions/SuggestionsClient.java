package org.mozilla.vrbrowser.search.suggestions;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import org.mozilla.geckoview.GeckoWebExecutor;
import org.mozilla.geckoview.WebRequest;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import mozilla.components.browser.search.SearchEngine;

public class SuggestionsClient {


    public static CompletableFuture<List<String>> getSuggestions(@NonNull GeckoWebExecutor executor, SearchEngine mEngine, String aQuery) {
        final CompletableFuture<List<String>> future = new CompletableFuture<>();

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
                            future.complete(SuggestionParser.selectResponseParser(mEngine).apply(body));

                        } else {
                            // called when response HTTP status is not "200"
                            future.completeExceptionally(new Throwable(String.format("Network Error: %s", webResponse.statusCode)));
                        }

                    } else {
                        // WebResponse body is null
                        future.completeExceptionally(new Throwable("Response body is null"));
                    }

                } else {
                    // WebResponse is null
                    future.completeExceptionally(new Throwable("Response is null"));
                }
                return null;

            }).exceptionally(throwable -> {
                // Exception happened
                future.completeExceptionally(new Throwable(String.format("Unknown network Error: %s", String.format("An exception happened during the request: %s", throwable.getMessage()))));
                return null;
            });
        });

        return future;
    }
}
