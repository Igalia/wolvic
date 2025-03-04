package com.igalia.wolvic.ui.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.WebAppItemBinding;
import com.igalia.wolvic.ui.callbacks.WebAppItemCallback;
import com.igalia.wolvic.utils.SystemUtils;

import java.util.List;
import java.util.Objects;

import mozilla.components.browser.icons.IconRequest;

public class WebAppsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    static final String LOGTAG = SystemUtils.createLogtag(WebAppsAdapter.class);

    private List<WebApp> mWebAppsList;
    private boolean mIsNarrowLayout;

    @Nullable
    private final WebAppItemCallback mWebAppItemCallback;

    public WebAppsAdapter(@Nullable WebAppItemCallback clickCallback, Context aContext) {
        mWebAppItemCallback = clickCallback;

        setHasStableIds(true);
    }

    public void setWebAppsList(final List<WebApp> webAppList) {
        if (mWebAppsList == null || mWebAppsList.isEmpty()) {
            mWebAppsList = webAppList;
            notifyItemRangeInserted(0, webAppList.size());

        } else {
            DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return mWebAppsList.size();
                }

                @Override
                public int getNewListSize() {
                    return webAppList.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    WebApp newItem = webAppList.get(newItemPosition);
                    WebApp oldItem = mWebAppsList.get(oldItemPosition);
                    return Objects.equals(newItem.getId(), oldItem.getId());
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    WebApp newItem = webAppList.get(newItemPosition);
                    WebApp oldItem = mWebAppsList.get(oldItemPosition);
                    return Objects.equals(newItem, oldItem);
                }
            });

            mWebAppsList = webAppList;
            result.dispatchUpdatesTo(this);
        }
    }

    public void removeItem(WebApp webAppItem) {
        int position = mWebAppsList.indexOf(webAppItem);
        if (position >= 0) {
            mWebAppsList.remove(position);
            notifyItemRemoved(position);
        }
    }

    public int itemCount() {
        if (mWebAppsList != null) {
            return mWebAppsList.size();
        }

        return 0;
    }

    public int getItemPosition(long id) {
        for (int position = 0; position < mWebAppsList.size(); position++)
            if (mWebAppsList.get(position).hashCode() == id)
                return position;
        return 0;
    }

    public void setNarrow(boolean isNarrow) {
        if (mIsNarrowLayout != isNarrow) {
            mIsNarrowLayout = isNarrow;
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        WebAppItemBinding binding = DataBindingUtil
                .inflate(LayoutInflater.from(parent.getContext()), R.layout.web_app_item,
                        parent, false);
        binding.setCallback(mWebAppItemCallback);
        binding.setIsHovered(false);
        binding.setIsNarrow(mIsNarrowLayout);

        return new WebAppItemViewHolder(binding);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        WebAppItemViewHolder webAppHolder = (WebAppItemViewHolder) holder;
        WebAppItemBinding binding = webAppHolder.binding;

        WebApp item = mWebAppsList.get(position);
        binding.setItem(item);
        binding.setIsNarrow(mIsNarrowLayout);

        SessionStore.get().getBrowserIcons().loadIntoView(binding.webAppIcon,
                item.getStartUrl(), item.getIconResources(), IconRequest.Size.LAUNCHER);

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
                    binding.setIsHovered(true);
                    view.postInvalidate();
                    return false;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return mWebAppsList == null ? 0 : mWebAppsList.size();
    }

    @Override
    public long getItemId(int position) {
        WebApp webApp = mWebAppsList.get(position);
        return webApp.hashCode();
    }

    static class WebAppItemViewHolder extends RecyclerView.ViewHolder {

        final WebAppItemBinding binding;

        WebAppItemViewHolder(@NonNull WebAppItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

}
