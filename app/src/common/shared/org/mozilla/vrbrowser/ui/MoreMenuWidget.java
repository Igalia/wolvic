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
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.mozilla.vrbrowser.R;

import java.util.ArrayList;

public class MoreMenuWidget extends UIWidget {
    private MenuAdapter mAdapter;
    private MoreMenuWidget.Delegate mDelegate;
    private MenuItem mPrivateBrowsingItem;

    public MoreMenuWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public MoreMenuWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public MoreMenuWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    public void setDelegate(MoreMenuWidget.Delegate aDelegate) {
        mDelegate = aDelegate;
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.more_menu, this);
        ImageButton closeButton = findViewById(R.id.moreMenuCloseButton);
        ListView listView = findViewById(R.id.moreMenuList);

        closeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mDelegate != null) {
                    mDelegate.onMenuCloseClick();
                }
            }
        });

        final ArrayList<MenuItem> items = new ArrayList<>();
        mPrivateBrowsingItem = new MenuItem(R.string.menu_enter_private_browsing, R.drawable.ic_icon_menu_private_browsing, new Runnable() {
            @Override
            public void run() {
                if (mDelegate != null) {
                    mDelegate.onTogglePrivateBrowsing();
                }
            }
        });
        items.add(mPrivateBrowsingItem);

        items.add(new MenuItem(R.string.menu_focus_mode, R.drawable.ic_icon_focus_mode, new Runnable() {
            @Override
            public void run() {
                if (mDelegate != null) {
                    mDelegate.onFocusModeClick();
                }
            }
        }));

        mAdapter = new MenuAdapter(aContext, items);
        listView.setAdapter(mAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position,
                                    long id) {
                MenuItem item = items.get(position);
                if (item.mCallback != null) {
                    item.mCallback.run();
                }
            }
        });
    }

    public void updatePrivateBrowsing(boolean aIsPrivate) {
        int stringId = aIsPrivate ? R.string.menu_exit_private_browsing : R.string.menu_enter_private_browsing;
        if (mPrivateBrowsingItem.mStringId != stringId) {
            mPrivateBrowsingItem.mStringId = stringId;
            mAdapter.notifyDataSetChanged();
        }
    }

    public interface Delegate {
        void onTogglePrivateBrowsing();
        void onFocusModeClick();
        void onMenuCloseClick();
    }

    class MenuItem {
        int mStringId;
        int mImageId;
        Runnable mCallback;

        MenuItem(int aStringId, int aImage, Runnable aCallback) {
            mStringId = aStringId;
            mImageId = aImage;
            mCallback = aCallback;
        }
    }

    class MenuAdapter extends BaseAdapter {
        private Context mContext;
        private ArrayList<MenuItem> mItems;
        private LayoutInflater mInflater;

        MenuAdapter(Context aContext, ArrayList<MenuItem> aItems) {
            mContext = aContext;
            mItems = aItems;
            mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
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

            textView.setText(mContext.getString(item.mStringId));
            imageView.setImageResource(item.mImageId);

            return view;
        }
    }
}
