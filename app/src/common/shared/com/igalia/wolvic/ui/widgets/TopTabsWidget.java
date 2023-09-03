/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.URLUtil;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.ui.views.TabView;
import com.igalia.wolvic.ui.views.UIButton;
import com.igalia.wolvic.ui.views.UITextButton;
import com.igalia.wolvic.ui.widgets.dialogs.SendTabDialogWidget;
import com.igalia.wolvic.utils.BitmapCache;

import java.util.ArrayList;
import java.util.List;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ObservableBoolean;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.databinding.TopTabsBinding;
import com.igalia.wolvic.ui.viewmodel.WindowViewModel;
import com.igalia.wolvic.utils.DeviceType;

public class TopTabsWidget extends UIWidget {

    private WindowViewModel mViewModel;
    private TopTabsBinding mBinding;
    private WindowWidget mAttachedWindow;
    private boolean mWidgetAdded = false;
    protected BitmapCache mBitmapCache;
    protected RecyclerView mTabsList;
    protected GridLayoutManager mLayoutManager;
    protected TabAdapter mAdapter;
    protected boolean mPrivateMode;
    protected TopTabsWidget.Delegate mTabDelegate;
    protected TextView mTabsAvailableCounter;
    protected TextView mSelectedTabsCounter;
    protected UITextButton mSelectTabsButton;
    protected UITextButton mDoneButton;
    protected UITextButton mCloseTabsButton;
    protected UITextButton mCloseTabsAllButton;
    protected UITextButton mSelectAllButton;
    protected UITextButton mUnselectTabs;
    protected LinearLayout mTabsSelectModeView;
    protected SendTabDialogWidget mSendTabDialog;

    protected boolean mSelecting;
    protected ArrayList<Session> mSelectedTabs = new ArrayList<>();

    public TopTabsWidget(Context aContext) {
        super(aContext);
        mBitmapCache = BitmapCache.getInstance(aContext);
        initialize();
    }

