/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.settings;

import android.content.Context;
import android.graphics.Point;
import android.view.LayoutInflater;

import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.OptionsLanguageVoiceBinding;
import org.mozilla.vrbrowser.ui.views.settings.RadioGroupSetting;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.LocaleUtils;

class VoiceSearchLanguageOptionsView extends SettingsView {

    private OptionsLanguageVoiceBinding mBinding;

    public VoiceSearchLanguageOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
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
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_language_voice, this, true);

        mScrollbar = mBinding.scrollbar;

        // Header
        mBinding.headerLayout.setBackClickListener(view -> {
            mDelegate.showView(SettingViewType.LANGUAGE);
        });
        mBinding.headerLayout.setHelpClickListener(view -> {
            SessionStore.get().getActiveSession().loadUri(getResources().getString(R.string.sumo_language_voice_url));
            mDelegate.exitWholeSettings();
        });

        // Footer
        mBinding.footerLayout.setFooterButtonClickListener(mResetListener);

        mBinding.languageRadio.setOptions(LocaleUtils.getSupportedLocalizedLanguages());

        String languageId = LocaleUtils.getVoiceSearchLanguageId(getContext());
        mBinding.languageRadio.setOnCheckedChangeListener(mLanguageListener);
        setLanguage(LocaleUtils.getIndexForSupportedLanguageId(languageId), false);
    }

    @Override
    protected boolean reset() {
        String defaultLanguageId = LocaleUtils.getDefaultLanguageId();
        setLanguage(LocaleUtils.getIndexForSupportedLanguageId(defaultLanguageId), true);
        return false;
    }

    private RadioGroupSetting.OnCheckedChangeListener mLanguageListener = (radioGroup, checkedId, doApply) -> {
        String currentLanguageId = LocaleUtils.getVoiceSearchLanguageId(getContext());
        String languageId = LocaleUtils.getSupportedLanguageIdForIndex(mBinding.languageRadio.getCheckedRadioButtonId());

        if (!languageId.equalsIgnoreCase(currentLanguageId)) {
            setLanguage(checkedId, true);
        }
    };

    private OnClickListener mResetListener = (view) -> reset();

    private void setLanguage(int checkedId, boolean doApply) {
        mBinding.languageRadio.setOnCheckedChangeListener(null);
        mBinding.languageRadio.setChecked(checkedId, doApply);
        mBinding.languageRadio.setOnCheckedChangeListener(mLanguageListener);

        if (doApply) {
            String languageId = LocaleUtils.getSupportedLanguageIdForIndex(checkedId);
            LocaleUtils.setVoiceSearchLanguageId(getContext(), languageId);
        }
    }

    @Override
    public Point getDimensions() {
        return new Point( WidgetPlacement.dpDimension(getContext(), R.dimen.settings_dialog_width),
                WidgetPlacement.dpDimension(getContext(), R.dimen.settings_dialog_height));
    }

    @Override
    protected SettingViewType getType() {
        return SettingViewType.LANGUAGE_VOICE;
    }
    
}
