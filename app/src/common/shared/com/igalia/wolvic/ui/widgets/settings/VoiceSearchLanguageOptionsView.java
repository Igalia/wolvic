/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets.settings;

import android.content.Context;
import android.graphics.Point;
import android.view.LayoutInflater;

import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.OptionsLanguageVoiceBinding;
import com.igalia.wolvic.speech.SpeechRecognizer;
import com.igalia.wolvic.ui.views.settings.RadioGroupSetting;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.utils.LocaleUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class VoiceSearchLanguageOptionsView extends SettingsView {

    private OptionsLanguageVoiceBinding mBinding;
    private List<String> mSupportedLanguages;

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
        SpeechRecognizer speechRecognizer = mWidgetManager.getServicesProvider().getSpeechRecognizer();

        mSupportedLanguages = (speechRecognizer != null) ? speechRecognizer.getSupportedLanguages() : Collections.emptyList();

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

        List<String> languageNames = new ArrayList<>(mSupportedLanguages.size());
        for (String language : mSupportedLanguages) {
            languageNames.add(LocaleUtils.getVoiceLanguageName(getContext(), language));
        }
        mBinding.languageRadio.setOptions(languageNames.toArray(new String[0]));

        String languageId = LocaleUtils.getVoiceSearchLanguageId(getContext());
        mBinding.languageRadio.setOnCheckedChangeListener(mLanguageListener);
        setLanguage(mSupportedLanguages.indexOf(languageId), false);
    }

    @Override
    protected boolean reset() {
        String defaultLanguageId = LocaleUtils.getDefaultLanguageId();
        setLanguage(LocaleUtils.getIndexForSupportedLanguageId(defaultLanguageId), true);
        return false;
    }

    private RadioGroupSetting.OnCheckedChangeListener mLanguageListener = (radioGroup, checkedId, doApply) -> {
        String languageId = mSupportedLanguages.get(mBinding.languageRadio.getCheckedRadioButtonId());
        String currentLanguageId = LocaleUtils.getVoiceSearchLanguageId(getContext());

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
            String languageId = mSupportedLanguages.get(checkedId);
            LocaleUtils.setVoiceSearchLanguageId(getContext(), languageId);
        }
    }

    @Override
    public Point getDimensions() {
        return new Point(WidgetPlacement.dpDimension(getContext(), R.dimen.settings_dialog_width),
                WidgetPlacement.dpDimension(getContext(), R.dimen.language_options_height));
    }

    @Override
    protected SettingViewType getType() {
        return SettingViewType.LANGUAGE_VOICE;
    }
    
}
