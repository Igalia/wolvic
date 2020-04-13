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
import org.mozilla.vrbrowser.databinding.DownloadItemBinding;
import org.mozilla.vrbrowser.downloads.Download;
import org.mozilla.vrbrowser.ui.callbacks.DownloadItemCallback;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.AnimationHelper;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.util.List;
import java.util.Objects;

public class DownloadsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    static final String LOGTAG = SystemUtils.createLogtag(DownloadsAdapter.class);

    private static final int ICON_ANIMATION_DURATION = 200;

    private List<Download> mDownloadsList;

    private int mMinPadding;
    private int mMaxPadding;
    private boolean mIsNarrowLayout;

    @Nullable
    private final DownloadItemCallback mDownloadItemCallback;

    public DownloadsAdapter(@Nullable DownloadItemCallback clickCallback, Context aContext) {
        mDownloadItemCallback = clickCallback;

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

    public void setDownloadsList(final List<Download> downloadsList) {
        if (mDownloadsList == null) {
            mDownloadsList = downloadsList;
            notifyItemRangeInserted(0, downloadsList.size());

        } else {
            DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return mDownloadsList.size();
                }

                @Override
                public int getNewListSize() {
                    return downloadsList.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    return mDownloadsList.get(oldItemPosition).getUri().equals(downloadsList.get(newItemPosition).getUri());
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    Download newDownloadItem = downloadsList.get(newItemPosition);
                    Download oldDownloadItem = mDownloadsList.get(oldItemPosition);
                    return newDownloadItem.getLastModified() == oldDownloadItem.getLastModified()
                            && Objects.equals(newDownloadItem.getTitle(), oldDownloadItem.getTitle())
                            && Objects.equals(newDownloadItem.getDescription(), oldDownloadItem.getDescription())
                            && Objects.equals(newDownloadItem.getOutputFile(), oldDownloadItem.getOutputFile())
                            && Objects.equals(newDownloadItem.getUri(), oldDownloadItem.getUri());
                }
            });

            mDownloadsList = downloadsList;
            result.dispatchUpdatesTo(this);
        }
    }

    public void removeItem(Download downloadItem) {
        int position = mDownloadsList.indexOf(downloadItem);
        if (position >= 0) {
            mDownloadsList.remove(position);
            notifyItemRemoved(position);
        }
    }

    public int itemCount() {
        if (mDownloadsList != null) {
            return mDownloadsList.size();
        }

        return 0;
    }

    public int getItemPosition(long id) {
        for (int position=0; position<mDownloadsList.size(); position++)
            if (mDownloadsList.get(position).getId() == id)
                return position;
        return 0;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        DownloadItemBinding binding = DataBindingUtil
                .inflate(LayoutInflater.from(parent.getContext()), R.layout.download_item,
                        parent, false);
        binding.setCallback(mDownloadItemCallback);
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
                    if (mDownloadItemCallback != null) {
                        mDownloadItemCallback.onMore(view, binding.getItem());
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
                    if (mDownloadItemCallback != null) {
                        mDownloadItemCallback.onDelete(view, binding.getItem());
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

        return new DownloadItemViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        DownloadItemViewHolder item = (DownloadItemViewHolder) holder;
        item.binding.setItem(mDownloadsList.get(position));
        item.binding.setIsNarrow(mIsNarrowLayout);
    }

    @Override
    public int getItemCount() {
        return mDownloadsList == null ? 0 : mDownloadsList.size();
    }

    @Override
    public long getItemId(int position) {
        Download download = mDownloadsList.get(position);
        return  download.getId();
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

    static class DownloadItemViewHolder extends RecyclerView.ViewHolder {

        final DownloadItemBinding binding;

        DownloadItemViewHolder(@NonNull DownloadItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

}
