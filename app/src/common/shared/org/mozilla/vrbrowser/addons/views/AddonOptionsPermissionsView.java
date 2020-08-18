package org.mozilla.vrbrowser.addons.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserActivity;
import org.mozilla.vrbrowser.addons.adapters.AddonsOptionsPermissionsViewAdapter;
import org.mozilla.vrbrowser.databinding.AddonOptionsPermissionsBinding;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.util.stream.Collectors;

import mozilla.components.feature.addons.Addon;

public class AddonOptionsPermissionsView extends RecyclerView.ViewHolder {

    private static final String LOGTAG = SystemUtils.createLogtag(AddonOptionsPermissionsView.class);

    private Context mContext;
    private AddonOptionsPermissionsBinding mBinding;
    private AddonsOptionsPermissionsViewAdapter mAdapter;
    private WidgetManagerDelegate mWidgetManager;

    @SuppressLint("ClickableViewAccessibility")
    public AddonOptionsPermissionsView(@NonNull Context context, @NonNull AddonOptionsPermissionsBinding binding) {
        super(binding.getRoot());

        mContext = context;
        mBinding = binding;
        mWidgetManager = ((VRBrowserActivity)context);

        mBinding.setLifecycleOwner((VRBrowserActivity) mContext);
        mAdapter = new AddonsOptionsPermissionsViewAdapter();
        mBinding.permissionsList.setAdapter(mAdapter);
        mBinding.permissionsList.setOnTouchListener((v, event) -> {
            v.requestFocusFromTouch();
            return false;
        });
        mBinding.permissionsList.addOnScrollListener(mScrollListener);
        mBinding.permissionsList.setHasFixedSize(true);
        mBinding.permissionsList.setItemViewCacheSize(20);
        mBinding.permissionsList.setDrawingCacheEnabled(true);
        mBinding.permissionsList.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
    }

    public void bind(Addon addon) {
        mBinding.setAddon(addon);

        // Update permissions list
        if (addon != null) {
            // If the addon is not installed we set the homepage link
            mBinding.learnMoreLink.setOnClickListener(view -> mWidgetManager.openNewTabForeground(mContext.getString(R.string.sumo_addons_permissions)));

            mAdapter.setPermissionsList(addon.translatePermissions().stream()
                    .map(integer -> {
                        @StringRes int stringId = integer;
                        return mContext.getString(stringId);
                    })
                    .sorted()
                    .collect(Collectors.toList()));
        }

        mBinding.executePendingBindings();
    }

    protected RecyclerView.OnScrollListener mScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);

            if (recyclerView.getScrollState() != RecyclerView.SCROLL_STATE_SETTLING) {
                recyclerView.requestFocus();
            }
        }
    };
}
