package com.igalia.wolvic.ui.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.browser.components.TopSitesAdapter;
import com.igalia.wolvic.browser.components.TopSitesHelper;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.NewTabBinding;
import com.igalia.wolvic.ui.adapters.TopSitesAdapterImpl;
import com.igalia.wolvic.ui.viewmodel.SettingsViewModel;
import com.igalia.wolvic.utils.SystemUtils;

import mozilla.components.feature.top.sites.TopSite;
import mozilla.components.feature.top.sites.TopSitesFeature;

public class NewTabView extends FrameLayout {

    static final String LOGTAG = SystemUtils.createLogtag(NewTabView.class);

    private NewTabBinding mBinding;
    private SettingsViewModel mSettingsViewModel;
    private TopSitesAdapterImpl mTopSitesAdapter;
    private TopSitesFeature mTopSitesFeature;

    public NewTabView(Context context) {
        super(context);
        initialize();
    }

    protected void initialize() {
        updateUI();
    }

    @SuppressLint("ClickableViewAccessibility")
    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        mSettingsViewModel = new ViewModelProvider((VRBrowserActivity) getContext(), ViewModelProvider.AndroidViewModelFactory.getInstance(((VRBrowserActivity) getContext()).getApplication())).get(SettingsViewModel.class);

        mBinding = DataBindingUtil.inflate(inflater, R.layout.new_tab, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity) getContext());
        mBinding.setSettingsmodel(mSettingsViewModel);

        mBinding.logo.setOnClickListener(v -> openUrl(getContext().getString(R.string.home_page_url)));

        // Top sites
        mTopSitesAdapter = new TopSitesAdapterImpl(mTopSitesClickListener);
        mBinding.topSitesList.setAdapter(mTopSitesAdapter);
        mBinding.topSitesList.setHasFixedSize(true);

        TopSitesHelper topSitesHelper = new TopSitesHelper(getContext(), ((VRBrowserActivity) getContext()).getCoroutineScope());
        mTopSitesFeature = topSitesHelper.createFeature(mTopSitesAdapter);
        mTopSitesFeature.start();
    }

    private final TopSitesAdapter.ClickListener mTopSitesClickListener =
            new TopSitesAdapter.ClickListener() {
                @Override
                public void onClicked(@NonNull TopSite site) {
                    openUrl(site.getUrl());
                }

                @Override
                public void onRemoved(@NonNull TopSite site) {
                    // TODO
                }

                @Override
                public void onPinned(@NonNull TopSite site) {
                    // TODO
                }
            };

    private void openUrl(@NonNull String url) {
        Session session = SessionStore.get().getActiveSession();
        session.loadUri(url);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mTopSitesFeature.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mTopSitesFeature.stop();
    }
}