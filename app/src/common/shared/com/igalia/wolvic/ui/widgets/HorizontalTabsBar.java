package com.igalia.wolvic.ui.widgets;

import android.content.Context;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.ui.adapters.TabsBarAdapter;

public class HorizontalTabsBar extends AbstractTabsBar {

    protected Button mAddTabButton;
    protected RecyclerView mTabsList;
    protected LinearLayoutManager mLayoutManager;
    protected TabsBarAdapter mAdapter;
    protected final TabDelegate mTabDelegate;

    public HorizontalTabsBar(Context aContext, TabDelegate aDelegate) {
        super(aContext);
        mTabDelegate = aDelegate;
        updateUI();
    }

    private void updateUI() {
        removeAllViews();

        inflate(getContext(), R.layout.tabs_bar_horizontal, this);

        mAddTabButton = findViewById(R.id.add_tab);
        mAddTabButton.setOnClickListener(v -> mTabDelegate.onTabAdd());

        mSyncTabButton = findViewById(R.id.sync_tabs);
        mSyncTabButton.setOnClickListener(v -> mTabDelegate.onTabSync());
        mSyncTabButton.setVisibility(mTabDelegate.accountIsConnected() ? VISIBLE : GONE);

        mTabsList = findViewById(R.id.tabsRecyclerView);
        mLayoutManager = new LinearLayoutManager(getContext());
        mLayoutManager.setOrientation(RecyclerView.HORIZONTAL);
        mTabsList.setLayoutManager(mLayoutManager);
        mAdapter = new TabsBarAdapter(mTabDelegate, TabsBarAdapter.Orientation.HORIZONTAL);
        mTabsList.setAdapter(mAdapter);
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        Context context = getContext();
        aPlacement.width = SettingsStore.getInstance(getContext()).getWindowWidth();
        aPlacement.height = WidgetPlacement.dpDimension(context, R.dimen.horizontal_tabs_bar_height);
        aPlacement.worldWidth = aPlacement.width * WidgetPlacement.worldToDpRatio(context);
        aPlacement.anchorX = 0.0f;
        aPlacement.anchorY = 0.0f;
        aPlacement.parentAnchorX = 0.0f;
        aPlacement.parentAnchorY = 1.0f;
        aPlacement.parentAnchorGravity = WidgetPlacement.GRAVITY_DEFAULT;
    }

    @Override
    public void updateWidgetPlacement() {
        if (mAttachedWindow == null) {
            mWidgetPlacement.parentHandle = -1;
        } else {
            mWidgetPlacement.parentHandle = mAttachedWindow.getHandle();
            mWidgetPlacement.width = mAttachedWindow.getPlacement().width;
            mWidgetPlacement.worldWidth = mAttachedWindow.getPlacement().worldWidth;
        }
    }

    public void refreshTabs() {
        mAdapter.updateTabs(SessionStore.get().getSessions(mPrivateMode));
    }

}
