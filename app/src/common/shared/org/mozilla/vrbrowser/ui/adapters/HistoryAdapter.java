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
import org.mozilla.vrbrowser.ui.callbacks.HistoryItemCallback;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.AnimationHelper;

import java.util.List;
import java.util.Objects;

import mozilla.components.concept.storage.VisitInfo;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryItemViewHolder> {

    static final String LOGTAG = HistoryAdapter.class.getSimpleName();

    private static final int ICON_ANIMATION_DURATION = 200;

    private List<? extends VisitInfo> mHistoryList;

    private int mMinPadding;
    private int mMaxPadding;
    private int mIconColorHover;
    private int mIconNormalColor;

    @Nullable
    private final HistoryItemCallback mHistoryItemCallback;

    public HistoryAdapter(@Nullable HistoryItemCallback clickCallback, Context aContext) {
        mHistoryItemCallback = clickCallback;

        mMinPadding = WidgetPlacement.pixelDimension(aContext, R.dimen.library_icon_padding_min);
        mMaxPadding = WidgetPlacement.pixelDimension(aContext, R.dimen.library_icon_padding_max);

        mIconColorHover = aContext.getResources().getColor(R.color.white, aContext.getTheme());
        mIconNormalColor = aContext.getResources().getColor(R.color.rhino, aContext.getTheme());

        setHasStableIds(true);
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
        return mHistoryList != null ? mHistoryList.size() : 0;
    }

    public int getItemPosition(long id) {
        for (int position=0; position<mHistoryList.size(); position++)
            if (mHistoryList.get(position).getVisitTime() == id)
                return position;
        return 0;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public HistoryItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        HistoryItemBinding binding = DataBindingUtil
                .inflate(LayoutInflater.from(parent.getContext()), R.layout.history_item,
                        parent, false);
        binding.setCallback(mHistoryItemCallback);
        binding.setIsHovered(false);
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
                    binding.setIsHovered(true);
                    return false;
            }
            return false;
        });
        binding.more.setOnHoverListener(mIconHoverListener);
        binding.more.setOnTouchListener((view, motionEvent) -> {
            int ev = motionEvent.getActionMasked();
            switch (ev) {
                case MotionEvent.ACTION_UP:
                    mHistoryItemCallback.onMore(view, binding.getItem());
                    return true;

                case MotionEvent.ACTION_DOWN:
                    return true;
            }
            return false;
        });
        binding.trash.setOnHoverListener(mIconHoverListener);
        binding.trash.setOnTouchListener((view, motionEvent) -> {
            int ev = motionEvent.getActionMasked();
            switch (ev) {
                case MotionEvent.ACTION_UP:
                    mHistoryItemCallback.onDelete(view, binding.getItem());
                    return true;

                case MotionEvent.ACTION_DOWN:
                    return true;
            }
            return false;
        });
        return new HistoryItemViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryItemViewHolder holder, int position) {
        holder.binding.setItem(mHistoryList.get(position));
        holder.binding.executePendingBindings();
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

    static class HistoryItemViewHolder extends RecyclerView.ViewHolder {

        final HistoryItemBinding binding;

        HistoryItemViewHolder(@NonNull HistoryItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private View.OnHoverListener mIconHoverListener = (view, motionEvent) -> {
        ImageView icon = (ImageView)view;
        int ev = motionEvent.getActionMasked();
        switch (ev) {
            case MotionEvent.ACTION_HOVER_ENTER:
                icon.setColorFilter(mIconColorHover);
                AnimationHelper.animateViewPadding(view,
                        mMaxPadding,
                        mMinPadding,
                        ICON_ANIMATION_DURATION);
                return false;

            case MotionEvent.ACTION_HOVER_EXIT:
                AnimationHelper.animateViewPadding(view,
                        mMinPadding,
                        mMaxPadding,
                        ICON_ANIMATION_DURATION,
                        () -> icon.setColorFilter(mIconNormalColor));
                return false;
        }

        return false;
    };

}
