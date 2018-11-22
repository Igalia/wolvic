
/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.mozilla.vrbrowser.R;

import java.util.ArrayList;

public abstract class MenuWidget extends UIWidget {
    protected MenuAdapter mAdapter;
    protected ListView mListView;

    public MenuWidget(Context aContext) {
        super(aContext);
        initialize(aContext, null);
    }

    public MenuWidget(Context aContext, ArrayList<MenuItem> aItems) {
        super(aContext);
        initialize(aContext, aItems);
    }

    private void initialize(Context aContext, ArrayList<MenuItem> aItems) {
        inflate(aContext, R.layout.menu, this);
        mListView = findViewById(R.id.menuListView);


        mAdapter = new MenuAdapter(aContext, aItems);
        mListView.setAdapter(mAdapter);
        mListView.setSoundEffectsEnabled(false);
        mListView.setVerticalScrollBarEnabled(false);
        mListView.setFastScrollAlwaysVisible(false);

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position,
                                    long id) {
                setSelectedItem(position);
                MenuItem item = mAdapter.mItems.get(position);
                if (item.mCallback != null) {
                    item.mCallback.run();
                }
            }
        });
    }

    public void updateMenuItems(ArrayList<MenuItem> aItems) {
        mAdapter.mItems = aItems;
        mAdapter.notifyDataSetChanged();
    }

    public void setSelectedItem(int aPosition) {
        mListView.setItemChecked(aPosition, true);
    }

    public int getSelectedItem() {
        return mListView.getCheckedItemPosition();
    }

    public class MenuItem {
        int mStringId;
        int mImageId;
        Runnable mCallback;

        MenuItem(int aStringId, int aImage, Runnable aCallback) {
            mStringId = aStringId;
            mImageId = aImage;
            mCallback = aCallback;
        }
    }

    class MenuAdapter extends BaseAdapter implements OnHoverListener {
        private Context mContext;
        private ArrayList<MenuItem> mItems;
        private LayoutInflater mInflater;
        private Drawable firstItemDrawable;
        private Drawable lastItemDrawable;

        MenuAdapter(Context aContext, ArrayList<MenuItem> aItems) {
            mContext = aContext;
            mItems = aItems != null ? aItems : new ArrayList<MenuItem>();
            mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            firstItemDrawable = aContext.getDrawable(R.drawable.menu_item_background_first);
            lastItemDrawable = aContext.getDrawable(R.drawable.menu_item_background_last);
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
                view = mInflater.inflate(R.layout.menu_item_image_text, parent, false);
                view.setOnHoverListener(this);
            }
            view.setTag(R.string.position_tag, position);
            if (position == 0) {
                view.setBackground(firstItemDrawable);
            } else if (position == mItems.size() - 1) {
                view.setBackground(lastItemDrawable);
            }

            MenuItem item = mItems.get(position);

            TextView textView = view.findViewById(R.id.listItemText);
            ImageView imageView = view.findViewById(R.id.listItemImage);

            textView.setText(mContext.getString(item.mStringId));
            if (item.mImageId > 0) {
                imageView.setImageResource(item.mImageId);
            } else {
                imageView.setVisibility(View.GONE);
                textView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
            }

            return view;
        }

        @Override
        public boolean onHover(View view, MotionEvent event) {
            int position = (int)view.getTag(R.string.position_tag);
            if (!isEnabled(position))
                return false;

            TextView label = view.findViewById(R.id.listItemText);
            ImageView image = view.findViewById(R.id.listItemImage);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    view.setHovered(true);
                    label.setHovered(true);
                    image.setHovered(true);
                    return true;

                case MotionEvent.ACTION_HOVER_EXIT:
                    view.setHovered(false);
                    label.setHovered(false);
                    image.setHovered(false);
                    return true;
            }

            return false;
        }
    }
}
