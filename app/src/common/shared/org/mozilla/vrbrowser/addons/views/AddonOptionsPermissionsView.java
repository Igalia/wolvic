package org.mozilla.vrbrowser.addons.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserActivity;
import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.addons.adapters.AddonsOptionsPermissionsViewAdapter;
import org.mozilla.vrbrowser.addons.delegates.AddonsDelegate;
import org.mozilla.vrbrowser.browser.Addons;
import org.mozilla.vrbrowser.databinding.AddonOptionsPermissionsBinding;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.utils.SystemUtils;

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
