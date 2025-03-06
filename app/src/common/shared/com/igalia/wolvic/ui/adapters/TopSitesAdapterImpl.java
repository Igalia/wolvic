package com.igalia.wolvic.ui.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.components.TopSitesAdapter;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.TopSitesItemBinding;
import com.igalia.wolvic.utils.SystemUtils;

import java.util.ArrayList;
import java.util.List;

import mozilla.components.browser.icons.IconRequest;
import mozilla.components.feature.top.sites.TopSite;

public class TopSitesAdapterImpl extends RecyclerView.Adapter<TopSitesAdapterImpl.ViewHolder> implements TopSitesAdapter {

    private static final String LOGTAG = SystemUtils.createLogtag(TopSitesAdapterImpl.class);

    private final List<TopSite> mTopSites = new ArrayList();
    private final TopSitesAdapter.ClickListener mClickListener;

    public TopSitesAdapterImpl(@NonNull TopSitesAdapter.ClickListener listener) {
        this.mClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TopSitesItemBinding binding = DataBindingUtil
                .inflate(LayoutInflater.from(parent.getContext()), R.layout.top_sites_item,
                        parent, false);
        binding.setListener(mClickListener);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TopSite site = mTopSites.get(position);
        TopSitesItemBinding binding = holder.binding;
        binding.setSite(site);
        SessionStore.get().getBrowserIcons().loadIntoView(binding.webAppIcon, site.getUrl(), IconRequest.Size.LAUNCHER);
    }

    @Override
    public int getItemCount() {
        return mTopSites.size();
    }

    public void updateTopSites(@NonNull List<? extends TopSite> topSites) {
        mTopSites.clear();
        mTopSites.addAll(topSites);
        notifyDataSetChanged();
    }

    @Override
    public void setClickListener(@NonNull TopSitesAdapter.ClickListener clickListener) {

    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TopSitesItemBinding binding;

        ViewHolder(@NonNull TopSitesItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
