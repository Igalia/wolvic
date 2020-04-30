package org.mozilla.vrbrowser.search.suggestions

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import mozilla.components.browser.search.suggestions.SearchSuggestionClient
import java.util.concurrent.CompletableFuture

fun getSuggestionsAsync(client: SearchSuggestionClient, query: String): CompletableFuture<List<String>?> =
        GlobalScope.future {
            client.getSuggestions(query)
        }