package com.igalia.wolvic.ui.adapters;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.databinding.SystemNotificationItemBinding;
import com.igalia.wolvic.ui.callbacks.SystemNotificationItemCallback;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SystemNotificationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<SystemNotification> mDisplayList = new ArrayList<>();

    @Nullable
    private final SystemNotificationItemCallback mNotificationItemCallback;

    public SystemNotificationAdapter(@Nullable SystemNotificationItemCallback clickCallback) {
        mNotificationItemCallback = clickCallback;

        setHasStableIds(true);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        SystemNotificationItemBinding binding = DataBindingUtil
                .inflate(LayoutInflater.from(parent.getContext()), R.layout.system_notification_item,
                        parent, false);

        binding.setCallback(mNotificationItemCallback);

        return new SystemNotificationItemViewHolder(binding);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        SystemNotification item = mDisplayList.get(position);

        SystemNotificationItemViewHolder notificationHolder = (SystemNotificationItemViewHolder) holder;
        notificationHolder.binding.setItem(item);
    }

    @Override
    public int getItemCount() {
        return mDisplayList.size();
    }

    @Override
    public long getItemId(int position) {
        return mDisplayList.get(position).hashCode();
    }

    public void setItems(Collection<SystemNotification> notifications) {
        mDisplayList.clear();
        mDisplayList.addAll(notifications);
        notifyDataSetChanged();
    }

    public void addItem(int index, SystemNotification notification) {
        mDisplayList.add(index, notification);
        notifyItemInserted(index);
    }

    static class SystemNotificationItemViewHolder extends RecyclerView.ViewHolder {

        final SystemNotificationItemBinding binding;

        SystemNotificationItemViewHolder(@NonNull SystemNotificationItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
