package org.mozilla.vrbrowser.ui.adapters;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.databinding.ContextMenuItemBinding;
import org.mozilla.vrbrowser.ui.callbacks.ContextMenuClickCallback;

import java.util.List;

public class ContextMenuAdapter extends RecyclerView.Adapter<ContextMenuAdapter.ContextMenuViewHolder> {

    private List<? extends ContextMenuNode> mContextMenuList;

    public static class ContextMenuNode {
        int position;
        Drawable icon;
        String title;

        public ContextMenuNode(int position, Drawable icon, String title) {
            this.position = position;
            this.icon = icon;
            this.title = title;
        }

        public Integer getPosition() {
            return position;
        }

        public void setPosition(int position) {
            this.position = position;
        }

        public Drawable getIcon() {
            return icon;
        }

        public void setIcon(Drawable icon) {
            this.icon = icon;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }

    @Nullable
    private final ContextMenuClickCallback mContextMenuItemClickCallback;

    public ContextMenuAdapter(@Nullable ContextMenuClickCallback clickCallback) {
        mContextMenuItemClickCallback = clickCallback;

        setHasStableIds(true);
    }

    public void setContextMenuItemList(final List<? extends ContextMenuNode> contextMenuList) {
        mContextMenuList = contextMenuList;
    }

    @Override
    public ContextMenuViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ContextMenuItemBinding binding = DataBindingUtil
                .inflate(LayoutInflater.from(parent.getContext()), R.layout.context_menu_item,
                        parent, false);
        binding.setCallback(mContextMenuItemClickCallback);
        return new ContextMenuViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ContextMenuViewHolder holder, int position) {
        holder.binding.setMenuItem(mContextMenuList.get(position));
        holder.binding.executePendingBindings();
    }

    @Override
    public int getItemCount() {
        return mContextMenuList == null ? 0 : mContextMenuList.size();
    }

    @Override
    public long getItemId(int position) {
        ContextMenuNode menuItem = mContextMenuList.get(position);
        return  menuItem.getPosition() != null ? menuItem.getPosition() : RecyclerView.NO_ID;
    }

    static class ContextMenuViewHolder extends RecyclerView.ViewHolder {

        final ContextMenuItemBinding binding;

        ContextMenuViewHolder(@NonNull ContextMenuItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

}
