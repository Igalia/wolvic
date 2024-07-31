package com.igalia.wolvic.ui.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.SessionChangeListener;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.ui.views.TabsBarItem;
import com.igalia.wolvic.utils.BitmapCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TabsBar extends UIWidget implements SessionChangeListener {

    protected BitmapCache mBitmapCache;
    protected RecyclerView mTabsList;
    protected GridLayoutManager mLayoutManager;
    protected TabsBarAdapter mAdapter;
    protected boolean mPrivateMode;
    protected TabDelegate mTabDelegate;

    public TabsBar(Context aContext) {
        super(aContext);
        updateUI();
    }

    public TabsBar(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        updateUI();
    }

    public TabsBar(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        updateUI();
    }

    private void updateUI() {
        if (mBitmapCache == null) {
            mBitmapCache = BitmapCache.getInstance(getContext());
        }

        removeAllViews();
        inflate(getContext(), R.layout.tabs_bar, this);
        mTabsList = findViewById(R.id.tabsRecyclerView);
        mTabsList.setHasFixedSize(true);
        mLayoutManager = new GridLayoutManager(getContext(), 1);
        mTabsList.setLayoutManager(mLayoutManager);
        mAdapter = new TabsBarAdapter();
        mTabsList.setAdapter(mAdapter);

        SessionStore.get().addSessionChangeListener(this);
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        Context context = getContext();
        aPlacement.width = WidgetPlacement.dpDimension(context, R.dimen.tabs_bar_width);
        aPlacement.height = WidgetPlacement.dpDimension(context, R.dimen.tabs_bar_height);
        aPlacement.worldWidth = aPlacement.width * WidgetPlacement.worldToDpRatio(context);
        aPlacement.translationY = WidgetPlacement.dpDimension(context, R.dimen.top_bar_window_margin);
        aPlacement.anchorX = 1.0f;
        aPlacement.anchorY = 0.0f;
        aPlacement.parentAnchorX = 0.0f;
        aPlacement.parentAnchorY = 0.0f;
        aPlacement.parentAnchorGravity = WidgetPlacement.GRAVITY_CENTER_Y;
    }

    @Override
    public void attachToWindow(@NonNull WindowWidget window) {
        super.attachToWindow(window);
        mPrivateMode = window.getSession().isPrivateMode();
        mWidgetPlacement.parentHandle = window.getHandle();
        mWidgetPlacement.height = window.getPlacement().height;
        refreshTabs();
    }

    @Override
    public void detachFromWindow() {
        super.detachFromWindow();
    }

    public void setTabDelegate(TabDelegate aDelegate) {
        mTabDelegate = aDelegate;
    }

    public void refreshTabs() {
        mAdapter.updateTabs(SessionStore.get().getSessions(mPrivateMode));
    }

    @Override
    public void updatePlacementTranslationZ() {
        getPlacement().translationZ = WidgetPlacement.getWindowWorldZMeters(getContext());
    }

    @Override
    public void onSessionAdded(Session aSession) {
        // who calls this???
        Log.e(LOGTAG, "onSessionAdded: " + aSession);
        refreshTabs();
    }

    @Override
    public void onSessionOpened(Session aSession) {
        Log.e(LOGTAG, "onSessionOpened: " + aSession);
        refreshTabs();
    }

    @Override
    public void onSessionClosed(Session aSession) {
        Log.e(LOGTAG, "onSessionClosed: " + aSession);
        refreshTabs();
    }

    @Override
    public void onSessionRemoved(String aId) {
        Log.e(LOGTAG, "onSessionRemoved: " + aId);
        refreshTabs();
    }

    @Override
    public void onSessionStateChanged(Session aSession, boolean aActive) {
        Log.e(LOGTAG, "onSessionStateChanged: " + aSession + ", " + aActive);
        refreshTabs();
    }

    @Override
    public void onCurrentSessionChange(WSession aOldSession, WSession aSession) {
        Log.e(LOGTAG, "onCurrentSessionChange: old " + aSession + ", new " + aSession);
        refreshTabs();
    }

    @Override
    public void onStackSession(Session aSession) {
        Log.e(LOGTAG, "onStackSession: " + aSession);
        refreshTabs();
    }

    @Override
    public void onUnstackSession(Session aSession, Session aParent) {
        Log.e(LOGTAG, "onUnstackSession: " + aSession);
        refreshTabs();
    }

    public class TabsBarAdapter extends RecyclerView.Adapter<TabsBarAdapter.ViewHolder> {
        private List<Session> mTabs = new ArrayList<>();

        class ViewHolder extends RecyclerView.ViewHolder {
            TabsBarItem mTabBarItem;

            ViewHolder(TabsBarItem v) {
                super(v);
                mTabBarItem = v;
            }
        }

        TabsBarAdapter() {
        }

        @Override
        public long getItemId(int position) {
            if (position == 0) {
                return 0;
            } else {
                return mTabs.get(position - 1).getId().hashCode();
            }
        }

        void updateTabs(List<Session> aTabs) {
            mTabs = aTabs;

            Log.e(LOGTAG, "updateTabs: " + aTabs.size());
            for (Session session : aTabs) {
                Log.e(LOGTAG, "  " + session.getCurrentUri() + "  " + session.getLastUse());
            }

            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            TabsBarItem view = (TabsBarItem) LayoutInflater.from(parent.getContext()).inflate(R.layout.tabs_bar_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (position > 0) {
                Session session = mTabs.get(position - 1);
                holder.mTabBarItem.attachToSession(session);
                holder.mTabBarItem.setMode(TabsBarItem.Mode.TAB_DETAILS);
                holder.mTabBarItem.setDelegate(mItemDelegate);
            } else {
                holder.mTabBarItem.attachToSession(null);
                holder.mTabBarItem.setMode(TabsBarItem.Mode.ADD_TAB);
            }
        }

        @Override
        public int getItemCount() {
            return mTabs.size() + 1;
        }

        private final TabsBarItem.Delegate mItemDelegate = new TabsBarItem.Delegate() {
            @Override
            public void onAdd(TabsBarItem item) {

                Log.e(LOGTAG, "TabsBarItem.Delegate onAdd");

                if (mTabDelegate != null) {
                    mTabDelegate.onTabAdd();
                }
            }

            @Override
            public void onClick(TabsBarItem item) {
                if (mTabDelegate != null) {
                    mTabDelegate.onTabSelect(item.getSession());
                }
            }

            @Override
            public void onClose(TabsBarItem item) {
                if (mTabDelegate != null) {
                    mTabDelegate.onTabsClose(Collections.singletonList(item.getSession()));
                }
                if (mTabs.size() > 1) {
                    List<Session> latestTabs = SessionStore.get().getSessions(mPrivateMode);
                    if (latestTabs.size() != (mTabs.size() - 1) && latestTabs.size() > 0) {
                        item.attachToSession(latestTabs.get(0));
                        return;
                    }
                    refreshTabs();
                }
            }
        };
    }
}
