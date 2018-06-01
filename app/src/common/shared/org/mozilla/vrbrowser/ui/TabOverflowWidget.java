/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.SessionStore;
import org.mozilla.vrbrowser.WidgetPlacement;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class TabOverflowWidget extends UIWidget {
    private Delegate mDelegate;
    private MenuAdapter mAdapter;

    public TabOverflowWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public TabOverflowWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public TabOverflowWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }


    public void setDelegate(Delegate aDelegate) {
        mDelegate = aDelegate;
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.tab_overflow_list, this);
        ListView listView = findViewById(R.id.tabList);

        mAdapter = new MenuAdapter(aContext);
        listView.setAdapter(mAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                MenuItem item = (MenuItem)mAdapter.getItem(position);
                if (mDelegate != null) {
                    mDelegate.onTabOverflowClick(item.mSessionId);
                }
            }
        });
    }

    @Override
    void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.width = 350;
        aPlacement.height = 275;
        aPlacement.parentAnchorX = 1.0f;
        aPlacement.parentAnchorY = 0.0f;
        aPlacement.anchorX = 1.0f;
        aPlacement.anchorY = 1.0f;
        aPlacement.translationX = -10.0f;
        aPlacement.translationY = -120.0f;
        aPlacement.translationZ = 2.0f;
    }

    public void updatePrivateBrowsing(boolean mPrivateMode) {
        mAdapter.setUsePrivateTabs(mPrivateMode);
    }

    public void onShow() {
        mAdapter.onShow();
    }

    public void onHide() {
        mAdapter.onHide();
    }


    @Override
    public void releaseWidget() {
        super.releaseWidget();
        mAdapter.onHide();
    }

    public interface Delegate {
        void onTabOverflowClick(int sessionId);
    }

    class MenuItem {
        int mSessionId;
        String mTitle;

        MenuItem(int sessionId, String title) {
            mSessionId = sessionId;
            mTitle = title;
        }
    }

    class MenuAdapter extends BaseAdapter implements GeckoSession.ContentDelegate, SessionStore.SessionChangeListener  {
        private Context mContext;
        private ArrayList<MenuItem> mItems;
        private LayoutInflater mInflater;
        private boolean mUsePrivateTabs = false;
        private boolean mActive = false;

        MenuAdapter(Context aContext) {
            mContext = aContext;
            mItems = new ArrayList<>();
            mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            onShow();
        }

        void resetTabs() {
            final List<Integer> sessionIds =  SessionStore.get().getSessionsByPrivateMode(mUsePrivateTabs);
            // Remove items not existing anymore
            mItems.removeIf(new Predicate<MenuItem>() {
                @Override
                public boolean test(MenuItem menuItem) {
                    return !sessionIds.contains(menuItem.mSessionId);
                }
            });
            // Add new items
            for (Integer sessionId: sessionIds) {
                if (getBySessionId(sessionId) == null) {
                    mItems.add(new MenuItem(sessionId, ""));
                }
            }
        }

        MenuItem getBySessionId(int sessionId) {
            for (MenuItem item: mItems) {
                if (sessionId == item.mSessionId) {
                    return item;
                }
            }

            return null;
        }

        void setUsePrivateTabs(boolean aUsePrivate) {
            if (mUsePrivateTabs != aUsePrivate) {
                mUsePrivateTabs = aUsePrivate;
                resetTabs();
                notifyDataSetChanged();
            }
        }

        void onShow() {
            SessionStore.get().addContentListener(this);
            SessionStore.get().addSessionChangeListener(this);
            resetTabs();
            for (MenuItem item: mItems) {
                SessionStore.get().dumpState(SessionStore.get().getSession(item.mSessionId), this);
            }
            notifyDataSetChanged();
        }

        void onHide() {
            SessionStore.get().removeContentListener(this);
            SessionStore.get().removeContentListener(this);
        }

        @Override
        public int getCount() {
            return mItems.size();
        }

        @Override
        public Object getItem(int position) {
            return mItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = mInflater.inflate(R.layout.image_text_list_item, parent, false);
            }

            MenuItem item = mItems.get(position);

            TextView textView = view.findViewById(R.id.listItemText);
            ImageView imageView = view.findViewById(R.id.listItemImage);

            textView.setText(item.mTitle);
            imageView.setImageResource(R.drawable.ic_icon_favicon_placeholder);

            return view;
        }


        // GeckoSession ContentDelegate
        @Override
        public void onTitleChange(GeckoSession aSession, String aTitle) {
            MenuItem item = getBySessionId(SessionStore.get().getSessionId(aSession));
            if (item != null) {
                item.mTitle = aTitle;
                notifyDataSetChanged();
            }
        }

        @Override
        public void onFocusRequest(GeckoSession aSession) {
        }

        @Override
        public void onCloseRequest(GeckoSession aSession) {
        }

        @Override
        public void onFullScreen(GeckoSession aSession, boolean b) {
        }

        @Override
        public void onContextMenu(GeckoSession aSession, int i, int i1, String s, int i2, String s1) {
        }

        @Override
        public void onExternalResponse(GeckoSession aSession, GeckoSession.WebResponseInfo webResponseInfo) {
        }

        // SessionStore.SessionChangeListener
        @Override
        public void onNewSession(GeckoSession aSession, int aId) {
            MenuItem item = getBySessionId(aId);
            if (item == null) {
                mItems.add(new MenuItem(aId, ""));
                notifyDataSetChanged();
            }
        }

        @Override
        public void onRemoveSession(GeckoSession aSession, int aId) {
            MenuItem item = getBySessionId(aId);
            if (item != null) {
                mItems.remove(item);
                notifyDataSetChanged();
            }
        }

        @Override
        public void onCurrentSessionChange(GeckoSession aSession, int aId) {

        }
    }
}
