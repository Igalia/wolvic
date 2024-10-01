package com.igalia.wolvic.ui.widgets;

import android.content.Context;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.SessionChangeListener;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.ui.adapters.TabsBarAdapter;

public class VerticalTabsBar extends AbstractTabsBar implements SessionChangeListener {

    protected boolean mPrivateMode;
    protected Button mAddTabButton;
    protected RecyclerView mTabsList;
    protected LinearLayoutManager mLayoutManager;
    protected TabsBarAdapter mAdapter;
    protected TabDelegate mTabDelegate;

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

        mTabsList = findViewById(R.id.tabsRecyclerView);
        mLayoutManager = new LinearLayoutManager(getContext());
        mLayoutManager.setOrientation(RecyclerView.VERTICAL);
        mTabsList.setLayoutManager(mLayoutManager);
        mAdapter = new TabsBarAdapter(mTabDelegate, TabsBarAdapter.Orientation.VERTICAL);
        mTabsList.setAdapter(mAdapter);

        SessionStore.get().addSessionChangeListener(this);
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        Context context = getContext();
        aPlacement.width = WidgetPlacement.dpDimension(context, R.dimen.vertical_tabs_bar_width);
        aPlacement.height = SettingsStore.getInstance(getContext()).getWindowHeight();
        aPlacement.worldWidth = aPlacement.width * WidgetPlacement.worldToDpRatio(context);
        aPlacement.translationY = WidgetPlacement.dpDimension(context, R.dimen.top_bar_window_margin);
        aPlacement.anchorX = 1.0f;
        aPlacement.anchorY = 0.0f;
        aPlacement.parentAnchorX = 0.0f;
        aPlacement.parentAnchorY = 0.0f;
        aPlacement.parentAnchorGravity = WidgetPlacement.GRAVITY_CENTER_Y;
        aPlacement.layer = true;
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.context_menu_z_distance);
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

    public void refreshTabs() {
        mAdapter.updateTabs(SessionStore.get().getSessions(mPrivateMode));
    }
}
