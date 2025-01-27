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
import com.igalia.wolvic.databinding.TabsBarHorizontalBinding;
import com.igalia.wolvic.ui.adapters.TabsBarAdapter;

public class HorizontalTabsBar extends AbstractTabsBar {

    private TabsBarHorizontalBinding mBinding;
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

        LayoutInflater inflater = LayoutInflater.from(getContext());
        mBinding = DataBindingUtil.inflate(inflater, R.layout.tabs_bar_horizontal, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity) getContext());
        mBinding.setSyncAccountEnabled(mSyncAccountEnabled);

        mBinding.addTab.setOnClickListener(v -> mTabDelegate.onTabAdd());

        mBinding.syncTabs.setOnClickListener(v -> mTabDelegate.onTabSync());

        mLayoutManager = new LinearLayoutManager(getContext());
        mLayoutManager.setOrientation(RecyclerView.HORIZONTAL);
        mBinding.tabsRecyclerView.setLayoutManager(mLayoutManager);
        mAdapter = new TabsBarAdapter(mTabDelegate, TabsBarAdapter.Orientation.HORIZONTAL);
        mBinding.tabsRecyclerView.setAdapter(mAdapter);
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
