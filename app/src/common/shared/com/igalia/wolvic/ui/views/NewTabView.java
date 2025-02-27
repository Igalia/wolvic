package com.igalia.wolvic.ui.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.browser.components.TopSitesHelper;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.NewTabBinding;
import com.igalia.wolvic.databinding.NewTabItemBinding;
import com.igalia.wolvic.ui.adapters.WebApp;
import com.igalia.wolvic.ui.adapters.WebAppsAdapter;
import com.igalia.wolvic.ui.callbacks.NewTabItemCallback;
import com.igalia.wolvic.ui.callbacks.WebAppItemCallback;
import com.igalia.wolvic.ui.viewmodel.WebAppsViewModel;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.utils.SystemUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import mozilla.components.browser.icons.IconRequest;
import mozilla.components.feature.top.sites.TopSite;
import mozilla.components.feature.top.sites.view.TopSitesView;
import mozilla.components.support.base.feature.LifecycleAwareFeature;

public class NewTabView extends FrameLayout implements TopSitesView {

    static final String LOGTAG = SystemUtils.createLogtag(NewTabView.class);

    private WidgetManagerDelegate mWidgetManager;
    private NewTabBinding mBinding;
    private WebAppsAdapter mWebAppsAdapter;
    private WebAppsViewModel mViewModel;
    private Handler mHandler;
    private TopSitesAdapter mTopSitesAdapter;
    LifecycleAwareFeature mTopSitesFeature;

    public NewTabView(Context context) {
        super(context);
        initialize();
    }

    protected void initialize() {
        mWidgetManager = ((VRBrowserActivity) getContext());
        mHandler = new Handler(Looper.getMainLooper());
        mViewModel = new ViewModelProvider((VRBrowserActivity) getContext(), ViewModelProvider.AndroidViewModelFactory.getInstance(((VRBrowserActivity) getContext()).getApplication())).get(WebAppsViewModel.class);

        updateUI();
    }

