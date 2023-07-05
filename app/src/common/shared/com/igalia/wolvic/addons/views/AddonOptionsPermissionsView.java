package com.igalia.wolvic.addons.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.VRBrowserApplication;
import com.igalia.wolvic.addons.adapters.AddonsOptionsPermissionsViewAdapter;
import com.igalia.wolvic.addons.delegates.AddonsDelegate;
import com.igalia.wolvic.browser.Addons;
import com.igalia.wolvic.databinding.AddonOptionsPermissionsBinding;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.utils.SystemUtils;

import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import mozilla.components.feature.addons.Addon;

public class AddonOptionsPermissionsView extends RecyclerView.ViewHolder implements Addons.AddonsListener {

    private static final String LOGTAG = SystemUtils.createLogtag(AddonOptionsPermissionsView.class);

    private Context mContext;
    private AddonOptionsPermissionsBinding mBinding;
    private AddonsOptionsPermissionsViewAdapter mAdapter;
    private WidgetManagerDelegate mWidgetManager;
    private AddonsDelegate mDelegate;
    private Executor mUIThreadExecutor;

    @SuppressLint("ClickableViewAccessibility")
    public AddonOptionsPermissionsView(@NonNull Context context, @NonNull AddonOptionsPermissionsBinding binding, @NonNull AddonsDelegate delegate) {
        super(binding.getRoot());

        mContext = context;
        mBinding = binding;
        mDelegate = delegate;
        mWidgetManager = ((VRBrowserActivity)context);
        mUIThreadExecutor = ((VRBrowserApplication)context.getApplicationContext()).getExecutors().mainThread();

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
        // Drawing Cache is deprecated in API level 28: https://developer.android.com/reference/android/view/View#getDrawingCache()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            mBinding.permissionsList.setDrawingCacheEnabled(true);
            mBinding.permissionsList.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
        }
        mBinding.learnMoreLink.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            mWidgetManager.openNewTabForeground(mContext.getString(R.string.sumo_addons_permissions));
        });

        mBinding.scrollview.setOnTouchListener((v, event) -> {
            v.requestFocusFromTouch();
            return false;
        });
    }

    public void bind(Addon addon) {
        mBinding.setAddon(addon);

        mWidgetManager.getServicesProvider().getAddons().addListener(this);

        // Update permissions list
        if (addon != null) {
            mAdapter.setPermissionsList(addon.translatePermissions(mContext).stream()
                    .sorted()
                    .collect(Collectors.toList()));
        }

        mBinding.executePendingBindings();
    }

    public void unbind() {
        mWidgetManager.getServicesProvider().getAddons().removeListener(this);
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

    @Override
    public void onAddonsUpdated() {
        mWidgetManager.getServicesProvider().getAddons().getAddons(true).thenAcceptAsync(addons -> {
            Addon addon = addons.stream()
                    .filter(item -> item.getId().equals(mBinding.getAddon().getId()))
                    .findFirst().orElse(null);

            if (addon == null || !addon.isInstalled()) {
                mDelegate.showAddonsList();

            } else {
                bind(addon);
            }

        }, mUIThreadExecutor).exceptionally(throwable -> {
            Log.d(LOGTAG, String.valueOf(throwable.getMessage()));
            return null;
        });
    }
}
