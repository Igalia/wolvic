package org.mozilla.vrbrowser.search.suggestions;

import android.content.Context;

import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.browser.SessionStore;
import org.mozilla.vrbrowser.search.SearchEngineWrapper;
import org.mozilla.vrbrowser.ui.widgets.SuggestionsWidget.SuggestionItem;
import org.mozilla.vrbrowser.ui.widgets.SuggestionsWidget.SuggestionItem.Type;
import org.mozilla.vrbrowser.utils.UrlUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SuggestionsProvider {

    public class DefaultSuggestionsComparator implements Comparator {

        public int compare(Object obj1, Object obj2) {
            SuggestionItem suggestion1 = (SuggestionItem)obj1;
            SuggestionItem suggestion2 = (SuggestionItem)obj2;
            if (suggestion1.type == Type.SUGGESTION && suggestion2.type == Type.SUGGESTION) {
                return 0;

            } else if (suggestion1.type == suggestion2.type) {
                if (mFilterText != null) {
                    if (suggestion1.title != null && suggestion2.title != null)
                        return suggestion1.title.toLowerCase().indexOf(mFilterText) - suggestion2.title.toLowerCase().indexOf(mFilterText);
                    return suggestion1.url.toLowerCase().indexOf(mFilterText) - suggestion2.url.indexOf(mFilterText);

                } else {
                    return suggestion1.url.compareTo(suggestion2.url);
                }

            } else {
                return suggestion1.type.ordinal() - suggestion2.type.ordinal();
            }
        }
    }

    private Context mContext;
    private SearchEngineWrapper mSearchEngineWrapper;
    private String mText;
    private String mFilterText;
    private Comparator mComparator;

    public SuggestionsProvider(Context context) {
        mContext = context;
        mSearchEngineWrapper = SearchEngineWrapper.get(mContext);
        mFilterText = "";
        mComparator = new DefaultSuggestionsComparator();
    }

    private String getSearchURLOrDomain(String text) {
        if (UrlUtils.isDomain(text)) {
            return text;

        } else {
            return mSearchEngineWrapper.getSearchURL(text);
        }
    }

    public void setFilterText(String text) {
        mFilterText = text.toLowerCase();
    }

    public void setText(String text) { mText = text; }

    public void setComparator(Comparator comparator) {
        mComparator = comparator;
    }

    public CompletableFuture<List<SuggestionItem>> getBookmarkSuggestions(@NonNull List<SuggestionItem> items) {
        CompletableFuture future = new CompletableFuture();
        SessionStore.get().getBookmarkStore().getBookmarks().thenAcceptAsync((bookmarks) -> {
            bookmarks.stream().
                    filter(b -> b.getUrl().toLowerCase().contains(mFilterText) ||
                            b.getTitle().toLowerCase().contains(mFilterText))
                    .forEach(b -> items.add(SuggestionItem.create(
                            b.getTitle(),
                            b.getUrl(),
                            null,
                            Type.BOOKMARK
                    )));
            if (mComparator != null)
                items.sort(mComparator);
            future.complete(items);
        });

        return future;
    }

    public CompletableFuture<List<SuggestionItem>> getHistorySuggestions(@NonNull final List<SuggestionItem> items) {
        CompletableFuture future = new CompletableFuture();
        SessionStore.get().getHistoryStore().getHistory().thenAcceptAsync((history) -> {
            history.stream()
                    .filter(h ->
                            h.toLowerCase().contains(mFilterText))
                    .forEach(h -> items.add(SuggestionItem.create(
                            h,
                            h,
                            null,
                            Type.HISTORY
                    )));
            if (mComparator != null)
                items.sort(mComparator);
            future.complete(items);
        });

        return future;
    }

    public CompletableFuture<List<SuggestionItem>> getSearchEngineSuggestions(@NonNull final List<SuggestionItem> items) {
        CompletableFuture future = new CompletableFuture();

        // Completion from browser-domains
        if (!mText.equals(mFilterText)) {
            items.add(SuggestionItem.create(
                    mText,
                    getSearchURLOrDomain(mText),
                    null,
                    Type.COMPLETION
            ));
        }

        // Original text
        items.add(SuggestionItem.create(
                mFilterText,
                getSearchURLOrDomain(mFilterText),
                null,
                Type.SUGGESTION
        ));

        // Suggestions
        mSearchEngineWrapper.getSuggestions(mFilterText).thenAcceptAsync((suggestions) -> {
            suggestions.forEach(s -> {
                String url = mSearchEngineWrapper.getSearchURL(s);
                items.add(SuggestionItem.create(
                        s,
                        url,
                        null,
                        Type.SUGGESTION
                ));
            });
            if (mComparator != null)
                items.sort(mComparator);
            future.complete(items);
        });

        return future;
    }

    public CompletableFuture<List<SuggestionItem>> getSuggestions() {
        return CompletableFuture.supplyAsync(() -> new ArrayList<SuggestionItem>())
                .thenComposeAsync(this::getSearchEngineSuggestions)
                .thenComposeAsync(this::getBookmarkSuggestions)
                .thenComposeAsync(this::getHistorySuggestions);
    }

}
