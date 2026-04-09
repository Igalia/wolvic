package com.igalia.wolvic.ui.widgets.settings;

import android.content.Context;
import android.graphics.Point;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.databinding.OptionsCustomSearchEnginesBinding;
import com.igalia.wolvic.search.CustomSearchEngine;
import com.igalia.wolvic.search.SearchEngineWrapper;
import com.igalia.wolvic.ui.adapters.CustomSearchEnginesAdapter;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;

import java.util.ArrayList;
import java.util.List;

public class CustomSearchEnginesOptionsView extends SettingsView implements CustomSearchEnginesAdapter.Delegate {

    private OptionsCustomSearchEnginesBinding mBinding;
    private CustomSearchEnginesAdapter mAdapter;

    public CustomSearchEnginesOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
        super(aContext, aWidgetManager);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        updateUI();
    }

    @Override
    protected void updateUI() {
        super.updateUI();

        LayoutInflater inflater = LayoutInflater.from(getContext());
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_custom_search_engines, this, true);

        // Header
        mBinding.headerLayout.setBackClickListener(view -> {
            mDelegate.showView(SettingViewType.SEARCH_ENGINE);
        });

        // Footer button - add new engine
        mBinding.footerLayout.setFooterButtonClickListener(view -> {
            mDelegate.showView(SettingViewType.SEARCH_ENGINE_EDIT);
        });

        // Setup RecyclerView
        mAdapter = new CustomSearchEnginesAdapter();
        mAdapter.setDelegate(this);
        mBinding.enginesList.setAdapter(mAdapter);

        loadEngines();
    }

    private void loadEngines() {
        List<CustomSearchEngine> engines = new ArrayList<>(
                SettingsStore.getInstance(getContext()).getCustomSearchEngines());
        
        mAdapter.setEngines(engines);

        mBinding.setIsEmpty(engines.isEmpty());
    }

    @Override
    public void onEngineSelected(View view, CustomSearchEngine engine) {
        mDelegate.showView(SettingViewType.SEARCH_ENGINE_EDIT, engine);
    }

    @Override
    public void onEngineDeleted(View view, CustomSearchEngine engine) {
        showConfirmDeleteSearchEngineDialog(engine.getName(), () -> {
            SearchEngineWrapper.get(getContext()).removeCustomSearchEngine(engine.getId());
            Toast.makeText(getContext(), R.string.search_engine_deleted, Toast.LENGTH_SHORT).show();
            loadEngines();
        });
    }

    @Override
    public Point getDimensions() {
        return new Point(WidgetPlacement.dpDimension(getContext(), R.dimen.settings_dialog_width),
                WidgetPlacement.dpDimension(getContext(), R.dimen.settings_dialog_height));
    }

    @Override
    protected SettingViewType getType() {
        return SettingViewType.CUSTOM_SEARCH_ENGINES;
    }

    @Override
    public void onShown() {
        super.onShown();
        loadEngines();
    }
}