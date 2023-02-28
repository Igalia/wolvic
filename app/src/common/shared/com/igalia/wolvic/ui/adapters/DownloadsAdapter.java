package com.igalia.wolvic.ui.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
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

import com.igalia.wolvic.R;
import com.igalia.wolvic.databinding.DownloadItemBinding;
import com.igalia.wolvic.downloads.Download;
import com.igalia.wolvic.ui.callbacks.DownloadItemCallback;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.utils.AnimationHelper;
import com.igalia.wolvic.utils.SystemUtils;

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
                    return mDownloadsList.get(oldItemPosition).getId() == downloadsList.get(newItemPosition).getId();
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    Download newDownloadItem = downloadsList.get(newItemPosition);
                    Download oldDownloadItem = mDownloadsList.get(oldItemPosition);
                    return newDownloadItem.getProgress() == oldDownloadItem.getProgress()
                            && newDownloadItem.getStatus() == oldDownloadItem.getStatus()
                            && newDownloadItem.getFilename().equals(oldDownloadItem.getFilename());
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

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        DownloadItemBinding binding = DataBindingUtil
                .inflate(LayoutInflater.from(parent.getContext()), R.layout.download_item,
                        parent, false);
        binding.setCallback(mDownloadItemCallback);
        binding.setIsHovered(false);
        binding.setIsNarrow(mIsNarrowLayout);

        return new DownloadItemViewHolder(binding);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        DownloadItemViewHolder item = (DownloadItemViewHolder) holder;
        DownloadItemBinding binding = item.binding;
        Download downloadItem = mDownloadsList.get(position);
        item.binding.setItem(downloadItem);
        item.binding.setIsNarrow(mIsNarrowLayout);

        switch (downloadItem.getStatus()) {
            case Download.PENDING:
                binding.thumbnail.setImageResource(R.drawable.ic_pending_circle);
                break;
            case Download.RUNNING:
                binding.thumbnail.setImageResource(R.drawable.ic_downloading_circle);
                break;
            case Download.PAUSED:
                binding.thumbnail.setImageResource(R.drawable.ic_pause_circle);
                break;
            case Download.FAILED:
            case Download.UNAVAILABLE:
                binding.thumbnail.setImageResource(R.drawable.ic_error_circle);
                break;
            case Download.SUCCESSFUL: {
                Uri fileUri = downloadItem.getOutputFileUri();
                if (fileUri == null) {
                    // If this ever happens, we mark the item as unavailable.
                    binding.thumbnail.setImageResource(R.drawable.ic_error_circle);
                } else {
                    ThumbnailAsyncTask task = new ThumbnailAsyncTask(binding.layout.getContext(), fileUri,
                            bitmap -> {
                                if (bitmap == null || binding.getItem() == null || !Objects.equals(fileUri, binding.getItem().getOutputFileUri()))
                                    binding.thumbnail.setImageResource(R.drawable.ic_generic_file);
                                else
                                    binding.thumbnail.setImageBitmap(bitmap);
                            }
                    );
                    task.execute();
                }
                break;
            }
        }
        binding.layout.setOnHoverListener((view, motionEvent) -> {
            int ev = motionEvent.getActionMasked();
            switch (ev) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    binding.setIsHovered(true);
                    view.getBackground().setState(new int[]{android.R.attr.state_hovered});
                    view.postInvalidate();
                    return false;

                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_HOVER_EXIT:
                    view.getBackground().setState(new int[]{android.R.attr.state_active});
                    binding.setIsHovered(false);
                    view.postInvalidate();
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
