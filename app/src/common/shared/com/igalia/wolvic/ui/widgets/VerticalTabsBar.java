package com.igalia.wolvic.ui.widgets;

import android.content.Context;
import android.view.LayoutInflater;

import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.TabsBarVerticalBinding;
import com.igalia.wolvic.ui.adapters.TabsBarAdapter;

public class VerticalTabsBar extends AbstractTabsBar {

    private TabsBarVerticalBinding mBinding;
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

        LayoutInflater inflater = LayoutInflater.from(getContext());
        mBinding = DataBindingUtil.inflate(inflater, R.layout.tabs_bar_vertical, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity) getContext());
        mBinding.setSyncAccountEnabled(mSyncAccountEnabled);

        mBinding.addTab.setOnClickListener(v -> mTabDelegate.onTabAdd());

        mBinding.syncTabs.setOnClickListener(v -> mTabDelegate.onTabSync());

        mLayoutManager = new LinearLayoutManager(getContext());
        mLayoutManager.setOrientation(RecyclerView.VERTICAL);
        mBinding.tabsRecyclerView.setLayoutManager(mLayoutManager);
        mAdapter = new TabsBarAdapter(mTabDelegate, TabsBarAdapter.Orientation.VERTICAL);
        mBinding.tabsRecyclerView.setAdapter(mAdapter);
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
