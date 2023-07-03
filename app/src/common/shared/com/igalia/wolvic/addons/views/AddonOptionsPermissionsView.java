package com.igalia.wolvic.addons.views;

import android.annotation.SuppressLint;
import android.content.Context;
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
        // TODO: This method was deprecated in API level 28.
        //  The view drawing cache was largely made obsolete with the introduction of
        //  hardware-accelerated rendering in API 11. With hardware-acceleration, intermediate
        //  cache layers are largely unnecessary and can easily result in a net loss in performance
        //  due to the cost of creating and updating the layer. In the rare cases where caching
        //  layers are useful, such as for alpha animations,
        //  setLayerType(int, android.graphics.Paint) handles this with hardware rendering.
        //  For software-rendered snapshots of a small part of the View hierarchy or individual
        //  Views it is recommended to create a Canvas from either a Bitmap or Picture and call
        //  draw(android.graphics.Canvas) on the View. However these software-rendered usages are
        //  discouraged and have compatibility issues with hardware-only rendering features such
        //  as Config.HARDWARE bitmaps, real-time shadows, and outline clipping. For screenshots of
        //  the UI for feedback reports or unit testing the PixelCopy API is recommended.
        mBinding.permissionsList.setDrawingCacheEnabled(true);
        mBinding.permissionsList.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
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
