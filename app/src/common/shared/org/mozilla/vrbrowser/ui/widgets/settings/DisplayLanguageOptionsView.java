/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.settings;

import android.content.Context;
import android.graphics.Point;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.SessionStore;
import org.mozilla.vrbrowser.ui.views.settings.RadioGroupSetting;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.LocaleUtils;

class DisplayLanguageOptionsView extends SettingsView {

    private RadioGroupSetting mLanguage;

    public DisplayLanguageOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
        super(aContext, aWidgetManager);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.options_language_display, this);

        // Header
        SettingsHeader header = findViewById(R.id.header_layout);
        header.setBackClickListener(view -> {
            mDelegate.showView(new LanguageOptionsView(getContext(), mWidgetManager));
        });
        header.setHelpClickListener(view -> {
            SessionStore.get().loadUri(getResources().getString(R.string.sumo_language_display_url));
            mDelegate.exitWholeSettings();
        });

        // Footer
        SettingsFooter footer = findViewById(R.id.footer_layout);
        footer.setResetClickListener(mResetListener);

        String language = LocaleUtils.getDisplayLocale(getContext());
        mLanguage = findViewById(R.id.languageRadio);
        mLanguage.setOnCheckedChangeListener(mLanguageListener);
        setLanguage(mLanguage.getIdForValue(language), false);
    }

    @Override
    protected boolean reset() {
        String systemLocale = LocaleUtils.getSystemLocale();
        String currentLocale = LocaleUtils.getCurrentLocale();
        if (!currentLocale.equalsIgnoreCase(systemLocale)) {
            setLanguage(mLanguage.getIdForValue(systemLocale), true);
            return true;

        } else {
            setLanguage(mLanguage.getIdForValue(systemLocale), false);
            return false;
        }
    }

    private RadioGroupSetting.OnCheckedChangeListener mLanguageListener = (radioGroup, checkedId, doApply) -> {
        String currentLocale = LocaleUtils.getCurrentLocale();
        String locale = mLanguage.getValueForId(mLanguage.getCheckedRadioButtonId()).toString();

        if (!locale.equalsIgnoreCase(currentLocale))
            setLanguage(checkedId, true);
    };

    private OnClickListener mResetListener = (view) -> {
        reset();
    };

    private void setLanguage(int checkedId, boolean doApply) {
        mLanguage.setOnCheckedChangeListener(null);
        mLanguage.setChecked(checkedId, doApply);
        mLanguage.setOnCheckedChangeListener(mLanguageListener);

        if (doApply) {
            String language = mLanguage.getValueForId(checkedId).toString();
            LocaleUtils.setDisplayLocale(getContext(), language);

            if (mDelegate != null)
                mDelegate.showRestartDialog();
        }
    }

    @Override
    public Point getDimensions() {
        return new Point( WidgetPlacement.dpDimension(getContext(), R.dimen.language_options_width),
                WidgetPlacement.dpDimension(getContext(), R.dimen.language_options_height));
    }
}
