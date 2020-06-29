package org.mozilla.vrbrowser.search.suggestions

import android.content.Context
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import mozilla.components.browser.search.suggestions.SearchSuggestionClient
import mozilla.components.concept.fetch.Request
import org.mozilla.vrbrowser.browser.engine.EngineProvider
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