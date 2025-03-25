package com.igalia.wolvic.ui.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.browser.components.TopSitesAdapter;
import com.igalia.wolvic.browser.components.TopSitesHelper;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.NewTabBinding;
import com.igalia.wolvic.ui.adapters.AnnouncementsAdapter;
import com.igalia.wolvic.ui.adapters.ExperiencesAdapter;
import com.igalia.wolvic.ui.adapters.TopSitesAdapterImpl;
import com.igalia.wolvic.ui.viewmodel.SettingsViewModel;
import com.igalia.wolvic.utils.SystemUtils;

import mozilla.components.feature.top.sites.TopSite;
import mozilla.components.feature.top.sites.TopSitesFeature;

public class NewTabView extends FrameLayout {

    static final String LOGTAG = SystemUtils.createLogtag(NewTabView.class);

    private NewTabBinding mBinding;
    private SettingsViewModel mSettingsViewModel;
    private AnnouncementsAdapter mAnnouncementsAdapter;
    private TopSitesAdapterImpl mTopSitesAdapter;
    private TopSitesFeature mTopSitesFeature;
    private ExperiencesAdapter mExperiencesAdapter;

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

        // Announcements
        mAnnouncementsAdapter = new AnnouncementsAdapter(getContext());
        mAnnouncementsAdapter.setClickListener(announcement -> {
            if (announcement.getLink() != null) {
                openUrl(announcement.getLink());
            }
        });
        mBinding.announcementsList.setAdapter(mAnnouncementsAdapter);
        mBinding.announcementsList.setHasFixedSize(false);

        mSettingsViewModel.getAnnouncements().observe((VRBrowserActivity) getContext(), remoteAnnouncements -> {
            mAnnouncementsAdapter.updateAnnouncements(remoteAnnouncements);
        });

        // Top sites
        mTopSitesAdapter = new TopSitesAdapterImpl(mTopSitesClickListener);
        mBinding.topSitesList.setAdapter(mTopSitesAdapter);
        mBinding.topSitesList.setHasFixedSize(true);

        TopSitesHelper topSitesHelper = new TopSitesHelper(getContext(), ((VRBrowserActivity) getContext()).getCoroutineScope());
        mTopSitesFeature = topSitesHelper.createFeature(mTopSitesAdapter);
        mTopSitesFeature.start();

        // Experiences
        mExperiencesAdapter = new ExperiencesAdapter(getContext());
        mExperiencesAdapter.setClickListener(experience -> openUrl(experience.getUrl()));
        mBinding.experiencesList.setAdapter(mExperiencesAdapter);
        // The grid items have different sizes and span a different nr of cells.
        mBinding.experiencesList.setHasFixedSize(false);
        GridLayoutManager layoutManager = (GridLayoutManager) mBinding.experiencesList.getLayoutManager();
        layoutManager.setSpanSizeLookup(mExperiencesAdapter.getSpanSizeLookup(layoutManager.getSpanCount()));

        mSettingsViewModel.getExperiences().observe((VRBrowserActivity) getContext(), experiences -> {
            mExperiencesAdapter.updateExperiences(experiences);
        });
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