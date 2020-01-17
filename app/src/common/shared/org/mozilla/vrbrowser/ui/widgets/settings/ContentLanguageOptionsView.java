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

import org.mozilla.gecko.util.ThreadUtils;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.OptionsLanguageContentBinding;
import org.mozilla.vrbrowser.ui.adapters.Language;
import org.mozilla.vrbrowser.ui.adapters.LanguagesAdapter;
import org.mozilla.vrbrowser.ui.callbacks.LanguageItemCallback;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.LocaleUtils;

import java.util.Collections;
import java.util.List;

public class ContentLanguageOptionsView extends SettingsView {

    private OptionsLanguageContentBinding mBinding;
    private LanguagesAdapter mPreferredAdapter;
    private LanguagesAdapter mAvailableAdapter;

    public ContentLanguageOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
        super(aContext, aWidgetManager);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Preferred languages adapter
        mPreferredAdapter = new LanguagesAdapter(getContext(), mLanguageItemCallback, true);
        mPreferredAdapter.setLanguageList(LocaleUtils.getPreferredLanguages(getContext()));

        // Available languages adapter
        mAvailableAdapter = new LanguagesAdapter(getContext(), mLanguageItemCallback, false);
        mAvailableAdapter.setLanguageList(LocaleUtils.getAvailableLanguages());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_language_content, this, true);

        // Header
        mBinding.headerLayout.setBackClickListener(view -> {
            mDelegate.showView(new LanguageOptionsView(getContext(), mWidgetManager));
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

        mBinding.executePendingBindings();
    }

    private OnClickListener mResetListener = (view) -> {
        reset();
    };

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
        SettingsStore.getInstance(getContext()).setContentLocales(
                LocaleUtils.getLocalesFromLanguages(mPreferredAdapter.getItems()));
        SessionStore.get().setLocales(
                LocaleUtils.getLocalesFromLanguages(mPreferredAdapter.getItems()));
    }

    private void refreshLanguages() {
        ThreadUtils.postToUiThread(() -> {
            mPreferredAdapter.setLanguageList(LocaleUtils.getPreferredLanguages(getContext()));
            mAvailableAdapter.setLanguageList(LocaleUtils.getAvailableLanguages());
        });
    }

    @Override
    protected boolean reset() {
        String systemLocale = LocaleUtils.getClosestAvailableLocale(LocaleUtils.getDeviceLanguage().getId());
        List<Language> preferredLanguages = LocaleUtils.getPreferredLanguages(getContext());
        if (preferredLanguages.size() > 1 || !preferredLanguages.get(0).getId().equals(systemLocale)) {
            SettingsStore.getInstance(getContext()).setContentLocales(Collections.emptyList());
            SessionStore.get().setLocales(Collections.emptyList());
            LocaleUtils.resetLanguages();
            refreshLanguages();
        }

        return false;
    }

    @Override
    public void onShown() {
        super.onShown();

        refreshLanguages();
    }

}
