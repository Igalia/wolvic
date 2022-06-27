/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets.settings;

import android.content.Context;
import android.graphics.Point;
import android.view.LayoutInflater;

import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.BuildConfig;
import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.databinding.OptionsLanguageVoiceBinding;
import com.igalia.wolvic.speech.SpeechServices;
import com.igalia.wolvic.ui.views.settings.RadioGroupSetting;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.utils.LocaleUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

class VoiceSearchServiceOptionsView extends SettingsView {

    private OptionsLanguageVoiceBinding mBinding;
    private final List<String> mSpeechServices = Arrays.asList(BuildConfig.SPEECH_SERVICES);

    public VoiceSearchServiceOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
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

        // Footer
        mBinding.footerLayout.setFooterButtonClickListener(mResetListener);

        List<String> speechServicesNames = new ArrayList<>();
        for (String service : mSpeechServices) {
            speechServicesNames.add(getContext().getString(SpeechServices.getNameResource(service)));
        }
        mBinding.languageRadio.setOptions(speechServicesNames.toArray(new String[0]));

        String serviceId = SettingsStore.getInstance(getContext()).getVoiceSearchService();
        mBinding.languageRadio.setOnCheckedChangeListener(mServiceListener);
        setService(mSpeechServices.indexOf(serviceId), false);
    }

    @Override
    protected boolean reset() {
        setService(mSpeechServices.indexOf(SpeechServices.DEFAULT), true);
        return false;
    }

    private RadioGroupSetting.OnCheckedChangeListener mServiceListener = (radioGroup, checkedId, doApply) -> {
        String serviceId = mSpeechServices.get(mBinding.languageRadio.getCheckedRadioButtonId());
        String currentServiceId = SettingsStore.getInstance(getContext()).getVoiceSearchService();

        if (!Objects.equals(serviceId, currentServiceId)) {
            setService(checkedId, true);
        }
    };

    private OnClickListener mResetListener = (view) -> reset();

    private void setService(int checkedId, boolean doApply) {
        mBinding.languageRadio.setOnCheckedChangeListener(null);
        mBinding.languageRadio.setChecked(checkedId, doApply);
        mBinding.languageRadio.setOnCheckedChangeListener(mServiceListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setVoiceSearchService(mSpeechServices.get(checkedId));
            // Changing the speech service resets the language
            LocaleUtils.setVoiceSearchLanguageId(getContext(), LocaleUtils.DEFAULT_LANGUAGE_ID);
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
