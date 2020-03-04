package org.mozilla.vrbrowser.search.suggestions;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.search.SearchEngineWrapper;
import org.mozilla.vrbrowser.ui.widgets.SuggestionsWidget.SuggestionItem;
import org.mozilla.vrbrowser.ui.widgets.SuggestionsWidget.SuggestionItem.Type;
import org.mozilla.vrbrowser.utils.UrlUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class SuggestionsProvider {

    private static final String LOGTAG = SuggestionsProvider.class.getSimpleName();

    public class DefaultSuggestionsComparator implements Comparator {

        public int compare(Object obj1, Object obj2) {
            SuggestionItem suggestion1 = (SuggestionItem)obj1;
            SuggestionItem suggestion2 = (SuggestionItem)obj2;
            if (suggestion1.type == Type.SUGGESTION && suggestion2.type == Type.SUGGESTION) {
                return 0;

            } else if (suggestion1.type == suggestion2.type) {
                if (suggestion1.type == Type.HISTORY) {
                    if (suggestion1.score != suggestion2.score) {
                        return suggestion1.score - suggestion2.score;
                    }
                }

                return suggestion1.url.compareTo(suggestion2.url);

            } else {
                return suggestion1.type.ordinal() - suggestion2.type.ordinal();
            }
        }
    }

    private SearchEngineWrapper mSearchEngineWrapper;
    private String mText;
    private String mFilterText;
    private Comparator mComparator;
    private Executor mUIThreadExecutor;

    public SuggestionsProvider(Context context) {
        mSearchEngineWrapper = SearchEngineWrapper.get(context);
        mFilterText = "";
        mComparator = new DefaultSuggestionsComparator();
        mUIThreadExecutor = ((VRBrowserApplication)context.getApplicationContext()).getExecutors().mainThread();
    }

    private String getSearchURLOrDomain(String text) {
        if (UrlUtils.isDomain(text)) {
            return text;
        } else if (UrlUtils.isIPUri(text)) {
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

    private CompletableFuture<List<SuggestionItem>> getBookmarkSuggestions(@NonNull List<SuggestionItem> items) {
        CompletableFuture future = new CompletableFuture();
        SessionStore.get().getBookmarkStore().searchBookmarks(mFilterText, 100).thenAcceptAsync((bookmarks) -> {
            bookmarks.stream()
                    .filter((b) -> !b.getUrl().startsWith("place:") &&
                            !b.getUrl().startsWith("about:reader"))
                    .forEach(b -> items.add(SuggestionItem.create(
                            b.getTitle(),
                            b.getUrl(),
                            null,
                            Type.BOOKMARK,
                            0
                    )));
            if (mComparator != null) {
                items.sort(mComparator);
            }
            future.complete(items);

        }, mUIThreadExecutor).exceptionally(throwable -> {
            Log.d(LOGTAG, "Error getting bookmarks suggestions: " + throwable.getLocalizedMessage());
            throwable.printStackTrace();
            future.complete(items);
            return null;
        });

        return future;
    }

    private CompletableFuture<List<SuggestionItem>> getHistorySuggestions(@NonNull final List<SuggestionItem> items) {
        CompletableFuture future = new CompletableFuture();
        SessionStore.get().getHistoryStore().getSuggestions(mFilterText, 100).thenAcceptAsync((history) -> {
            history.forEach(h -> items.add(SuggestionItem.create(
                            h.getTitle(),
                            h.getUrl(),
                            null,
                            Type.HISTORY,
                            h.getScore()
                    )));
            if (mComparator != null) {
                items.sort(mComparator);
            }
            future.complete(items);

        }, mUIThreadExecutor).exceptionally(throwable -> {
            Log.d(LOGTAG, "Error getting history suggestions: " + throwable.getLocalizedMessage());
            throwable.printStackTrace();
            future.complete(items);
            return null;
        });

        return future;
    }

    private CompletableFuture<List<SuggestionItem>> getSearchEngineSuggestions(@NonNull final List<SuggestionItem> items) {
        CompletableFuture future = new CompletableFuture();

        // Completion from browser-domains
        if (!mText.equals(mFilterText)) {
            items.add(SuggestionItem.create(
                    mText,
                    getSearchURLOrDomain(mText),
                    null,
                    Type.COMPLETION,
                    0
            ));
        }

        // Original text
        items.add(SuggestionItem.create(
                mFilterText,
                getSearchURLOrDomain(mFilterText),
                null,
                Type.SUGGESTION,
                0
        ));

        // Suggestions
        mSearchEngineWrapper.getSuggestions(mFilterText).thenAcceptAsync((suggestions) -> {
            suggestions.forEach(s -> {
                String url = mSearchEngineWrapper.getSearchURL(s);
                items.add(SuggestionItem.create(
                        s,
                        url,
                        null,
                        Type.SUGGESTION,
                        0
                ));
            });
            if (mComparator != null) {
                items.sort(mComparator);
            }
            future.complete(items);

        }, mUIThreadExecutor).exceptionally(throwable -> {
            Log.d(LOGTAG, "Error getting search engine suggestions: " + throwable.getLocalizedMessage());
            throwable.printStackTrace();
            future.complete(items);
            return null;
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
