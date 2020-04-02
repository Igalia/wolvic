/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.settings;

import android.content.Context;
import android.graphics.Point;
import android.view.LayoutInflater;
import android.view.View;

import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.OptionsLanguageContentBinding;
import org.mozilla.vrbrowser.ui.adapters.Language;
import org.mozilla.vrbrowser.ui.adapters.LanguagesAdapter;
import org.mozilla.vrbrowser.ui.callbacks.LanguageItemCallback;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.LocaleUtils;

public class ContentLanguageOptionsView extends SettingsView {

    private OptionsLanguageContentBinding mBinding;
    private LanguagesAdapter mPreferredAdapter;
    private LanguagesAdapter mAvailableAdapter;

    public ContentLanguageOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
        super(aContext, aWidgetManager);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        // Preferred languages adapter
        mPreferredAdapter = new LanguagesAdapter(getContext(), mLanguageItemCallback, true);

        // Available languages adapter
        mAvailableAdapter = new LanguagesAdapter(getContext(), mLanguageItemCallback, false);

        updateUI();
    }

    @Override
    protected void updateUI() {
        super.updateUI();

        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_language_content, this, true);

        // Header
        mBinding.headerLayout.setBackClickListener(view -> {
            mDelegate.showView(SettingViewType.LANGUAGE);
        });
        mBinding.headerLayout.setHelpClickListener(view -> {
            SessionStore.get().getActiveSession().loadUri(getResources().getString(R.string.sumo_language_content_url));
            mDelegate.exitWholeSettings();
        });

        // Adapters
        mBinding.preferredList.setAdapter(mPreferredAdapter);
        mBinding.availableList.setAdapter(mAvailableAdapter);

        // Footer
        mBinding.footerLayout.setFooterButtonClickListener(mResetListener);

        mPreferredAdapter.setLanguageList(LocaleUtils.getPreferredLanguages(getContext()));
        mAvailableAdapter.setLanguageList(LocaleUtils.getAvailableLanguages(getContext()));

        mBinding.executePendingBindings();
    }

    private OnClickListener mResetListener = (view) -> reset();

    @Override
    public Point getDimensions() {
        return new Point( WidgetPlacement.dpDimension(getContext(), R.dimen.settings_dialog_width),
                WidgetPlacement.dpDimension(getContext(), R.dimen.settings_dialog_height));
    }

    private LanguageItemCallback mLanguageItemCallback = new LanguageItemCallback() {

        @Override
        public void onAdd(View view, Language language) {
            mPreferredAdapter.onAdd(language);
            mAvailableAdapter.onAdd(language);

            saveCurrentLanguages();
        }

        @Override
        public void onRemove(View view, Language language) {
            mPreferredAdapter.onRemove(language);
            mAvailableAdapter.onRemove(language);

            saveCurrentLanguages();
        }

        @Override
        public void onMoveUp(View view, Language language) {
            mPreferredAdapter.moveItemUp(view, language);

            saveCurrentLanguages();
        }

        @Override
        public void onMoveDown(View view, Language language) {
            mPreferredAdapter.moveItemDown(view, language);

            saveCurrentLanguages();
        }
    };

    private void saveCurrentLanguages() {
        LocaleUtils.setPreferredLanguages(getContext(), mPreferredAdapter.getItems());
    }

    private void refreshLanguages() {
        post(() -> {
            mPreferredAdapter.setLanguageList(LocaleUtils.getPreferredLanguages(getContext()));
            mAvailableAdapter.setLanguageList(LocaleUtils.getAvailableLanguages(getContext()));
        });
    }

    @Override
    protected boolean reset() {
        LocaleUtils.resetPreferredLanguages(getContext());
        refreshLanguages();

        return false;
    }

    @Override
    public void onShown() {
        super.onShown();

        refreshLanguages();
    }

    @Override
    protected SettingViewType getType() {
        return SettingViewType.LANGUAGE_CONTENT;
    }
}
