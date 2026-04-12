package com.igalia.wolvic.ui.adapters;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.databinding.CustomSearchEngineItemBinding;
import com.igalia.wolvic.search.CustomSearchEngine;

import java.util.ArrayList;
import java.util.List;

public class CustomSearchEnginesAdapter extends RecyclerView.Adapter<CustomSearchEnginesAdapter.ViewHolder> {

    public interface Delegate {
        void onEngineSelected(View view, CustomSearchEngine engine);
        void onEngineDeleted(View view, CustomSearchEngine engine);
    }

    private List<CustomSearchEngine> mEngines = new ArrayList<>();
    private Delegate mDelegate;

    public void setEngines(List<CustomSearchEngine> engines) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return mEngines.size();
            }

            @Override
            public int getNewListSize() {
                return engines.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return mEngines.get(oldItemPosition).getId().equals(engines.get(newItemPosition).getId());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return mEngines.get(oldItemPosition).equals(engines.get(newItemPosition));
            }
        });

        mEngines.clear();
        mEngines.addAll(engines);
        diffResult.dispatchUpdatesTo(this);
    }

    public void setDelegate(Delegate delegate) {
        mDelegate = delegate;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        CustomSearchEngineItemBinding binding = DataBindingUtil.inflate(
                LayoutInflater.from(parent.getContext()),
                R.layout.custom_search_engine_item,
                parent,
                false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CustomSearchEngine engine = mEngines.get(position);
        holder.binding.setEngine(engine);
        holder.binding.setDelegate(mDelegate);
        holder.binding.setIsHovered(false);
        holder.binding.executePendingBindings();

        // Layout hover listener - shows/hides delete button
        holder.binding.layout.setOnHoverListener((view, motionEvent) -> {
            int ev = motionEvent.getActionMasked();
            switch (ev) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    holder.binding.setIsHovered(true);
                    view.getBackground().setState(new int[]{android.R.attr.state_hovered});
                    view.postInvalidate();
                    return false;

                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_HOVER_EXIT:
                    view.getBackground().setState(new int[]{android.R.attr.state_active});
                    holder.binding.setIsHovered(false);
                    view.postInvalidate();
                    return false;
            }
            return false;
        });

        // Trash icon hover and click
        holder.binding.trash.setOnHoverListener((view, motionEvent) -> {
            int ev = motionEvent.getActionMasked();
            switch (ev) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    holder.binding.setIsHovered(true);
                    return false;
                case MotionEvent.ACTION_HOVER_EXIT:
                    holder.binding.setIsHovered(false);
                    return false;
            }
            return false;
        });

        holder.binding.trash.setOnTouchListener((view, motionEvent) -> {
            holder.binding.setIsHovered(true);
            int ev = motionEvent.getActionMasked();
            switch (ev) {
                case MotionEvent.ACTION_UP:
                    if (mDelegate != null) {
                        mDelegate.onEngineDeleted(view, engine);
                    }
                    return true;
                case MotionEvent.ACTION_DOWN:
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    holder.binding.setIsHovered(false);
                    return false;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return mEngines.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CustomSearchEngineItemBinding binding;

        ViewHolder(CustomSearchEngineItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}