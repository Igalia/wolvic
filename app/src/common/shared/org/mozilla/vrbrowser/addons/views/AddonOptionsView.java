package org.mozilla.vrbrowser.addons.views;

import android.content.Context;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserActivity;
import org.mozilla.vrbrowser.addons.adapters.AddonsViewAdapter;
import org.mozilla.vrbrowser.addons.delegates.AddonOptionsViewDelegate;
import org.mozilla.vrbrowser.addons.delegates.AddonsDelegate;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.databinding.AddonOptionsBinding;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.prompts.PromptData;
import org.mozilla.vrbrowser.utils.SystemUtils;

import mozilla.components.concept.engine.webextension.EnableSource;
import mozilla.components.feature.addons.Addon;
import mozilla.components.feature.addons.ui.ExtensionsKt;

public class AddonOptionsView extends RecyclerView.ViewHolder implements AddonOptionsViewDelegate {

    private static final String LOGTAG = SystemUtils.createLogtag(AddonOptionsView.class);

    private Context mContext;
    private AddonOptionsBinding mBinding;
    private WidgetManagerDelegate mWidgetManager;
    private AddonsDelegate mDelegate;

    public AddonOptionsView(@NonNull Context context, @NonNull AddonOptionsBinding binding, @NonNull AddonsDelegate delegate) {
        super(binding.getRoot());

        mContext = context;
        mBinding = binding;
        mDelegate = delegate;
        mWidgetManager = ((VRBrowserActivity)context);

        mBinding.setLifecycleOwner((VRBrowserActivity) mContext);
        mBinding.setDelegate(this);
    }

    public void bind(Addon addon) {
        mBinding.setAddon(addon);

        // Update addon bindings
        if (addon != null) {
            // Addon enabled
            mBinding.addonEnabled.setValue(addon.isEnabled(), false);
            mBinding.addonEnabled.setOnCheckedChangeListener((compoundButton, b, apply) -> setAddonEnabled(addon, b));

            // Addon private mode
            mBinding.addonPrivateMode.setValue(addon.isAllowedInPrivateBrowsing(), false);
            mBinding.addonPrivateMode.setOnCheckedChangeListener((compoundButton, b, apply) -> mWidgetManager.getServicesProvider().getAddons().getAddonManager().setAddonAllowedInPrivateBrowsing(addon, b, addon12 -> null, throwable ->  {
                Log.d(LOGTAG, throwable.getMessage() != null ? throwable.getMessage() : "");
                return null;
            }));

            setAddonEnabled(addon, addon.isEnabled());
        }

        mBinding.executePendingBindings();
    }

    private void setAddonEnabled(@NonNull Addon addon, boolean isEnabled) {
        if (isEnabled) {
            mWidgetManager.getServicesProvider().getAddons().getAddonManager().enableAddon(addon, EnableSource.USER, addon1 -> {
                mBinding.setAddon(addon1);
                mBinding.addonPrivateMode.setVisibility(View.VISIBLE);
                mBinding.addonSettings.setVisibility(View.VISIBLE);
                mBinding.addonSettings.setEnabled(addon1.getInstalledState() != null &&
                        addon1.getInstalledState().getOptionsPageUrl() != null);
                return null;

            }, throwable -> {
                Log.d(LOGTAG, throwable.getMessage() != null ? throwable.getMessage() : "");
                return null;
            });

        } else {
            mWidgetManager.getServicesProvider().getAddons().getAddonManager().disableAddon(addon, EnableSource.USER, addon1 -> {
                mBinding.setAddon(addon1);
                mBinding.addonPrivateMode.setVisibility(View.GONE);
                mBinding.addonSettings.setVisibility(View.GONE);
                mBinding.addonSettings.setEnabled(false);
                return null;

            }, throwable -> {
                Log.d(LOGTAG, throwable.getMessage() != null ? throwable.getMessage() : "");
                return null;
            });
        }
    }

    @Override
    public void onRemoveAddonButtonClicked(@NonNull View view, @NonNull Addon addon) {
        mWidgetManager.getServicesProvider().getAddons().getAddonManager().uninstallAddon(addon, () -> {
            showRemoveAddonSuccessDialog(addon);
            return null;

        }, (s, throwable) -> {
            Log.d(LOGTAG, s);
            showRemoveAddonErrorDialog(addon);
            return null;
        });
    }

    private void showRemoveAddonSuccessDialog(@NonNull Addon addon) {
        mDelegate.showAddonsList();
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
        mDelegate.showAddonsList();
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
}
