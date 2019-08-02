/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.settings;

import android.content.Context;
import android.graphics.Point;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.ui.views.settings.RadioGroupSetting;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.LocaleUtils;

class VoiceSearchLanguageOptionsView extends SettingsView {

    private RadioGroupSetting mLanguage;

    public VoiceSearchLanguageOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
        super(aContext, aWidgetManager);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.options_language_voice, this);

        // Header
        SettingsHeader header = findViewById(R.id.header_layout);
        header.setBackClickListener(view -> {
            mDelegate.showView(new LanguageOptionsView(getContext(), mWidgetManager));
        });
        header.setHelpClickListener(view -> {
            SessionStore.get().getActiveStore().loadUri(getResources().getString(R.string.sumo_language_voice_url));
            mDelegate.exitWholeSettings();
        });

        // Footer
        SettingsFooter footer = findViewById(R.id.footer_layout);
        footer.setResetClickListener(mResetListener);

        String language = LocaleUtils.getVoiceSearchLocale(getContext());
        mLanguage = findViewById(R.id.languageRadio);
        mLanguage.setOnCheckedChangeListener(mLanguageListener);
        setLanguage(mLanguage.getIdForValue(language), false);
    }

    @Override
    protected boolean reset() {
        String value = mLanguage.getValueForId(mLanguage.getCheckedRadioButtonId()).toString();
        if (!value.equals(LocaleUtils.getSystemLocale())) {
            setLanguage(mLanguage.getIdForValue(LocaleUtils.getSystemLocale()), true);
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
        mLanguage.setOnCheckedChangeListener(null);
        mLanguage.setChecked(checkedId, doApply);
        mLanguage.setOnCheckedChangeListener(mLanguageListener);

        LocaleUtils.setVoiceSearchLocale(getContext(), mLanguage.getValueForId(checkedId).toString());
    }

    @Override
    public Point getDimensions() {
        return new Point( WidgetPlacement.dpDimension(getContext(), R.dimen.language_options_width),
                WidgetPlacement.dpDimension(getContext(), R.dimen.language_options_height));
    }
}
