package org.mozilla.vrbrowser.addons.views;

import android.content.Context;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserActivity;
import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.addons.adapters.AddonsViewAdapter;
import org.mozilla.vrbrowser.addons.delegates.AddonOptionsViewDelegate;
import org.mozilla.vrbrowser.addons.delegates.AddonsDelegate;
import org.mozilla.vrbrowser.browser.Addons;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.databinding.AddonOptionsBinding;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.prompts.PromptData;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.util.concurrent.Executor;

import mozilla.components.concept.engine.webextension.EnableSource;
import mozilla.components.feature.addons.Addon;
import mozilla.components.feature.addons.ui.ExtensionsKt;

public class AddonOptionsView extends RecyclerView.ViewHolder implements AddonOptionsViewDelegate, Addons.AddonsListener {

    private static final String LOGTAG = SystemUtils.createLogtag(AddonOptionsView.class);

    private Context mContext;
    private AddonOptionsBinding mBinding;
    private WidgetManagerDelegate mWidgetManager;
    private AddonsDelegate mDelegate;
    private Executor mUIThreadExecutor;

    public AddonOptionsView(@NonNull Context context, @NonNull AddonOptionsBinding binding, @NonNull AddonsDelegate delegate) {
        super(binding.getRoot());

        mContext = context;
        mBinding = binding;
        mDelegate = delegate;
        mWidgetManager = ((VRBrowserActivity)context);
        mUIThreadExecutor = ((VRBrowserApplication)context.getApplicationContext()).getExecutors().mainThread();

        mBinding.setLifecycleOwner((VRBrowserActivity) mContext);
        mBinding.setDelegate(this);
    }

    public void bind(Addon addon) {
        mBinding.setAddon(addon);

        mWidgetManager.getServicesProvider().getAddons().addListener(this);

        // Update addon bindings
        if (addon != null) {
            // Addon enabled
            mBinding.addonEnabled.setValue(addon.isEnabled(), false);
            mBinding.addonEnabled.setOnCheckedChangeListener((compoundButton, b, apply) -> setAddonEnabled(addon, b));

            // Addon private mode
            mBinding.addonPrivateMode.setValue(addon.isAllowedInPrivateBrowsing(), false);
            mBinding.addonPrivateMode.setOnCheckedChangeListener((compoundButton, b, apply) -> mWidgetManager.getServicesProvider().getAddons().setAddonAllowedInPrivateBrowsing(addon, b, addon1 -> null, throwable ->  {
                Log.d(LOGTAG, String.valueOf(throwable.getMessage()));
                return null;
            }));
        }

        mBinding.executePendingBindings();
    }

    public void unbind() {
        mWidgetManager.getServicesProvider().getAddons().removeListener(this);
    }

    private void setAddonEnabled(@NonNull Addon addon, boolean isEnabled) {
        if (isEnabled) {
            mWidgetManager.getServicesProvider().getAddons().enableAddon(addon, EnableSource.USER, addon1 -> null, throwable -> {
                Log.d(LOGTAG, String.valueOf(throwable.getMessage()));
                return null;
            });

        } else {
            mWidgetManager.getServicesProvider().getAddons().disableAddon(addon, EnableSource.USER, addon1 -> null, throwable -> {
                Log.d(LOGTAG, String.valueOf(throwable.getMessage()));
                return null;
            });
        }
    }

    @Override
    public void onRemoveAddonButtonClicked(@NonNull View view, @NonNull Addon addon) {
        mWidgetManager.getServicesProvider().getAddons().uninstallAddon(addon, () -> {
            showRemoveAddonSuccessDialog(addon);
            return null;

        }, (s, throwable) -> {
            Log.d(LOGTAG, s);
            showRemoveAddonErrorDialog(addon);
            return null;
        });
    }

    private void showRemoveAddonSuccessDialog(@NonNull Addon addon) {
        PromptData data = new PromptData.Builder()
                .withIconRes(R.drawable.ic_icon_addons)
                .withTitle(mContext.getString(
                        R.string.addons_remove_success_dialog_title,
                        ExtensionsKt.getTranslatedName(addon)))
                .withBtnMsg(new String[]{
                        mContext.getString(R.string.addons_remove_success_dialog_ok)
                })
                .build();
        mWidgetManager.getFocusedWindow().showConfirmPrompt(data);
    }

    private void showRemoveAddonErrorDialog(@NonNull Addon addon) {
        PromptData data = new PromptData.Builder()
                .withIconRes(R.drawable.ic_icon_addons)
                .withTitle(mContext.getString(
                        R.string.addons_remove_error_dialog_title,
                        ExtensionsKt.getTranslatedName(addon)))
                .withBtnMsg(new String[]{
                        mContext.getString(R.string.addons_remove_error_dialog_ok)
                })
                .build();
        mWidgetManager.getFocusedWindow().showConfirmPrompt(data);
    }

    @Override
    public void onAddonDetailsButtonClicked(@NonNull View view, @NonNull Addon addon) {
        mDelegate.showAddonOptionsDetails(addon, AddonsViewAdapter.ADDONS_LEVEL_2);
    }

    @Override
    public void onAddonSettingsButtonClicked(@NonNull View view, @NonNull Addon addon) {
        if (addon.getInstalledState() != null) {
            boolean openInTab = addon.getInstalledState().getOpenOptionsPageInTab();
            String settingsUrl = addon.getInstalledState().getOptionsPageUrl();
            if (settingsUrl != null) {
                if (openInTab) {
                    mWidgetManager.openNewTabForeground(settingsUrl);

                } else {
                    Session session = mWidgetManager.getFocusedWindow().getSession();
                    if (session != null) {
                        session.loadUri(settingsUrl);
                    }
                }
            }
        }
    }

    @Override
    public void onAddonPermissionsButtonClicked(@NonNull View view, @NonNull Addon addon) {
        mDelegate.showAddonOptionsPermissions(addon);
    }

    @Override
    public void onAddonsUpdated() {
        mWidgetManager.getServicesProvider().getAddons().getAddons(true).thenAcceptAsync(addons -> {
            Addon addon = addons.stream()
                    .filter(item -> item.getId().equals(mBinding.getAddon().getId()))
                    .findFirst().orElse(null);

            if (addon != null && addon.isInstalled()) {
                bind(addon);

            } else {
                mDelegate.showAddonsList();
            }

        }, mUIThreadExecutor).exceptionally(throwable -> {
            Log.d(LOGTAG, String.valueOf(throwable.getMessage()));
            return null;
        });
    }
}
