package org.mozilla.vrbrowser.addons.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserActivity;
import org.mozilla.vrbrowser.addons.adapters.AddonsManagerAdapter;
import org.mozilla.vrbrowser.addons.adapters.AddonsViewAdapter;
import org.mozilla.vrbrowser.addons.delegates.AddonsDelegate;
import org.mozilla.vrbrowser.databinding.AddonsListBinding;
import org.mozilla.vrbrowser.ui.viewmodel.LibraryViewModel;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.dialogs.PromptDialogWidget;
import org.mozilla.vrbrowser.ui.widgets.prompts.PromptData;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import mozilla.components.concept.engine.CancellableOperation;
import mozilla.components.feature.addons.Addon;
import mozilla.components.feature.addons.ui.AddonsManagerAdapterDelegate;
import mozilla.components.feature.addons.ui.ExtensionsKt;

public class AddonsListView extends RecyclerView.ViewHolder implements AddonsManagerAdapterDelegate {

    private static final String LOGTAG = SystemUtils.createLogtag(AddonsListView.class);

    private Context mContext;
    private LibraryViewModel mViewModel;
    private AddonsListBinding mBinding;
    private AddonsManagerAdapter mAdapter;
    private Executor mUIThreadExecutor;
    private AddonsDelegate mDelegate;
    private WidgetManagerDelegate mWidgetManager;

    @SuppressLint("ClickableViewAccessibility")
    public AddonsListView(@NonNull Context context, @NonNull AddonsListBinding binding, @NonNull AddonsDelegate delegate) {
        super(binding.getRoot());

        mContext = context;
        mWidgetManager = (VRBrowserActivity)mContext;
        mDelegate = delegate;
        mViewModel = new ViewModelProvider(
                (VRBrowserActivity) mContext,
                ViewModelProvider.AndroidViewModelFactory.getInstance(((VRBrowserActivity) mContext).getApplication()))
                .get(LibraryViewModel.class);
        mAdapter = new AddonsManagerAdapter(
                ((VRBrowserActivity) mContext).getServicesProvider().getAddons().getAddonCollectionProvider(),
                AddonsListView.this,
                Collections.emptyList(),
                createAddonStyle(mContext)
        );
        mBinding = binding;
        mUIThreadExecutor = ((VRBrowserActivity) mContext).getServicesProvider().getExecutors().mainThread();

        mBinding.setLifecycleOwner((VRBrowserActivity) mContext);
        mBinding.setViewModel(mViewModel);
        mBinding.addonsList.setAdapter(mAdapter);
        mBinding.addonsList.setOnTouchListener((v, event) -> {
            v.requestFocusFromTouch();
            return false;
        });
        mBinding.addonsList.addOnScrollListener(mScrollListener);
        mBinding.addonsList.setHasFixedSize(true);
        mBinding.addonsList.setItemViewCacheSize(20);
        mBinding.addonsList.setDrawingCacheEnabled(true);
        mBinding.addonsList.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

        mViewModel.setIsLoading(true);
    }

