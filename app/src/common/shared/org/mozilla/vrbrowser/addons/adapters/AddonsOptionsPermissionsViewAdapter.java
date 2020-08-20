package org.mozilla.vrbrowser.addons.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.databinding.AddonOptionsPermissionsItemBinding;

import java.util.ArrayList;
import java.util.List;

public class AddonsOptionsPermissionsViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<String> mPermissionsList;

    public AddonsOptionsPermissionsViewAdapter() {
        mPermissionsList = new ArrayList<>();
        setHasStableIds(true);
    }

    public void setPermissionsList(final List<String> permissionsList) {
        mPermissionsList = permissionsList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        AddonOptionsPermissionsItemBinding binding = DataBindingUtil
                .inflate(LayoutInflater.from(parent.getContext()), R.layout.addon_options_permissions_item,
                        parent, false);

        return new PermissionItemViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        String permission = mPermissionsList.get(position);
        ((PermissionItemViewHolder)holder).binding.setTitle(permission);
    }

    @Override
    public int getItemCount() {
        return mPermissionsList == null ? 0 : mPermissionsList.size();
    }

    static class PermissionItemViewHolder extends RecyclerView.ViewHolder {

        final AddonOptionsPermissionsItemBinding binding;

        PermissionItemViewHolder(@NonNull AddonOptionsPermissionsItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
