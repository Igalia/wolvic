package com.igalia.wolvic.ui.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.ui.views.TabsBarItem;
import com.igalia.wolvic.ui.widgets.TabDelegate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TabsBarAdapter extends RecyclerView.Adapter<TabsBarAdapter.ViewHolder> {

    public enum Orientation {HORIZONTAL, VERTICAL}

    private final TabDelegate mTabDelegate;
    private final Orientation mOrientation;
    private final List<Session> mTabs = new ArrayList<>();

    static class ViewHolder extends RecyclerView.ViewHolder {
        TabsBarItem mTabBarItem;

        ViewHolder(TabsBarItem v) {
            super(v);
            mTabBarItem = v;
        }
    }

    public TabsBarAdapter(@NonNull TabDelegate tabDelegate, Orientation orientation) {
        mTabDelegate = tabDelegate;
        mOrientation = orientation;
    }

    @Override
    public long getItemId(int position) {
        return (position == 0) ? 0 : mTabs.get(position - 1).getId().hashCode();
    }

    public void updateTabs(List<Session> aTabs) {
        mTabs.clear();
        mTabs.addAll(aTabs);

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        @LayoutRes int layout;
        if (mOrientation == Orientation.HORIZONTAL) {
            layout = R.layout.tabs_bar_item_horizontal;
        } else {
            layout = R.layout.tabs_bar_item_vertical;
        }
        TabsBarItem view = (TabsBarItem) LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.mTabBarItem.setDelegate(mItemDelegate);

        Session session = mTabs.get(position);
        holder.mTabBarItem.attachToSession(session);
    }

    @Override
    public int getItemCount() {
        return mTabs.size();
    }

    private final TabsBarItem.Delegate mItemDelegate = new TabsBarItem.Delegate() {
        @Override
        public void onClick(TabsBarItem item) {
            mTabDelegate.onTabSelect(item.getSession());
        }

        @Override
        public void onClose(TabsBarItem item) {
            mTabDelegate.onTabsClose(Collections.singletonList(item.getSession()));
        }
    };
}
