package org.mozilla.vrbrowser.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewGroup;

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

    private List<PopUpSite> mDisplayList;

    private PopUpSiteItemCallback mCallback;

    public PopUpAdapter(Context aContext, PopUpSiteItemCallback callback) {
        mCallback = callback;

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
        binding.site.setOnCheckedChangeListener((compoundButton, value, apply) -> {
            if (mCallback != null) {
                mCallback.onSwitch(binding.getItem(), value);
            }
        });

        return new PopUpSiteViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        PopUpSiteViewHolder siteHolder = (PopUpSiteViewHolder) holder;
        PopUpSite site = mDisplayList.get(position);
        siteHolder.binding.setItem(site);
        siteHolder.binding.site.setChecked(site.allowed);
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

}

