package com.igalia.wolvic.ui.widgets.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;

import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.R;
import com.igalia.wolvic.databinding.OptionsSearchEngineBinding;
import com.igalia.wolvic.search.SearchEngineWrapper;
import com.igalia.wolvic.ui.views.settings.RadioGroupSetting;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;

import java.util.ArrayList;
import java.util.List;

import mozilla.components.browser.search.SearchEngine;

public class SearchEngineView extends SettingsView implements SharedPreferences.OnSharedPreferenceChangeListener {

    private OptionsSearchEngineBinding mBinding;
    private final RadioGroupSetting.OnCheckedChangeListener mSearchEngineListener = (radioGroup, checkedId, doApply) -> {
        setSearchEngine(checkedId, true);
    };
    private final OnClickListener mResetListener = (view) -> reset();
    private List<SearchEngine> mSearchEngines;

    public SearchEngineView(Context aContext, WidgetManagerDelegate aWidgetManager) {
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

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_search_engine, this, true);

        mScrollbar = mBinding.scrollbar;

        // Header
        mBinding.headerLayout.setBackClickListener(view -> {
            mDelegate.showView(SettingViewType.PRIVACY);
        });

        // Footer
        mBinding.footerLayout.setFooterButtonClickListener(mResetListener);

        mSearchEngines = new ArrayList<>(SearchEngineWrapper.get(getContext()).getAvailableSearchEngines());
        mBinding.searchEngineRadio.setOptions(mSearchEngines.stream().map(SearchEngine::getName).toArray(String[]::new));

        mBinding.searchEngineRadio.setOnCheckedChangeListener(mSearchEngineListener);
        int checkedIndex = mSearchEngines.indexOf(SearchEngineWrapper.get(getContext()).getCurrentSearchEngine());
        setSearchEngine(checkedIndex, false);
    }

    @Override
    protected boolean reset() {
        SearchEngineWrapper.get(getContext()).setDefaultSearchEngine();
        return false;
    }

    private void setSearchEngine(int checkedId, boolean doApply) {
        mBinding.searchEngineRadio.setOnCheckedChangeListener(null);
        mBinding.searchEngineRadio.setChecked(checkedId, doApply);
        mBinding.searchEngineRadio.setOnCheckedChangeListener(mSearchEngineListener);

        SearchEngine searchEngine = mSearchEngines.get(checkedId);
        if (searchEngine != null && doApply) {
            SearchEngineWrapper.get(getContext()).setCurrentSearchEngineId(getContext(), searchEngine.getIdentifier());
        }
    }

    @Override
    public Point getDimensions() {
        return new Point(WidgetPlacement.dpDimension(getContext(), R.dimen.settings_dialog_width),
                WidgetPlacement.dpDimension(getContext(), R.dimen.settings_dialog_height));
    }

    @Override
    protected SettingViewType getType() {
        return SettingViewType.SEARCH_ENGINE;
    }

    @Override
    public void onShown() {
        super.onShown();
        PreferenceManager.getDefaultSharedPreferences(getContext()).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onHidden() {
        super.onHidden();
        PreferenceManager.getDefaultSharedPreferences(getContext()).unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getContext().getString(R.string.settings_key_search_engine_id))) {
            int checkedId = mBinding.searchEngineRadio.getCheckedRadioButtonId();
            if (checkedId >= 0 && checkedId < mSearchEngines.size()) {
                SearchEngine selected = mSearchEngines.get(checkedId);
                String storedSearchEngineId = sharedPreferences.getString(key, "");
                if (storedSearchEngineId.equals(selected.getIdentifier())) {
                    // The selected radio button is already the correct one, so we are done.
                    return;
                }
            }
            // Otherwise, update the UI.
            updateUI();
        }
    }
}