    @SuppressLint("ClickableViewAccessibility")
    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        mBinding = DataBindingUtil.inflate(inflater, R.layout.new_tab, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity) getContext());
        mBinding.setWebAppsViewModel(mViewModel);

        mBinding.favicon.setOnClickListener(v -> {
            openUrl(getContext().getString(R.string.home_page_url));
            // do I need to close this?
            Log.e(LOGTAG, "favicon onClick " + v);
        });

        mBinding.urlBar.setOnClickListener(v -> {

            Log.e(LOGTAG, "URL bar onClick " + v);

//            mWidgetManager.getNavigationBar().focusURLBar();
        });

        // Web apps

        mWebAppsAdapter = new NewTabWebAppsAdapter(mWebAppItemCallback, getContext());
        mBinding.webAppsList.setAdapter(mWebAppsAdapter);
        mBinding.webAppsList.post(() -> setupGrid(mBinding.webAppsList));

        // Set up GridLayoutManager with fixed size items
        int spanCount = 8; // Number of columns
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), spanCount);
        mBinding.webAppsList.setLayoutManager(layoutManager);

        mBinding.webAppsList.setOnTouchListener((v, event) -> {
            v.requestFocusFromTouch();
            return false;
        });
        mBinding.webAppsList.setHasFixedSize(true);
        mBinding.webAppsList.setItemViewCacheSize(20);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            mBinding.webAppsList.setDrawingCacheEnabled(true);
            mBinding.webAppsList.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
        }

        mViewModel.setIsLoading(true);

        List<WebApp> webApps = SessionStore.get().getWebAppsStore().getWebApps();
        updateWebApps(webApps);

        // Top sites
        mTopSitesAdapter = new TopSitesAdapter(null);
        mBinding.topSitesList.setAdapter(mTopSitesAdapter);
        mBinding.topSitesList.post(() -> setupGrid(mBinding.topSitesList));

        TopSitesHelper topSitesHelper = new TopSitesHelper(getContext());

        mTopSitesFeature = topSitesHelper.createFeature(this);
    }

    private void openUrl(@NonNull String url) {
        Session session = SessionStore.get().getActiveSession();
        session.loadUri(url);
    }

    private void updateWebApps(@NonNull List<WebApp> webApps) {
        mHandler.post(() -> {
            mWebAppsAdapter.setWebAppsList(webApps);
            mViewModel.setIsEmpty(webApps.isEmpty());
            mViewModel.setIsLoading(false);
            mBinding.executePendingBindings();
        });
    }

    private void setupGrid(RecyclerView recyclerView) {
        int parentWidth = recyclerView.getWidth() - recyclerView.getPaddingLeft() - recyclerView.getPaddingRight();
        int desiredItemWidth = getResources().getDimensionPixelSize(R.dimen.new_tab_item_width);
        int spanCount = Math.max(1, parentWidth / desiredItemWidth);

        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), spanCount);
        recyclerView.setLayoutManager(layoutManager);
    }

    private final WebAppItemCallback mWebAppItemCallback = new WebAppItemCallback() {
        @Override
        public void onClick(@NonNull View view, @NonNull WebApp item) {

            Log.e("NEWTAB", "item onClick " + item);

            mBinding.webAppsList.requestFocusFromTouch();

            openUrl(item.getStartUrl());
        }

        @Override
        public void onDelete(@NonNull View view, @NonNull WebApp item) {
            mBinding.webAppsList.requestFocusFromTouch();
            SessionStore.get().getWebAppsStore().removeWebAppById(item.getId());
        }
    };

    private final NewTabItemCallback mNewTabItemCallback = new NewTabItemCallback() {
        @Override
        public void onClick(@NonNull View view, @NonNull String name, @NonNull String url) {
            Log.e(LOGTAG, "onClick");
            openUrl(url);
        }

        @Override
        public void onDelete(@NonNull View view, @NonNull String name, @NonNull String url) {
            Log.e(LOGTAG, "onDelete");
        }
    };

    @Override
    public void displayTopSites(@NonNull List<? extends TopSite> list) {

        Log.e(LOGTAG, "displayTopSites");
        for (TopSite site : list) {
            Log.e(LOGTAG, "    " + site.getTitle() + "  " + site.getUrl());
        }

        mTopSitesAdapter.updateTopSites(list);
    }

    public static class NewTabItemViewHolder extends RecyclerView.ViewHolder {
        final NewTabItemBinding binding;

        NewTabItemViewHolder(@NonNull NewTabItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    public class NewTabWebAppsAdapter extends WebAppsAdapter {

        public NewTabWebAppsAdapter(@Nullable WebAppItemCallback clickCallback, Context aContext) {
            super(clickCallback, aContext);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            NewTabItemBinding binding = DataBindingUtil
                    .inflate(LayoutInflater.from(parent.getContext()), R.layout.new_tab_item,
                            parent, false);
            binding.setCallback(mNewTabItemCallback);

            return new NewTabItemViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            NewTabItemBinding binding = ((NewTabItemViewHolder) holder).binding;
            WebApp item = mWebAppsList.get(position);
            binding.setTitle(item.getName());
            binding.setUrl(item.getStartUrl());
            SessionStore.get().getBrowserIcons().loadIntoView(binding.webAppIcon, item.getStartUrl(), IconRequest.Size.LAUNCHER);
        }
    }

    public interface OnTopSiteClickListener {
        void onTopSiteClicked(TopSite site);

        void onTopSiteRemoved(TopSite site);
    }

    public class TopSitesAdapter extends RecyclerView.Adapter<NewTabItemViewHolder> {
        private List<TopSite> mTopSites = Collections.emptyList();
        private final OnTopSiteClickListener listener;


        public TopSitesAdapter(OnTopSiteClickListener listener) {
            this.listener = listener;
        }

        @NonNull
        @Override
        public NewTabItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            NewTabItemBinding binding = DataBindingUtil
                    .inflate(LayoutInflater.from(parent.getContext()), R.layout.new_tab_item,
                            parent, false);
            binding.setCallback(mNewTabItemCallback);
            return new NewTabItemViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull NewTabItemViewHolder holder, int position) {
            TopSite site = mTopSites.get(position);

            Log.e(LOGTAG, "onBindViewHolder " + site.getTitle() + " " + site.getUrl());

            NewTabItemBinding binding = holder.binding;
            binding.setTitle(site.getTitle());
            binding.setUrl(site.getUrl());
            SessionStore.get().getBrowserIcons().loadIntoView(binding.webAppIcon, site.getUrl(), IconRequest.Size.LAUNCHER);
        }

        @Override
        public int getItemCount() {
            return mTopSites.size();
        }

        public void updateTopSites(@NonNull List<? extends TopSite> newTopSites) {
            Log.e(LOGTAG, "updateTopSites");
            for (TopSite site : newTopSites) {
                Log.e(LOGTAG, "    " + site.getTitle() + "  " + site.getUrl());
            }

            mTopSites = new ArrayList<>(newTopSites);
            notifyDataSetChanged();
        }
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