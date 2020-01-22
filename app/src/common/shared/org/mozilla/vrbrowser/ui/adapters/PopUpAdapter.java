package org.mozilla.vrbrowser.ui.adapters;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.databinding.PopupItemBinding;
import org.mozilla.vrbrowser.db.PopUpSite;
import org.mozilla.vrbrowser.ui.callbacks.PopUpSiteItemCallback;
import org.mozilla.vrbrowser.utils.SystemUtils;
import org.mozilla.vrbrowser.utils.ViewUtils;

import java.util.List;
import java.util.Objects;

public class PopUpAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    static final String LOGTAG = SystemUtils.createLogtag(BookmarkAdapter.class);

    private static final int ICON_ANIMATION_DURATION = 200;

    private List<PopUpSite> mDisplayList;

    private PopUpSiteItemCallback mCallback;

    private int mIconColorHover;
    private int mIconNormalColor;
    private int mIconSize;
    private int mMaxIconSize;

    public PopUpAdapter(Context aContext, PopUpSiteItemCallback callback) {
        mCallback = callback;

        mIconSize = (int)aContext.getResources().getDimension(R.dimen.language_row_icon_size);
        mMaxIconSize = mIconSize + ((mIconSize*25)/100);

        mIconColorHover = aContext.getResources().getColor(R.color.smoke, aContext.getTheme());
        mIconNormalColor = aContext.getResources().getColor(R.color.concrete, aContext.getTheme());

        setHasStableIds(true);
    }

    public void setSites(@NonNull List<PopUpSite> sites) {
        if (mDisplayList == null) {
            mDisplayList = sites;
            notifyItemRangeChanged(0, sites.size());

        } else {
            notifyDiff(sites);
        }
    }

    private void notifyDiff(List<PopUpSite> newDisplayList) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return mDisplayList.size();
            }

            @Override
            public int getNewListSize() {
                return newDisplayList.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return mDisplayList.get(oldItemPosition).url.equals(newDisplayList.get(newItemPosition).url);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return Objects.equals(newDisplayList.get(newItemPosition).url, mDisplayList.get(oldItemPosition).url);
            }
        });

        mDisplayList = newDisplayList;
        result.dispatchUpdatesTo(this);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        PopupItemBinding binding = DataBindingUtil
                .inflate(LayoutInflater.from(parent.getContext()), R.layout.popup_item,
                        parent, false);
        binding.setCallback(mCallback);
        binding.layout.setOnHoverListener((v, event) -> {
            int ev = event.getActionMasked();
            switch (ev) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    binding.delete.setHovered(true);
                    return false;

                case MotionEvent.ACTION_HOVER_EXIT:
                    if (!ViewUtils.isInsideView(binding.delete, (int)event.getRawX(), (int)event.getRawY())) {
                        binding.delete.setHovered(false);
                    }
                    return false;
            }

            return false;
        });
        binding.delete.setOnHoverListener(mIconHoverListener);

        return new PopUpSiteViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        PopUpSiteViewHolder siteHolder = (PopUpSiteViewHolder) holder;
        PopUpSite site = mDisplayList.get(position);
        siteHolder.binding.setItem(site);
    }

    @Override
    public int getItemCount() {
        return mDisplayList == null ? 0 : mDisplayList.size();
    }

    static class PopUpSiteViewHolder extends RecyclerView.ViewHolder {

        final PopupItemBinding binding;

        PopUpSiteViewHolder(@NonNull PopupItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private View.OnHoverListener mIconHoverListener = (view, motionEvent) -> {
        ImageView icon = (ImageView)view;
        int ev = motionEvent.getActionMasked();
        switch (ev) {
            case MotionEvent.ACTION_HOVER_ENTER: {
                icon.setColorFilter(mIconColorHover);
                ValueAnimator anim = ValueAnimator.ofInt(mIconSize, mMaxIconSize);
                anim.addUpdateListener(valueAnimator -> {
                    int val = (Integer) valueAnimator.getAnimatedValue();
                    ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                    layoutParams.width = val;
                    layoutParams.height = val;
                    view.setLayoutParams(layoutParams);
                });
                anim.setDuration(ICON_ANIMATION_DURATION);
                anim.start();

                return false;
            }

            case MotionEvent.ACTION_HOVER_EXIT: {
                ValueAnimator anim = ValueAnimator.ofInt(mMaxIconSize, mIconSize);
                anim.addUpdateListener(valueAnimator -> {
                    int val = (Integer) valueAnimator.getAnimatedValue();
                    ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                    layoutParams.width = val;
                    layoutParams.height = val;
                    view.setLayoutParams(layoutParams);
                });
                anim.setDuration(ICON_ANIMATION_DURATION);
                anim.start();
                icon.setColorFilter(mIconNormalColor);

                return false;
            }
        }

        return false;
    };

}

