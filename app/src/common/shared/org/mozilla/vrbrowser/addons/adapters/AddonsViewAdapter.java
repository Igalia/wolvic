package org.mozilla.vrbrowser.addons.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.addons.delegates.AddonsDelegate;
import org.mozilla.vrbrowser.addons.views.AddonOptionsDetailsView;
import org.mozilla.vrbrowser.addons.views.AddonOptionsPermissionsView;
import org.mozilla.vrbrowser.addons.views.AddonOptionsView;
import org.mozilla.vrbrowser.addons.views.AddonsListView;
import org.mozilla.vrbrowser.databinding.AddonOptionsBinding;
import org.mozilla.vrbrowser.databinding.AddonOptionsDetailsBinding;
import org.mozilla.vrbrowser.databinding.AddonOptionsPermissionsBinding;
import org.mozilla.vrbrowser.databinding.AddonsListBinding;
import org.mozilla.vrbrowser.ui.delegates.LibraryNavigationDelegate;

import mozilla.components.feature.addons.Addon;

public class AddonsViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements LibraryNavigationDelegate {

    private static final int LEVELS = 3;

    @IntDef(value = { ADDONS_LEVEL_0, ADDONS_LEVEL_1, ADDONS_LEVEL_2 })
    public @interface AddonsLevels {}
    public static final int ADDONS_LEVEL_0 = 0;
    public static final int ADDONS_LEVEL_1 = 1;
    public static final int ADDONS_LEVEL_2 = 2;

    @IntDef(value = { ADDONS_LIST, ADDON_OPTIONS, ADDON_OPTIONS_PERMISSIONS, ADDON_OPTIONS_DETAILS })
    public @interface AddonsViews {}
    public static final int ADDONS_LIST = 0;
    public static final int ADDON_OPTIONS = 1;
    public static final int ADDON_OPTIONS_PERMISSIONS = 2;
    public static final int ADDON_OPTIONS_DETAILS = 3;

    private AddonsDelegate mDelegate;
    private Addon mCurrentAddon;
    private @AddonsViews int mCurrentItem;

    public AddonsViewAdapter(@NonNull AddonsDelegate delegate) {
        mDelegate = delegate;

        setHasStableIds(true);
    }

    public void setCurrentAddon(Addon addon) {
        mCurrentAddon = addon;
    }

    public Addon getCurrentAddon() {
        return mCurrentAddon;
    }

    public void setCurrentItem(@AddonsViews int item) {
        mCurrentItem = item;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            case ADDONS_LIST: {
                AddonsListBinding binding = DataBindingUtil
                        .inflate(LayoutInflater.from(parent.getContext()), R.layout.addons_list,
                                parent, false);
                return new AddonsListView(parent.getContext(), binding, mDelegate);
            }

            case ADDON_OPTIONS: {
                AddonOptionsBinding binding = DataBindingUtil
                        .inflate(LayoutInflater.from(parent.getContext()), R.layout.addon_options,
                                parent, false);
                return new AddonOptionsView(parent.getContext(), binding, mDelegate);
            }

            case ADDON_OPTIONS_DETAILS: {
                AddonOptionsDetailsBinding binding = DataBindingUtil
                        .inflate(LayoutInflater.from(parent.getContext()), R.layout.addon_options_details,
                                parent, false);
                return new AddonOptionsDetailsView(parent.getContext(), binding, mDelegate);
            }

            case ADDON_OPTIONS_PERMISSIONS: {
                AddonOptionsPermissionsBinding binding = DataBindingUtil
                        .inflate(LayoutInflater.from(parent.getContext()), R.layout.addon_options_permissions,
                                parent, false);
                return new AddonOptionsPermissionsView(parent.getContext(), binding, mDelegate);
            }
        }

        throw new RuntimeException("there is no type that matches the type " + viewType + " + make sure your using types correctly");
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);

        if (holder instanceof AddonsListView) {
            ((AddonsListView)holder).unbind();

        } else if (holder instanceof AddonOptionsView) {
            ((AddonOptionsView)holder).unbind();

        } else if (holder instanceof AddonOptionsDetailsView) {
            ((AddonOptionsDetailsView)holder).unbind();

        } else if (holder instanceof AddonOptionsPermissionsView) {
            ((AddonOptionsPermissionsView)holder).unbind();

        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (position == ADDONS_LEVEL_0) {
            ((AddonsListView) holder).bind();

        } else if (position == ADDONS_LEVEL_1) {
            if (mCurrentItem == ADDON_OPTIONS) {
                ((AddonOptionsView) holder).bind(mCurrentAddon);

            } else if (mCurrentItem == ADDON_OPTIONS_DETAILS) {
                ((AddonOptionsDetailsView) holder).bind(mCurrentAddon);
            }

        } else if (position == ADDONS_LEVEL_2) {
            if (mCurrentItem == ADDON_OPTIONS_DETAILS) {
                ((AddonOptionsDetailsView) holder).bind(mCurrentAddon);

            } else if (mCurrentItem == ADDON_OPTIONS_PERMISSIONS) {
                ((AddonOptionsPermissionsView) holder).bind(mCurrentAddon);
            }
        }
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
    }

    @Override
    public int getItemCount() {
        return LEVELS;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == ADDONS_LEVEL_1 || position == ADDONS_LEVEL_2) {
            return mCurrentItem;
        }

        return position;
    }

    @Override
    public long getItemId(int position) {
        return super.getItemId(position);
    }

}
