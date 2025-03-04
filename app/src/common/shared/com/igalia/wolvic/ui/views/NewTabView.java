package com.igalia.wolvic.ui.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.GridLayoutManager;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.browser.components.TopSitesHelper;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.NewTabBinding;
import com.igalia.wolvic.ui.adapters.ExperiencesAdapter;
import com.igalia.wolvic.ui.adapters.TopSitesAdapter;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.utils.SystemUtils;

import java.util.List;

import mozilla.components.feature.top.sites.TopSite;
import mozilla.components.feature.top.sites.view.TopSitesView;
import mozilla.components.feature.top.sites.TopSitesFeature;

public class NewTabView extends FrameLayout implements TopSitesView {

    static final String LOGTAG = SystemUtils.createLogtag(NewTabView.class);
    public static final int TOP_SITES_COLUMNS = 8;
    public static final int EXPERIENCES_COLUMNS = 4;

    private WidgetManagerDelegate mWidgetManager;
    private NewTabBinding mBinding;
    private Handler mHandler;
    private TopSitesAdapter mTopSitesAdapter;
    private TopSitesFeature mTopSitesFeature; // ✅ FIXED TYPE
    private ExperiencesAdapter mExperiencesAdapter;

    public NewTabView(Context context) {
        super(context);
        initialize();
    }

    protected void initialize() {
        mWidgetManager = ((VRBrowserActivity) getContext());
        mHandler = new Handler(Looper.getMainLooper());
        updateUI();
    }

    @SuppressLint("ClickableViewAccessibility")
    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        mBinding = DataBindingUtil.inflate(inflater, R.layout.new_tab, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity) getContext());

        mBinding.favicon.setOnClickListener(v -> {
            openUrl(getContext().getString(R.string.home_page_url));
            Log.e(LOGTAG, "favicon onClick " + v);
        });

        mBinding.searchBar.setOnClickListener(v -> {
            Log.e(LOGTAG, "URL bar onClick " + v);
        });

        // Top sites
        mTopSitesAdapter = new TopSitesAdapter(null);
        mBinding.topSitesList.setAdapter(mTopSitesAdapter);
        mBinding.topSitesList.setLayoutManager(new GridLayoutManager(getContext(), TOP_SITES_COLUMNS));
        mBinding.topSitesList.setHasFixedSize(true);

        TopSitesHelper topSitesHelper = new TopSitesHelper(getContext(), ((VRBrowserActivity) getContext()).getLifecycle().getCoroutineScope());
        mTopSitesFeature = topSitesHelper.createFeature(this);
        mTopSitesFeature.start();

        // Experiences
        mExperiencesAdapter = new ExperiencesAdapter(getContext());
        mBinding.experiencesList.setAdapter(mExperiencesAdapter);
        mBinding.experiencesList.setLayoutManager(new GridLayoutManager(getContext(), EXPERIENCES_COLUMNS));
        mBinding.experiencesList.setHasFixedSize(true);
    }

    private void openUrl(@NonNull String url) {
        Session session = SessionStore.get().getActiveSession();
        session.loadUri(url);
    }

    @Override
    public void displayTopSites(@NonNull List<? extends TopSite> list) {
        Log.e(LOGTAG, "displayTopSites");
        for (TopSite site : list) {
            Log.e(LOGTAG, "    " + site.getTitle() + "  " + site.getUrl());
        }

        mTopSitesAdapter.updateTopSites(list);
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