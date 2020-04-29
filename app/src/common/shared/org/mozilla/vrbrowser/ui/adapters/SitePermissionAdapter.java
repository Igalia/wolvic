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
import org.mozilla.vrbrowser.databinding.SitePermissionItemBinding;
import org.mozilla.vrbrowser.db.SitePermission;
import org.mozilla.vrbrowser.ui.callbacks.PermissionSiteItemCallback;
import org.mozilla.vrbrowser.utils.SystemUtils;
import org.mozilla.vrbrowser.utils.ViewUtils;

import java.util.List;
import java.util.Objects;

public class SitePermissionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    static final String LOGTAG = SystemUtils.createLogtag(SitePermissionAdapter.class);

    private static final int ICON_ANIMATION_DURATION = 200;

    private List<SitePermission> mDisplayList;

    private PermissionSiteItemCallback mCallback;

    private int mIconColorHover;
    private int mIconNormalColor;
    private int mIconSize;
    private int mMaxIconSize;

    public SitePermissionAdapter(Context aContext, PermissionSiteItemCallback callback) {
        mCallback = callback;

        mIconSize = (int)aContext.getResources().getDimension(R.dimen.language_row_icon_size);
        mMaxIconSize = mIconSize + ((mIconSize*25)/100);

        mIconColorHover = aContext.getResources().getColor(R.color.smoke, aContext.getTheme());
        mIconNormalColor = aContext.getResources().getColor(R.color.concrete, aContext.getTheme());

        setHasStableIds(true);
    }

    public void setSites(@NonNull List<SitePermission> sites) {
        if (mDisplayList == null) {
            mDisplayList = sites;
            notifyDataSetChanged();

        } else {
            notifyDiff(sites);
        }
    }

    public List<SitePermission> getSites() {
        return mDisplayList;
    }

    private void notifyDiff(List<SitePermission> newDisplayList) {
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
                return mDisplayList.get(oldItemPosition).id == newDisplayList.get(newItemPosition).id;
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
        SitePermissionItemBinding binding = DataBindingUtil
                .inflate(LayoutInflater.from(parent.getContext()), R.layout.site_permission_item,
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

        return new PermissionSiteViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        PermissionSiteViewHolder siteHolder = (PermissionSiteViewHolder) holder;
        SitePermission site = mDisplayList.get(position);
        siteHolder.binding.setItem(site);
    }

    @Override
    public long getItemId(int position) {
        return mDisplayList.get(position).id;
    }

    @Override
    public int getItemCount() {
        return mDisplayList == null ? 0 : mDisplayList.size();
    }

    static class PermissionSiteViewHolder extends RecyclerView.ViewHolder {

        final SitePermissionItemBinding binding;

        PermissionSiteViewHolder(@NonNull SitePermissionItemBinding binding) {
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

