package com.igalia.wolvic.search.suggestions

import android.content.Context
import com.igalia.wolvic.browser.engine.EngineProvider
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import mozilla.components.browser.search.suggestions.SearchSuggestionClient
import mozilla.components.concept.fetch.Request
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

fun getSuggestionsAsync(client: SearchSuggestionClient, query: String): CompletableFuture<List<String>?> =
        GlobalScope.future {
            client.getSuggestions(query)
        }

fun fetchSearchSuggestions(context: Context, searchUrl: String): String? {
    val request = Request(searchUrl);
    return EngineProvider.getDefaultClient(context).fetch(request).body.string(StandardCharsets.UTF_8)
}