    public TopTabsWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        mBitmapCache = BitmapCache.getInstance(aContext);
        initialize();
    }

    public TopTabsWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        mBitmapCache = BitmapCache.getInstance(aContext);
        initialize();
    }

    public interface Delegate {
        void onTabSelect(Session aTab);
        void onTabAdd();
        void onTabsClose(List<Session> aTabs);
    }

    private void initialize() {
        updateUI();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        Context context = getContext();
        aPlacement.visible = false;
        aPlacement.width = WidgetPlacement.dpDimension(context, R.dimen.top_tabs_width);
        aPlacement.height = WidgetPlacement.dpDimension(context, R.dimen.top_tabs_height);
        aPlacement.worldWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.window_world_width) * aPlacement.width/getWorldWidth();
        aPlacement.translationY = WidgetPlacement.dpDimension(context, R.dimen.top_tabs_window_margin);
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.0f;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 1.0f;
        // FIXME: we temporarily disable the creation of a layer for this widget in order to
        // limit the amount of layers we create, as Pico's runtime only allows 16 at a given time.
        if (DeviceType.isPicoXR())
            aPlacement.layer = false;
    }

    private void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.top_tabs, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity)getContext());
        mBinding.setViewmodel(mViewModel);

        mTabsList = findViewById(R.id.tabsRecyclerView);
        mTabsList.setHasFixedSize(true);
        final int columns = 4;
        mLayoutManager = new GridLayoutManager(getContext(), columns);
        mTabsList.setLayoutManager(mLayoutManager);
        mTabsList.addItemDecoration(new GridSpacingItemDecoration(getContext(), columns));

        mTabsAvailableCounter = findViewById(R.id.tabsAvailableCounter);
        mSelectedTabsCounter = findViewById(R.id.tabsSelectedCounter);

        // specify an adapter (see also next example)
        mAdapter = new TabAdapter();
        mTabsList.setAdapter(mAdapter);

        mTabsSelectModeView = findViewById(R.id.tabsSelectModeView);

        mSelectTabsButton = findViewById(R.id.tabsSelectButton);
        mSelectTabsButton.setOnClickListener(view -> {
            enterSelectMode();
        });

        mDoneButton = findViewById(R.id.tabsDoneButton);
        mDoneButton.setOnClickListener(view -> {
            exitSelectMode();
        });

        mCloseTabsButton = findViewById(R.id.tabsCloseButton);
        mCloseTabsButton.setOnClickListener(v -> {
            if (mTabDelegate != null) {
                mTabDelegate.onTabsClose(mSelectedTabs);
            }
            onDismiss();
        });

        mCloseTabsAllButton = findViewById(R.id.tabsCloseAllButton);
        mCloseTabsAllButton.setOnClickListener(v -> {
            if (mTabDelegate != null) {
                mTabDelegate.onTabsClose(mAdapter.mTabs);
            }
            onDismiss();
        });

        mSelectAllButton = findViewById(R.id.tabsSelectAllButton);
        mSelectAllButton.setOnClickListener(v -> {
            mSelectedTabs = new ArrayList<>(mAdapter.mTabs);
            mAdapter.notifyDataSetChanged();
            updateSelectionMode();
        });

        mUnselectTabs = findViewById(R.id.tabsUnselectButton);
        mUnselectTabs.setOnClickListener(v -> {
            mSelectedTabs.clear();
            mAdapter.notifyDataSetChanged();
            updateSelectionMode();
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        updateUI();
    }

    @Override
    public void detachFromWindow() {
        mAttachedWindow = null;

        if (mViewModel != null) {
            mViewModel.getIsTopTabsVisible().removeObserver(mIsVisible);
            mViewModel = null;
        }
    }

    @Override
    public void attachToWindow(@NonNull WindowWidget aWindow) {
        if (mAttachedWindow == aWindow) {
            return;
        }
        detachFromWindow();

        mPrivateMode = aWindow.getSession().isPrivateMode();
        mWidgetPlacement.parentHandle = aWindow.getHandle();
        mAttachedWindow = aWindow;

        // ModelView creation and observers setup
        mViewModel = new ViewModelProvider(
                (VRBrowserActivity)getContext(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(((VRBrowserActivity) getContext()).getApplication()))
                .get(String.valueOf(mAttachedWindow.hashCode()), WindowViewModel.class);

        mBinding.setViewmodel(mViewModel);

        mViewModel.getIsTopTabsVisible().observe((VRBrowserActivity)getContext(), mIsVisible);
    }

    public @Nullable WindowWidget getAttachedWindow() {
        return mAttachedWindow;
    }

    @Override
    public void releaseWidget() {
        detachFromWindow();

        mAttachedWindow = null;
        super.releaseWidget();
    }

    Observer<ObservableBoolean> mIsVisible = isVisible -> {
        mWidgetPlacement.visible = isVisible.get();
        if (!mWidgetAdded) {
            mWidgetManager.addWidget(TopTabsWidget.this);
            mWidgetAdded = true;
        } else {
            mWidgetManager.updateWidget(TopTabsWidget.this);
        }
    };

    @Override
    public void show(int aShowFlags) {
        super.show(aShowFlags);
        refreshTabs();
        invalidate();
        mTabsList.requestFocusFromTouch();
    }

    @Override
    public void hide(@HideFlags int aHideFlags) {
        super.hide(aHideFlags);
        if (mRenderer != null) {
            mRenderer.clearSurface();
        }
    }

    public void setDelegate(TopTabsWidget.Delegate aDelegate) {
        mTabDelegate = aDelegate;
    }

    public void refreshTabs() {
        mAdapter.updateTabs(SessionStore.get().getSortedSessions(mPrivateMode));
    }

    public class TabAdapter extends RecyclerView.Adapter<TabAdapter.MyViewHolder> {
        private ArrayList<Session> mTabs = new ArrayList<>();

        class MyViewHolder extends RecyclerView.ViewHolder {
            // each data item is just a string in this case
            TabView tabView;
            MyViewHolder(TabView v) {
                super(v);
                tabView = v;
            }

        }

        TabAdapter() {}

        void updateTabs(ArrayList<Session> aTabs) {
            mTabs = aTabs;
            notifyDataSetChanged();
            updateTabCounter();
        }

        void updateTabCounter() {
            if (mTabs.size() > 1) {
                mTabsAvailableCounter.setText(getContext().getString(R.string.tabs_counter_plural, String.valueOf(mTabs.size())));
            } else {
                mTabsAvailableCounter.setText(R.string.tabs_counter_singular);
            }
        }

        @Override
        public TabAdapter.MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            TabView view = (TabView)LayoutInflater.from(parent.getContext()).inflate(R.layout.tab_view, parent, false);
            parent.setClipToPadding(false);
            parent.setClipChildren(false);
            return new MyViewHolder(view);
        }

        @Override
        public void onBindViewHolder(MyViewHolder holder, int position) {
            if (position > 0) {
                Session session = mTabs.get(position - 1);
                holder.tabView.attachToSession(session, mBitmapCache);
            } else {
                holder.tabView.setAddTabMode(true);
            }

            holder.tabView.setSelecting(mSelecting);
            holder.tabView.setSelected(mSelectedTabs.contains(holder.tabView.getSession()));
            holder.tabView.setActive(SessionStore.get().getActiveSession() == holder.tabView.getSession());
            if (holder.tabView.getSession() != null) {
                String uri = holder.tabView.getSession().getCurrentUri();
                holder.tabView.setSendTabEnabled(URLUtil.isHttpUrl(uri) || URLUtil.isHttpsUrl(uri));
            } else {
                holder.tabView.setSendTabEnabled(false);
            }
            holder.tabView.setDelegate(new TabView.Delegate() {
                @Override
                public void onClose(TabView aSender) {
                    if (aSender.getSession() != null) {
                        String uri = aSender.getSession().getCurrentUri();
                        aSender.setSendTabEnabled(URLUtil.isHttpUrl(uri) || URLUtil.isHttpsUrl(uri));
                    }
                    if (mTabDelegate != null) {
                        ArrayList<Session> closed = new ArrayList<>();
                        closed.add(aSender.getSession());
                        mTabDelegate.onTabsClose(closed);
                    }
                    if (mTabs.size() > 1) {
                        ArrayList<Session> latestTabs = SessionStore.get().getSortedSessions(mPrivateMode);
                        if (latestTabs.size() != (mTabs.size() - 1) && latestTabs.size() > 0) {
                            aSender.attachToSession(latestTabs.get(0), mBitmapCache);
                            return;
                        }
                        mTabs.remove(holder.getBindingAdapterPosition() - 1);
                        mAdapter.notifyItemRemoved(holder.getBindingAdapterPosition());
                        updateTabCounter();

                    } else {
                        onDismiss();
                    }
                }

                @Override
                public void onClick(TabView aSender) {
                    if (mSelecting) {
                        if (aSender.isSelected()) {
                            aSender.setSelected(false);
                            mSelectedTabs.remove(aSender.getSession());
                        } else {
                            aSender.setSelected(true);
                            mSelectedTabs.add(aSender.getSession());
                        }
                        updateSelectionMode();
                        return;
                    }
                    if (mTabDelegate != null) {
                        mTabDelegate.onTabSelect(aSender.getSession());
                    }
                    onDismiss();
                }

                @Override
                public void onAdd(TabView aSender) {
                    if (mTabDelegate != null) {
                        mTabDelegate.onTabAdd();
                    }
                    onDismiss();
                }

                @Override
                public void onSend(TabView aSender) {
                    mSendTabDialog = SendTabDialogWidget.getInstance(getContext());
                    mSendTabDialog.setSessionId(aSender.getSession().getId());
                    mSendTabDialog.mWidgetPlacement.parentHandle = mWidgetManager.getFocusedWindow().getHandle();
                    mSendTabDialog.setDelegate(() -> show(REQUEST_FOCUS));
                    mSendTabDialog.show(UIWidget.REQUEST_FOCUS);

                    holder.tabView.reset();
                }
            });
        }

        @Override
        public int getItemCount() {
            return mTabs.size() + 1;
        }
    }

    private Runnable mSelectModeBackHandler = this::exitSelectMode;

    private void enterSelectMode() {
        if (mSelecting) {
            return;
        }
        mSelecting = true;
        mSelectTabsButton.setVisibility(View.GONE);
        mDoneButton.setVisibility(View.VISIBLE);
        updateSelectionMode();
        mWidgetManager.pushBackHandler(mSelectModeBackHandler);

        post(() -> mAdapter.notifyDataSetChanged());
    }

    private void exitSelectMode() {
        if (!mSelecting) {
            return;
        }
        mSelecting = false;
        mSelectTabsButton.setVisibility(View.VISIBLE);
        mDoneButton.setVisibility(View.GONE);
        mSelectedTabs.clear();
        updateSelectionMode();
        mWidgetManager.popBackHandler(mSelectModeBackHandler);

        post(() -> mAdapter.notifyDataSetChanged());
    }

    private void updateSelectionMode() {
        mTabsSelectModeView.setVisibility(mSelecting ? View.VISIBLE : View.GONE);
        if (mSelectedTabs.size() > 0) {
            mCloseTabsButton.setVisibility(View.VISIBLE);
            mUnselectTabs.setVisibility(View.VISIBLE);
            mCloseTabsAllButton.setVisibility(View.GONE);
            mSelectAllButton.setVisibility(View.GONE);
        } else {
            mCloseTabsButton.setVisibility(View.GONE);
            mUnselectTabs.setVisibility(View.GONE);
            mCloseTabsAllButton.setVisibility(View.VISIBLE);
            mSelectAllButton.setVisibility(View.VISIBLE);
        }

        if (mSelecting) {
            if (mSelectedTabs.size() == 0) {
                mSelectedTabsCounter.setText(R.string.tabs_selected_counter_none);
            } else if (mSelectedTabs.size() > 1) {
                mSelectedTabsCounter.setText(getContext().getString(R.string.tabs_selected_counter_plural, String.valueOf(mSelectedTabs.size())));
            } else {
                mSelectedTabsCounter.setText(R.string.tabs_selected_counter_singular);
            }
        }
    }

    @Override
    protected void onDismiss() {
        exitSelectMode();
    }

    public class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
        private int mColumns;
        private int mSpacingH;
        private int mSpacingV;


        public GridSpacingItemDecoration(Context aContext, int aColumns) {
            mColumns = aColumns;
            mSpacingH = WidgetPlacement.pixelDimension(aContext, R.dimen.tabs_spacing_h);
            mSpacingV = WidgetPlacement.pixelDimension(aContext, R.dimen.tabs_spacing_h);
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view); // item position
            int row = position / mColumns;

            outRect.left = mSpacingH / 2;
            outRect.right = mSpacingH / 2;
            outRect.top = row > 0 ? mSpacingV : 0;
        }
    }

}
