package com.igalia.wolvic.ui.widgets;

import android.content.Context;
import android.widget.Button;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.ui.adapters.TabsBarAdapter;

public class VerticalTabsBar extends AbstractTabsBar {

    protected Button mAddTabButton;
    protected RecyclerView mTabsList;
    protected LinearLayoutManager mLayoutManager;
    protected TabsBarAdapter mAdapter;
    protected final TabDelegate mTabDelegate;

    public VerticalTabsBar(Context aContext, TabDelegate aDelegate) {
        super(aContext);
        mTabDelegate = aDelegate;
        updateUI();
    }

    private void updateUI() {
        removeAllViews();

        inflate(getContext(), R.layout.tabs_bar_vertical, this);

        mAddTabButton = findViewById(R.id.add_tab);
        mAddTabButton.setOnClickListener(v -> mTabDelegate.onTabAdd());

        mSyncTabButton = findViewById(R.id.sync_tabs);
        mSyncTabButton.setOnClickListener(v -> mTabDelegate.onTabSync());
        mSyncTabButton.setVisibility(mTabDelegate.accountIsConnected() ? VISIBLE : GONE);

        mTabsList = findViewById(R.id.tabsRecyclerView);
        mLayoutManager = new LinearLayoutManager(getContext());
        mLayoutManager.setOrientation(RecyclerView.VERTICAL);
        mTabsList.setLayoutManager(mLayoutManager);
        mAdapter = new TabsBarAdapter(mTabDelegate, TabsBarAdapter.Orientation.VERTICAL);
        mTabsList.setAdapter(mAdapter);
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        Context context = getContext();
        aPlacement.width = WidgetPlacement.dpDimension(context, R.dimen.vertical_tabs_bar_width);
        aPlacement.height = SettingsStore.getInstance(getContext()).getWindowHeight();
        aPlacement.worldWidth = aPlacement.width * WidgetPlacement.worldToDpRatio(context);
        aPlacement.anchorX = 1.0f;
        aPlacement.anchorY = 0.0f;
        aPlacement.parentAnchorX = 0.0f;
        aPlacement.parentAnchorY = 0.0f;
        aPlacement.parentAnchorGravity = WidgetPlacement.GRAVITY_CENTER_Y;
    }

    @Override
    public void updateWidgetPlacement() {
        if (mAttachedWindow == null) {
            mWidgetPlacement.parentHandle = -1;
        } else {
            mWidgetPlacement.parentHandle = mAttachedWindow.getHandle();
            mWidgetPlacement.height = mAttachedWindow.getPlacement().height;
        }
    }

    public void refreshTabs() {
        mAdapter.updateTabs(SessionStore.get().getSessions(mPrivateMode));
    }
}

