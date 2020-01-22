package org.mozilla.vrbrowser.ui.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.databinding.HistoryItemBinding;
import org.mozilla.vrbrowser.databinding.HistoryItemHeaderBinding;
import org.mozilla.vrbrowser.ui.callbacks.HistoryItemCallback;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.AnimationHelper;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.util.List;
import java.util.Objects;

import mozilla.components.concept.storage.VisitInfo;
import mozilla.components.concept.storage.VisitType;

public class HistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    static final String LOGTAG = SystemUtils.createLogtag(HistoryAdapter.class);

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private static final int ICON_ANIMATION_DURATION = 200;

    private List<? extends VisitInfo> mHistoryList;

    private int mMinPadding;
    private int mMaxPadding;
    private boolean mIsNarrowLayout;

    @Nullable
    private final HistoryItemCallback mHistoryItemCallback;

    public HistoryAdapter(@Nullable HistoryItemCallback clickCallback, Context aContext) {
        mHistoryItemCallback = clickCallback;

        mMinPadding = WidgetPlacement.pixelDimension(aContext, R.dimen.library_icon_padding_min);
        mMaxPadding = WidgetPlacement.pixelDimension(aContext, R.dimen.library_icon_padding_max);

        mIsNarrowLayout = false;

        setHasStableIds(true);
    }

    public void setNarrow(boolean isNarrow) {
        if (mIsNarrowLayout != isNarrow) {
            mIsNarrowLayout = isNarrow;
            notifyDataSetChanged();
        }
    }

    public void setHistoryList(final List<? extends VisitInfo> historyList) {
        if (mHistoryList == null) {
            mHistoryList = historyList;
            notifyItemRangeInserted(0, historyList.size());

        } else {
            DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return mHistoryList.size();
                }

                @Override
                public int getNewListSize() {
                    return historyList.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    return mHistoryList.get(oldItemPosition).getVisitTime() == historyList.get(newItemPosition).getVisitTime();
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    VisitInfo newHistoryItem = historyList.get(newItemPosition);
                    VisitInfo oldHistoryItem = mHistoryList.get(oldItemPosition);
                    return newHistoryItem.getVisitTime() == oldHistoryItem.getVisitTime()
                            && Objects.equals(newHistoryItem.getTitle(), oldHistoryItem.getTitle())
                            && Objects.equals(newHistoryItem.getUrl(), oldHistoryItem.getUrl());
                }
            });

            mHistoryList = historyList;
            result.dispatchUpdatesTo(this);
        }
    }

    public void removeItem(VisitInfo historyItem) {
        int position = mHistoryList.indexOf(historyItem);
        if (position >= 0) {
            mHistoryList.remove(position);
            notifyItemRemoved(position);
        }
    }

    public int itemCount() {
        if (mHistoryList != null) {
            return mHistoryList.stream().allMatch(item ->
                    item.getVisitType() == VisitType.NOT_A_VISIT) ?
                    0 :
                    mHistoryList.size();
        }

        return 0;
    }

    public int getItemPosition(long id) {
        for (int position=0; position<mHistoryList.size(); position++)
            if (mHistoryList.get(position).getVisitTime() == id)
                return position;
        return 0;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_ITEM) {
            HistoryItemBinding binding = DataBindingUtil
                    .inflate(LayoutInflater.from(parent.getContext()), R.layout.history_item,
                            parent, false);
            binding.setCallback(mHistoryItemCallback);
            binding.setIsHovered(false);
            binding.setIsNarrow(mIsNarrowLayout);
            binding.layout.setOnHoverListener((view, motionEvent) -> {
                int ev = motionEvent.getActionMasked();
                switch (ev) {
                    case MotionEvent.ACTION_HOVER_ENTER:
                        binding.setIsHovered(true);
                        return false;

                    case MotionEvent.ACTION_HOVER_EXIT:
                        binding.setIsHovered(false);
                        return false;
                }

                return false;
            });
            binding.layout.setOnTouchListener((view, motionEvent) -> {
                int ev = motionEvent.getActionMasked();
                switch (ev) {
                    case MotionEvent.ACTION_UP:
                        return false;

                    case MotionEvent.ACTION_DOWN:
                        binding.more.setImageState(new int[]{android.R.attr.state_active},false);
                        binding.trash.setImageState(new int[]{android.R.attr.state_active},false);
                        binding.setIsHovered(true);
                        return false;

                    case MotionEvent.ACTION_CANCEL:
                        binding.setIsHovered(false);
                        return false;
                }
                return false;
            });
            binding.more.setOnHoverListener(mIconHoverListener);
            binding.more.setOnTouchListener((view, motionEvent) -> {
                binding.setIsHovered(true);
                int ev = motionEvent.getActionMasked();
                switch (ev) {
                    case MotionEvent.ACTION_UP:
                        if (mHistoryItemCallback != null) {
                            mHistoryItemCallback.onMore(view, binding.getItem());
                        }
                        binding.more.setImageState(new int[]{android.R.attr.state_active},true);
                        return true;

                    case MotionEvent.ACTION_DOWN:
                        binding.more.setImageState(new int[]{android.R.attr.state_pressed},true);
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        binding.setIsHovered(false);
                        binding.more.setImageState(new int[]{android.R.attr.state_active},true);
                        return false;
                }
                return false;
            });
            binding.trash.setOnHoverListener(mIconHoverListener);
            binding.trash.setOnTouchListener((view, motionEvent) -> {
                binding.setIsHovered(true);
                int ev = motionEvent.getActionMasked();
                switch (ev) {
                    case MotionEvent.ACTION_UP:
                        if (mHistoryItemCallback != null) {
                            mHistoryItemCallback.onDelete(view, binding.getItem());
                        }
                        binding.trash.setImageState(new int[]{android.R.attr.state_active},true);
                        return true;

                    case MotionEvent.ACTION_DOWN:
                        binding.trash.setImageState(new int[]{android.R.attr.state_pressed},true);
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        binding.setIsHovered(false);
                        binding.trash.setImageState(new int[]{android.R.attr.state_active},true);
                        return false;
                }
                return false;
            });

            return new HistoryItemViewHolder(binding);

         } else if (viewType == TYPE_HEADER){
            HistoryItemHeaderBinding binding = DataBindingUtil
                    .inflate(LayoutInflater.from(parent.getContext()), R.layout.history_item_header,
                            parent, false);

            return new HistoryItemViewHeaderHolder(binding);
        }

        throw new RuntimeException("there is no type that matches the type " + viewType + " + make sure your using types correctly");
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HistoryItemViewHolder) {
            HistoryItemViewHolder item = (HistoryItemViewHolder) holder;
            item.binding.setItem(mHistoryList.get(position));
            item.binding.setIsNarrow(mIsNarrowLayout);

        } else if (holder instanceof HistoryItemViewHeaderHolder) {
            HistoryItemViewHeaderHolder item = (HistoryItemViewHeaderHolder) holder;
            item.binding.setTitle(mHistoryList.get(position).getTitle());
        }
    }

    @Override
    public int getItemCount() {
        return mHistoryList == null ? 0 : mHistoryList.size();
    }

    @Override
    public long getItemId(int position) {
        VisitInfo historyItem = mHistoryList.get(position);
        return  historyItem.getVisitTime();
    }

    @Override
    public int getItemViewType(int position) {
        if (isPositionHeader(position))
            return TYPE_HEADER;

        return TYPE_ITEM;
    }

    private boolean isPositionHeader(int position) {
        return mHistoryList.get(position).getVisitType() == VisitType.NOT_A_VISIT;
    }

    private View.OnHoverListener mIconHoverListener = (view, motionEvent) -> {
        ImageView icon = (ImageView)view;
        int ev = motionEvent.getActionMasked();
        switch (ev) {
            case MotionEvent.ACTION_HOVER_ENTER:
                icon.setImageState(new int[]{android.R.attr.state_hovered},true);
                AnimationHelper.animateViewPadding(view,
                        mMaxPadding,
                        mMinPadding,
                        ICON_ANIMATION_DURATION);
                return false;

            case MotionEvent.ACTION_HOVER_EXIT:
                icon.setImageState(new int[]{android.R.attr.state_active},true);
                AnimationHelper.animateViewPadding(view,
                        mMinPadding,
                        mMaxPadding,
                        ICON_ANIMATION_DURATION);
                return false;
        }

        return false;
    };

    static class HistoryItemViewHolder extends RecyclerView.ViewHolder {

        final HistoryItemBinding binding;

        HistoryItemViewHolder(@NonNull HistoryItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class HistoryItemViewHeaderHolder extends RecyclerView.ViewHolder {

        final HistoryItemHeaderBinding binding;

        HistoryItemViewHeaderHolder(@NonNull HistoryItemHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

}
