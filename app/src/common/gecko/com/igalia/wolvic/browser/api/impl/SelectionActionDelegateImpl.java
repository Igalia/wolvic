package com.igalia.wolvic.browser.api.impl;

import android.graphics.RectF;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.utils.SystemUtils;

import org.mozilla.geckoview.GeckoSession;

import java.util.ArrayList;
import java.util.Collection;

/* package */ class SelectionActionDelegateImpl implements GeckoSession.SelectionActionDelegate {
    static final String LOGTAG = SystemUtils.createLogtag(SelectionActionDelegateImpl.class);

    private WSession.SelectionActionDelegate mDelegate;
    private WSession mSession;

    public SelectionActionDelegateImpl(WSession.SelectionActionDelegate delegate, WSession session) {
        this.mDelegate = delegate;
        this.mSession = session;
    }

    /* package */ class SelectionImpl implements WSession.SelectionActionDelegate.Selection {
        GeckoSession.SelectionActionDelegate.Selection mSelection;

        public SelectionImpl(Selection selection) {
            this.mSelection = selection;
        }

        @Override
        public int flags() {
            return mSelection.flags;
        }

        @NonNull
        @Override
        public String text() {
            return mSelection.text;
        }

        @Nullable
        @Override
        public RectF clientRect() {
            return mSelection.clientRect;
        }

        @NonNull
        @Override
        public Collection<String> availableActions() {
            Collection<String> sessionSelectionActions = new ArrayList<String>();
            for (String geckoAction : mSelection.availableActions)
                sessionSelectionActions.add(fromGecko(geckoAction));

            return sessionSelectionActions;
        }

        @Override
        public boolean isActionAvailable(@NonNull String action) {
            return mSelection.isActionAvailable(toGecko(action));
        }

        @Override
        public void execute(@NonNull String action) {
            String geckoAction = toGecko(action);
            if (mSelection.isActionAvailable(geckoAction)) {
                mSelection.execute(geckoAction);
            }
        }

        private String fromGecko(@NonNull String action) {
            switch (action) {
                case GeckoSession.SelectionActionDelegate.ACTION_HIDE:
                    return WSession.SelectionActionDelegate.ACTION_HIDE;
                case GeckoSession.SelectionActionDelegate.ACTION_CUT:
                    return WSession.SelectionActionDelegate.ACTION_CUT;
                case GeckoSession.SelectionActionDelegate.ACTION_COPY:
                    return WSession.SelectionActionDelegate.ACTION_COPY;
                case GeckoSession.SelectionActionDelegate.ACTION_DELETE:
                    return WSession.SelectionActionDelegate.ACTION_DELETE;
                case GeckoSession.SelectionActionDelegate.ACTION_PASTE:
                    return WSession.SelectionActionDelegate.ACTION_PASTE;
                case GeckoSession.SelectionActionDelegate.ACTION_PASTE_AS_PLAIN_TEXT:
                    return WSession.SelectionActionDelegate.ACTION_PASTE_AS_PLAIN_TEXT;
                case GeckoSession.SelectionActionDelegate.ACTION_SELECT_ALL:
                    return WSession.SelectionActionDelegate.ACTION_SELECT_ALL;
                case GeckoSession.SelectionActionDelegate.ACTION_UNSELECT:
                    return WSession.SelectionActionDelegate.ACTION_UNSELECT;
                case GeckoSession.SelectionActionDelegate.ACTION_COLLAPSE_TO_START:
                    return WSession.SelectionActionDelegate.ACTION_COLLAPSE_TO_START;
                case GeckoSession.SelectionActionDelegate.ACTION_COLLAPSE_TO_END:
                    return WSession.SelectionActionDelegate.ACTION_COLLAPSE_TO_END;
                default:
                    Log.w(LOGTAG, "Unhandled Gecko action: " + action);
            }
            return action;
        }

        private String toGecko(@NonNull String action) {
            switch (action) {
                case WSession.SelectionActionDelegate.ACTION_HIDE:
                    return GeckoSession.SelectionActionDelegate.ACTION_HIDE;
                case WSession.SelectionActionDelegate.ACTION_CUT:
                    return GeckoSession.SelectionActionDelegate.ACTION_CUT;
                case WSession.SelectionActionDelegate.ACTION_COPY:
                    return GeckoSession.SelectionActionDelegate.ACTION_COPY;
                case WSession.SelectionActionDelegate.ACTION_DELETE:
                    return GeckoSession.SelectionActionDelegate.ACTION_DELETE;
                case WSession.SelectionActionDelegate.ACTION_PASTE:
                    return GeckoSession.SelectionActionDelegate.ACTION_PASTE;
                case WSession.SelectionActionDelegate.ACTION_PASTE_AS_PLAIN_TEXT:
                    return GeckoSession.SelectionActionDelegate.ACTION_PASTE_AS_PLAIN_TEXT;
                case WSession.SelectionActionDelegate.ACTION_SELECT_ALL:
                    return GeckoSession.SelectionActionDelegate.ACTION_SELECT_ALL;
                case WSession.SelectionActionDelegate.ACTION_UNSELECT:
                    return GeckoSession.SelectionActionDelegate.ACTION_UNSELECT;
                case WSession.SelectionActionDelegate.ACTION_COLLAPSE_TO_START:
                    return GeckoSession.SelectionActionDelegate.ACTION_COLLAPSE_TO_START;
                case WSession.SelectionActionDelegate.ACTION_COLLAPSE_TO_END:
                    return GeckoSession.SelectionActionDelegate.ACTION_COLLAPSE_TO_END;
                default:
                    Log.w(LOGTAG, "Unhandled action: " + action);
                    return action;
            }
        }
    }

    @Override
    public void onShowActionRequest(@NonNull GeckoSession session, @NonNull Selection selection) {
        mDelegate.onShowActionRequest(mSession, new SelectionImpl(selection));
    }

    @Override
    public void onHideAction(@NonNull GeckoSession session, int reason) {
        mDelegate.onHideAction(mSession, reason);
    }
}
