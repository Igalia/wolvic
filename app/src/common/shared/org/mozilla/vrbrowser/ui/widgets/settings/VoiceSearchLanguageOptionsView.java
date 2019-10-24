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
        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_language_voice, this, true);

        mScrollbar = mBinding.scrollbar;

        // Header
        mBinding.headerLayout.setBackClickListener(view -> {
            mDelegate.showView(new LanguageOptionsView(getContext(), mWidgetManager));
        });
        mBinding.headerLayout.setHelpClickListener(view -> {
            SessionStore.get().getActiveSession().loadUri(getResources().getString(R.string.sumo_language_voice_url));
            mDelegate.exitWholeSettings();
        });

        // Footer
        mBinding.footerLayout.setResetClickListener(mResetListener);

        String language = LocaleUtils.getVoiceSearchLocale(getContext());
        mBinding.languageRadio.setOnCheckedChangeListener(mLanguageListener);
        setLanguage(mBinding.languageRadio.getIdForValue(language), false);
    }

    @Override
    protected boolean reset() {
        String value = mBinding.languageRadio.getValueForId(mBinding.languageRadio.getCheckedRadioButtonId()).toString();
        if (!value.equals(LocaleUtils.getSystemLocale())) {
            setLanguage(mBinding.languageRadio.getIdForValue(LocaleUtils.getSystemLocale()), true);
        }

        return false;
    }

    private RadioGroupSetting.OnCheckedChangeListener mLanguageListener = (radioGroup, checkedId, doApply) -> {
        setLanguage(checkedId, true);
    };

    private OnClickListener mResetListener = (view) -> {
        reset();
    };

    private void setLanguage(int checkedId, boolean doApply) {
        mBinding.languageRadio.setOnCheckedChangeListener(null);
        mBinding.languageRadio.setChecked(checkedId, doApply);
        mBinding.languageRadio.setOnCheckedChangeListener(mLanguageListener);

        LocaleUtils.setVoiceSearchLocale(getContext(), mBinding.languageRadio.getValueForId(checkedId).toString());
    }

    @Override
    public Point getDimensions() {
        return new Point( WidgetPlacement.dpDimension(getContext(), R.dimen.language_options_width),
                WidgetPlacement.dpDimension(getContext(), R.dimen.language_options_height));
    }
}