    public void bind() {
        ((VRBrowserActivity) mContext).getServicesProvider().getAddons().getAddons(true).thenApplyAsync(addons -> {
            if (addons == null || addons.size() == 0) {
                mViewModel.setIsEmpty(true);
                mViewModel.setIsLoading(false);

            } else {
                mViewModel.setIsEmpty(false);
                mViewModel.setIsLoading(false);
                mAdapter.updateAddons(addons);
                mBinding.getRoot().post(() -> mBinding.addonsList.scrollToPosition(0));
            }

            mBinding.executePendingBindings();
            return null;
            
        }, mUIThreadExecutor).exceptionally(throwable -> {
            Log.d(LOGTAG, String.valueOf(throwable.getMessage()));
            return null;
        });
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

    public void onDestroy() {
        mBinding.addonsList.removeOnScrollListener(mScrollListener);
    }

    private AddonsManagerAdapter.Style createAddonStyle(@NonNull Context context) {
        return new AddonsManagerAdapter.Style(
                R.color.rhino,
                R.color.library_panel_title_text_color,
                R.color.library_panel_description_color,
                Typeface.DEFAULT_BOLD,
                R.color.fog,
                R.drawable.ic_icon_tray_private_browsing_v2
        );
    }

    // AddonsViewDelegate

    @Override
    public void onAddonItemClicked(Addon addon) {
        if (addon.isInstalled()) {
            mDelegate.showAddonOptions(addon);

        } else {
            mDelegate.showAddonOptionsDetails(addon, AddonsViewAdapter.ADDONS_LEVEL_1);
        }
    }

    @Override
    public void onInstallAddonButtonClicked(@NonNull Addon addon) {
        showInstallAddonDialog(addon);
    }

    private void showInstallAddonDialog(final @NonNull Addon addon) {
        String permissionsHtml = addon.translatePermissions().stream()
                .map(integer -> {
                    @StringRes int stringId = integer;
                    return "<li>&nbsp;" + mContext.getString(stringId) + "</li>";
                })
                .sorted()
                .collect(Collectors.joining());
        PromptData data = new PromptData.Builder()
                .withIconUrl(addon.getIconUrl())
                .withTitle(mContext.getString(
                        R.string.addons_install_dialog_title,
                        ExtensionsKt.getTranslatedName(addon)))
                .withBody(mContext.getString(
                        R.string.addons_install_dialog_body,
                        permissionsHtml))
                .withBodyGravity(Gravity.START)
                .withBtnMsg(new String[]{
                        mContext.getString(R.string.addons_install_dialog_cancel),
                        mContext.getString(R.string.addons_install_dialog_add)
                })
                .withCallback((index, isChecked) -> {
                    if (index == PromptDialogWidget.POSITIVE) {
                        mWidgetManager.getServicesProvider().getExecutors().mainThread().execute(() -> {
                            CancellableOperation installTask = mWidgetManager.getServicesProvider().getAddons().getAddonManager().installAddon(addon, addon1 -> {
                                showDownloadingAddonSuccessDialog(addon1);
                                mAdapter.updateAddon(addon1);
                                mBinding.getRoot().post(() -> mBinding.addonsList.smoothScrollToPosition(0));
                                return null;

                            }, (s, throwable) -> {
                                Log.d(LOGTAG, s);
                                if (!(throwable instanceof CancellationException)) {
                                    showDownloadingAddonErrorDialog(addon);
                                }
                                return null;
                            });

                            showDownloadingAddonDialog(addon, installTask);
                        });
                    }
                })
                .build();
        mWidgetManager.getFocusedWindow().showConfirmPrompt(data);
    }

    private void showDownloadingAddonDialog(final @NonNull Addon addon, final @NonNull CancellableOperation task) {
        PromptData data = new PromptData.Builder()
                .withIconUrl(addon.getIconUrl())
                .withTitle(mContext.getString(R.string.addons_downloading_dialog_title))
                .withBtnMsg(new String[]{
                        mContext.getString(R.string.addons_downloading_dialog_cancel)
                })
                .withCallback((index, isChecked) -> task.cancel())
                .build();
        mWidgetManager.getFocusedWindow().showConfirmPrompt(data);
    }

    private void showDownloadingAddonSuccessDialog(final @NonNull Addon addon) {
        PromptData data = new PromptData.Builder()
                .withIconUrl(addon.getIconUrl())
                .withTitle(mContext.getString(R.string.addons_download_success_dialog_title,
                        ExtensionsKt.getTranslatedName(addon),
                        mContext.getString(R.string.app_name)))
                .withBody(mContext.getString(R.string.addons_download_success_dialog_body))
                .withBtnMsg(new String[]{
                        mContext.getString(R.string.addons_download_success_dialog_ok)
                })
                .withCheckboxText(mContext.getString(R.string.addons_download_success_dialog_checkbox))
                .withCallback((index, isChecked) -> {
                    if (isChecked) {
                        mWidgetManager.getServicesProvider().getAddons().getAddonManager().setAddonAllowedInPrivateBrowsing(
                                addon,
                                true,
                                addon1 -> {
                                    mAdapter.updateAddon(addon1);
                                    mBinding.getRoot().post(() -> mBinding.addonsList.smoothScrollToPosition(0));
                                    return null;
                                },
                                throwable -> {
                                    Log.d(LOGTAG, String.valueOf(throwable.getMessage()));
                                    return null;
                                });
                    }
                })
                .build();
        mWidgetManager.getFocusedWindow().showConfirmPrompt(data);
    }

    private void showDownloadingAddonErrorDialog(final @NonNull Addon addon) {
        PromptData data = new PromptData.Builder()
                .withIconUrl(addon.getIconUrl())
                .withTitle(mContext.getString(R.string.addons_download_error_dialog_title))
                .withBtnMsg(new String[]{
                        mContext.getString(R.string.addons_download_error_dialog_ok)
                })
                .build();
        mWidgetManager.getFocusedWindow().showConfirmPrompt(data);
    }

    @Override
    public void onNotYetSupportedSectionClicked(@NonNull List<Addon> unsupportedAddons) {
        // Nothing to do in FxR,this is Fenix/Fennec migration specific
    }

}
