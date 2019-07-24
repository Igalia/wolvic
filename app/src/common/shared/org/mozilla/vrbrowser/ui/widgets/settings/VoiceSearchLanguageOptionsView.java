/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.settings;

import android.content.Context;
import android.graphics.Point;
import android.view.View;
import android.widget.ScrollView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.ui.views.UIButton;
import org.mozilla.vrbrowser.ui.views.settings.ButtonSetting;
import org.mozilla.vrbrowser.ui.views.settings.RadioGroupSetting;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.LocaleUtils;

class VoiceSearchLanguageOptionsView extends SettingsView {
    private AudioEngine mAudio;
    private UIButton mBackButton;
    private RadioGroupSetting mLanguage;
    private ButtonSetting mResetButton;
    private ScrollView mScrollbar;

    public VoiceSearchLanguageOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
        super(aContext, aWidgetManager);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.options_language, this);

        mAudio = AudioEngine.fromContext(aContext);

        mScrollbar = findViewById(R.id.scrollbar);

        mBackButton = findViewById(R.id.backButton);
        mBackButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            onDismiss();
        });

        String language = SettingsStore.getInstance(getContext()).getVoiceSearchLanguage();
        mLanguage = findViewById(R.id.languageRadio);
        mLanguage.setOnCheckedChangeListener(mLanguageListener);
        setLanguage(mLanguage.getIdForValue(language), false);

        mResetButton = findViewById(R.id.resetButton);
        mResetButton.setOnClickListener(mResetListener);

        mScrollbar = findViewById(R.id.scrollbar);
    }

    private RadioGroupSetting.OnCheckedChangeListener mLanguageListener = (radioGroup, checkedId, doApply) -> {
        setLanguage(checkedId, true);
    };

    private OnClickListener mResetListener = (view) -> {
        String value = mLanguage.getValueForId(mLanguage.getCheckedRadioButtonId()).toString();
        if (!value.equals(LocaleUtils.getCurrentLocale())) {
            setLanguage(mLanguage.getIdForValue(LocaleUtils.getCurrentLocale()), true);
        }
    };

    private void setLanguage(int checkedId, boolean doApply) {
        mLanguage.setOnCheckedChangeListener(null);
        mLanguage.setChecked(checkedId, doApply);
        mLanguage.setOnCheckedChangeListener(mLanguageListener);

        SettingsStore.getInstance(getContext()).setVoiceSearchLanguage(mLanguage.getValueForId(checkedId).toString());
    }

    @Override
    public Point getDimensions() {
        return new Point( WidgetPlacement.dpDimension(getContext(), R.dimen.language_options_width),
                WidgetPlacement.dpDimension(getContext(), R.dimen.language_options_height));
    }
}
