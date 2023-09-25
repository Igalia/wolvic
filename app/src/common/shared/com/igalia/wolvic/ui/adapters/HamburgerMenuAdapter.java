package com.igalia.wolvic.ui.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserApplication;
import com.igalia.wolvic.browser.Addons;
import com.igalia.wolvic.databinding.HamburgerMenuAddonItemBinding;
import com.igalia.wolvic.databinding.HamburgerMenuAddonsSettingsItemBinding;
import com.igalia.wolvic.databinding.HamburgerMenuItemBinding;
import com.igalia.wolvic.utils.SystemUtils;
import com.igalia.wolvic.utils.ViewUtils;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Function;

import mozilla.components.concept.engine.webextension.Action;

public class HamburgerMenuAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    static final String LOGTAG = SystemUtils.createLogtag(HamburgerMenuAdapter.class);

    private Context mContext;
    private List<MenuItem> mItemList;

    public static class MenuItem {

        @IntDef(value = { TYPE_DEFAULT, TYPE_ADDON, TYPE_ADDONS_SETTINGS })
        public @interface MenuItemType {}
        public static final int TYPE_DEFAULT = 0;
        public static final int TYPE_ADDON = 1;
        public static final int TYPE_ADDONS_SETTINGS = 2;

        @MenuItem.MenuItemType
        int mItemType;
        int mId;
        String mAddonId;
        String mTitle;
        @DrawableRes
        int mIcon;
        Function<MenuItem, Void> mCallback;
        Action mAction;

        public MenuItem(@NonNull Builder builder) {
            mItemType = builder.mItemType;
            mId = builder.mId;
            mAddonId = builder.mAddonId;
            mTitle = builder.mTitle;
            mIcon = builder.mIcon;
            mCallback = builder.mCallback;
            mAction = builder.mAction;
        }

        public int getItemType() {
            return mItemType;
        }

        public int getId() {
            return mId;
        }

        public String getAddonId() {
            return mAddonId;
        }

        public String getTitle() {
            return mTitle;
        }

        public int getIcon() {
            return mIcon;
        }

        public Function<MenuItem, Void> getCallback() {
            return mCallback;
        }

        public void setCallback(@NonNull Function<MenuItem, Void> callback) {
            mCallback = callback;
        }

        public Action getAction() {
            return mAction;
        }

        public void setAction(Action action) {
            mAction = action;
        }

        public void setIcon(@DrawableRes int icon) {
            mIcon = icon;
        }

        public void setTitle(String title) {
            mTitle = title;
        }

        public static class Builder {

            @MenuItem.MenuItemType
            int mItemType;
            int mId = -1;
            String mAddonId = "";
            String mTitle;
            @DrawableRes
            int mIcon;
            Function<MenuItem, Void> mCallback;
            Action mAction;

            public Builder(@MenuItem.MenuItemType int type, @Nullable Function<MenuItem, Void> callback) {
                this.mItemType = type;
                this.mCallback = callback;
            }

            public MenuItem.Builder withId(int id) {
                this.mId = id;
                return this;
            }

            public MenuItem.Builder withAddonId(@NonNull String addonId) {
                this.mAddonId = addonId;
                return this;
            }

            public MenuItem.Builder withTitle(@Nullable String title) {
                this.mTitle = title;
                return this;
            }

            public MenuItem.Builder withIcon(@DrawableRes int icon) {
                this.mIcon = icon;
                return this;
            }

            public MenuItem.Builder withAction(@Nullable Action action) {
                this.mAction = action;
                return this;
            }

            public MenuItem build(){
                return new MenuItem(this);
            }
        }
    }

    private Executor mMainExecutor;

    public HamburgerMenuAdapter(@NonNull Context aContext) {
        mContext = aContext;
        mMainExecutor = ((VRBrowserApplication)mContext.getApplicationContext()).getExecutors().mainThread();
        setHasStableIds(true);
    }

    public void setItems(final List<MenuItem> list) {
        if (mItemList == null) {
            mItemList = list;
            notifyItemRangeInserted(0, list.size());

        } else {
            DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return mItemList.size();
                }

                @Override
                public int getNewListSize() {
                    return list.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    return mItemList.get(oldItemPosition).mTitle == list.get(newItemPosition).mTitle;
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    MenuItem newItem = list.get(newItemPosition);
                    MenuItem oldItem = mItemList.get(oldItemPosition);
                    return Objects.equals(newItem.mItemType, oldItem.mItemType) &&
                            Objects.equals(newItem.mTitle, oldItem.mTitle) &&
                            Objects.equals(newItem.mIcon, oldItem.mIcon) &&
                            Objects.equals(newItem.mCallback, oldItem.mCallback) &&
                            Objects.equals(newItem.mAction, oldItem.mAction) &&
                            Objects.equals(newItem.mAddonId, oldItem.mAddonId);
                }
            });

            mItemList = list;
            result.dispatchUpdatesTo(this);
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == MenuItem.TYPE_DEFAULT) {
            HamburgerMenuItemBinding binding = DataBindingUtil
                    .inflate(LayoutInflater.from(parent.getContext()), R.layout.hamburger_menu_item,
                            parent, false);

            return new HamburgerMenuItemHolder(binding);

         } else if (viewType == MenuItem.TYPE_ADDON){
            HamburgerMenuAddonItemBinding binding = DataBindingUtil
                    .inflate(LayoutInflater.from(parent.getContext()), R.layout.hamburger_menu_addon_item,
                            parent, false);

            return new HamburgerMenuItemAddonHolder(binding);

        } else if (viewType == MenuItem.TYPE_ADDONS_SETTINGS){
            HamburgerMenuAddonsSettingsItemBinding binding = DataBindingUtil
                    .inflate(LayoutInflater.from(parent.getContext()), R.layout.hamburger_menu_addons_settings_item,
                            parent, false);

            return new HamburgerMenuItemAddonsSettingsHolder(binding);
        }

        throw new RuntimeException("there is no type that matches the type " + viewType + " + make sure your using types correctly");
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        final MenuItem item = mItemList.get(position);
        final View.OnClickListener callback = view -> {
            if (item.mCallback != null) {
                item.mCallback.apply(item);
            }
        };

        if (holder instanceof HamburgerMenuItemHolder) {
            HamburgerMenuItemHolder viewHolder = (HamburgerMenuItemHolder) holder;
            viewHolder.binding.setItem(item);
            viewHolder.binding.container.setTag(R.string.position_tag, position);
            viewHolder.binding.container.setOnHoverListener(mHoverListener);
            ViewUtils.setStickyClickListener(viewHolder.binding.container, callback);
            setBackground(viewHolder.binding.container, item, position);

        } else if (holder instanceof HamburgerMenuItemAddonHolder) {
            HamburgerMenuItemAddonHolder viewHolder = (HamburgerMenuItemAddonHolder) holder;
            viewHolder.binding.setItem(item);
            viewHolder.binding.container.setTag(R.string.position_tag, position);
            viewHolder.binding.container.setOnHoverListener(mHoverListener);
            if (item.mAction != null) {
                if (item.mAction.getBadgeBackgroundColor() != null) {
                    viewHolder.binding.badge.setBackgroundColor(item.mAction.getBadgeBackgroundColor());
                }
                if (item.mAction.getBadgeTextColor() != null) {
                    viewHolder.binding.badge.setTextColor(item.mAction.getBadgeTextColor());
                }
                if (item.mAction.getBadgeText() != null && !item.mAction.getBadgeText().isEmpty()) {
                    viewHolder.binding.badge.setVisibility(View.VISIBLE);
                }
                Addons.Companion.loadActionIcon(
                    mContext,
                    item.mAction,
                    viewHolder.binding.listItemImage.getWidth())
                    .thenAcceptAsync(
                            viewHolder.binding.listItemImage::setImageDrawable,
                            mMainExecutor)
                    .exceptionally(throwable -> {
                        Log.d(LOGTAG, "Error loading extension icon: " + throwable.getLocalizedMessage());
                        throwable.printStackTrace();
                        return null;
                    });
            }
            ViewUtils.setStickyClickListener(viewHolder.binding.container, callback);
            setBackground(viewHolder.binding.container, item, position);

        } else if (holder instanceof HamburgerMenuItemAddonsSettingsHolder) {
            HamburgerMenuItemAddonsSettingsHolder viewHolder = (HamburgerMenuItemAddonsSettingsHolder) holder;
            viewHolder.binding.setItem(item);
            viewHolder.binding.container.setTag(R.string.position_tag, position);
            viewHolder.binding.container.setOnHoverListener(mHoverListener);
            ViewUtils.setStickyClickListener(viewHolder.binding.container, callback);
            setBackground(viewHolder.binding.container, item, position);
        }
    }

    @Override
    public int getItemCount() {
        return mItemList == null ? 0 : mItemList.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        if (mItemList != null && mItemList.get(position) != null) {
            return mItemList.get(position).mItemType;
        }

        return MenuItem.TYPE_DEFAULT;
    }

    private void setBackground(@NonNull View view, @NonNull MenuItem item, int position) {
        if (position == 0) {
            if (position == mItemList.size()-1) {
                if (item.getItemType() == MenuItem.TYPE_ADDON) {
                    view.setBackgroundResource(R.drawable.context_menu_item_background_single_dark);

                } else {
                    view.setBackgroundResource(R.drawable.context_menu_item_background_single);
                }

            } else {
                if (item.getItemType() == MenuItem.TYPE_ADDON) {
                    view.setBackgroundResource(R.drawable.context_menu_item_background_first_dark);

                } else {
                    view.setBackgroundResource(R.drawable.context_menu_item_background_first);
                }
            }

        } else if (position == mItemList.size()-1) {
            if (item.getItemType() == MenuItem.TYPE_ADDON) {
                view.setBackgroundResource(R.drawable.context_menu_item_background_last_dark);

            } else {
                view.setBackgroundResource(R.drawable.context_menu_item_background_last);
            }

        } else {
            if (item.getItemType() == MenuItem.TYPE_ADDON) {
                view.setBackgroundResource(R.drawable.context_menu_item_background_dark);

            } else {
                view.setBackgroundResource(R.drawable.context_menu_item_background);
            }
        }
    }

    private View.OnHoverListener mHoverListener = (view, motionEvent) -> {
        int position = (int)view.getTag(R.string.position_tag);

        if (mItemList.size() <= position) {
            return false;
        }

        MenuItem item = mItemList.get(position);
        if (item.mCallback == null) {
            return false;
        }

        TextView label = view.findViewById(R.id.listItemText);
        switch (motionEvent.getActionMasked()) {
            case MotionEvent.ACTION_HOVER_ENTER:
                label.setShadowLayer(label.getShadowRadius(), label.getShadowDx(), label.getShadowDy(), mContext.getColor(R.color.text_shadow_light));
                return false;

            case MotionEvent.ACTION_HOVER_EXIT:
                label.setShadowLayer(label.getShadowRadius(), label.getShadowDx(), label.getShadowDy(), mContext.getColor(R.color.text_shadow));
                return false;
        }

        return false;
    };

    static class HamburgerMenuItemHolder extends RecyclerView.ViewHolder {

        final HamburgerMenuItemBinding binding;

        HamburgerMenuItemHolder(@NonNull HamburgerMenuItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class HamburgerMenuItemAddonHolder extends RecyclerView.ViewHolder {

        final HamburgerMenuAddonItemBinding binding;

        HamburgerMenuItemAddonHolder(@NonNull HamburgerMenuAddonItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class HamburgerMenuItemAddonsSettingsHolder extends RecyclerView.ViewHolder {

        final HamburgerMenuAddonsSettingsItemBinding binding;

        HamburgerMenuItemAddonsSettingsHolder(@NonNull HamburgerMenuAddonsSettingsItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

}
