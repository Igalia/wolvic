package com.igalia.wolvic.ui.widgets.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Point;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.R;
import com.igalia.wolvic.databinding.OptionsEditSearchEngineBinding;
import com.igalia.wolvic.search.CustomSearchEngine;
import com.igalia.wolvic.search.SearchEngineValidation;
import com.igalia.wolvic.search.SearchEngineWrapper;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;

@SuppressLint("ViewConstructor")
class EditSearchEngineOptionsView extends SettingsView {

    private OptionsEditSearchEngineBinding mBinding;
    private CustomSearchEngine mEngine;
    private boolean mIsEditMode;

    private boolean mNameValid = false;
    private boolean mSearchUrlValid = false;
    private boolean mSuggestUrlValid = true;

    public EditSearchEngineOptionsView(@NonNull Context aContext, @NonNull WidgetManagerDelegate aWidgetManager, @Nullable CustomSearchEngine engine) {
        super(aContext, aWidgetManager);
        mEngine = engine;
        mIsEditMode = engine != null;
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        updateUI();
    }

    @Override
    protected void updateUI() {
        super.updateUI();

        LayoutInflater inflater = LayoutInflater.from(getContext());
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_edit_search_engine, this, true);

        mScrollbar = mBinding.scrollbar;

        mBinding.headerLayout.setBackClickListener(view -> {
            onDismiss();
        });

        mBinding.footerLayout.setFooterButtonClickListener(mIsEditMode ? v -> onDismiss() : v -> saveEngine());
        mBinding.footerLayout.setFooterButtonText(mIsEditMode ? R.string.close_button : R.string.add_search_engine_save);
        mBinding.footerLayout.setFooterButtonEnabled(false);

        mBinding.nameEdit.setHint1(getContext().getString(R.string.add_search_engine_name_hint));
        mBinding.searchUrlEdit.setHint1(getContext().getString(R.string.add_search_engine_search_url_hint));
        mBinding.suggestUrlEdit.setHint1(getContext().getString(R.string.add_search_engine_suggest_url_hint));

        mBinding.nameEdit.setOnSaveClickedListener(v -> validateAndSaveName());
        mBinding.searchUrlEdit.setOnSaveClickedListener(v -> validateAndSaveSearchUrl());
        mBinding.suggestUrlEdit.setOnSaveClickedListener(v -> validateAndSaveSuggestUrl());

        if (mIsEditMode) {
            mBinding.nameEdit.setFirstText(mEngine.getName());
            mBinding.searchUrlEdit.setFirstText(mEngine.getSearchUrl());
            if (mEngine.getSuggestUrl() != null) {
                mBinding.suggestUrlEdit.setFirstText(mEngine.getSuggestUrl());
            }
            mNameValid = true;
            mSearchUrlValid = true;
            mSuggestUrlValid = true;
            updateFooterButtonState();
        }
    }

    @Override
    public void onShown() {
        super.onShown();
        mBinding.headerLayout.setTitle(mIsEditMode ? R.string.edit_search_engine_title : R.string.add_search_engine_title);
    }

    private void validateAndSaveName() {
        String name = mBinding.nameEdit.getFirstText().trim();
        SearchEngineValidation.ValidationResult result = SearchEngineValidation.validateName(getContext(), name);

        if (!result.isValid()) {
            showError(result.getErrorMessage());
            return;
        }

        mNameValid = true;
        hideError();
        if (mIsEditMode) {
            saveCurrentEngine(name, null, null);
        }
        updateFooterButtonState();
    }

    private void validateAndSaveSearchUrl() {
        String searchUrl = mBinding.searchUrlEdit.getFirstText().trim();
        SearchEngineValidation.ValidationResult result = SearchEngineValidation.validateSearchUrl(getContext(), searchUrl);

        if (!result.isValid()) {
            showError(result.getErrorMessage());
            return;
        }

        mSearchUrlValid = true;
        hideError();
        if (mIsEditMode) {
            saveCurrentEngine(null, searchUrl, null);
        }
        updateFooterButtonState();
    }

    private void validateAndSaveSuggestUrl() {
        String suggestUrl = mBinding.suggestUrlEdit.getFirstText().trim();
        SearchEngineValidation.ValidationResult result = SearchEngineValidation.validateSuggestUrl(getContext(),
                suggestUrl.isEmpty() ? null : suggestUrl);

        if (!result.isValid()) {
            showError(result.getErrorMessage());
            return;
        }

        mSuggestUrlValid = true;
        hideError();
        if (mIsEditMode) {
            saveCurrentEngine(null, null, suggestUrl);
        }
        updateFooterButtonState();
    }

    private void showError(String message) {
        mBinding.errorText.setVisibility(View.VISIBLE);
        mBinding.errorText.setText(message);
    }

    private void hideError() {
        mBinding.errorText.setVisibility(View.GONE);
    }

    private void updateFooterButtonState() {
        mBinding.footerLayout.setFooterButtonEnabled(mNameValid && mSearchUrlValid && mSuggestUrlValid);
    }

    private void saveEngine() {
        String name = mBinding.nameEdit.getFirstText().trim();
        String searchUrl = mBinding.searchUrlEdit.getFirstText().trim();
        String suggestUrl = mBinding.suggestUrlEdit.getFirstText().trim();

        String id = CustomSearchEngine.ID_PREFIX + System.currentTimeMillis();
        CustomSearchEngine engine = new CustomSearchEngine.Builder()
                .setId(id)
                .setName(name)
                .setSearchUrl(searchUrl)
                .setSuggestUrl(suggestUrl.isEmpty() ? null : suggestUrl)
                .build();
        SearchEngineWrapper.get(getContext()).addCustomSearchEngine(engine);

        Toast.makeText(getContext(), R.string.add_search_engine_save, Toast.LENGTH_SHORT).show();
        onDismiss();
    }

    private void saveCurrentEngine(@Nullable String name, @Nullable String searchUrl, @Nullable String suggestUrl) {
        if (mEngine == null) {
            return;
        }

        String finalName = name != null ? name : mEngine.getName();
        String finalSearchUrl = searchUrl != null ? searchUrl : mEngine.getSearchUrl();
        String finalSuggestUrl;
        if (suggestUrl != null) {
            finalSuggestUrl = suggestUrl.isEmpty() ? null : suggestUrl;
        } else {
            finalSuggestUrl = mEngine.getSuggestUrl();
        }

        CustomSearchEngine engine = new CustomSearchEngine.Builder()
                .setId(mEngine.getId())
                .setName(finalName)
                .setSearchUrl(finalSearchUrl)
                .setSuggestUrl(finalSuggestUrl)
                .build();
        SearchEngineWrapper.get(getContext()).updateCustomSearchEngine(engine);
        mEngine = engine;
    }

    @Override
    public Point getDimensions() {
        return new Point(WidgetPlacement.dpDimension(getContext(), R.dimen.settings_dialog_width),
                WidgetPlacement.dpDimension(getContext(), R.dimen.display_options_height));
    }

    @Override
    protected SettingViewType getType() {
        return SettingViewType.SEARCH_ENGINE_EDIT;
    }
}
