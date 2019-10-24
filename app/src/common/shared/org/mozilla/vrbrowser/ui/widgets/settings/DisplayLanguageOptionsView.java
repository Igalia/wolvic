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
import org.mozilla.vrbrowser.databinding.OptionsLanguageDisplayBinding;
import org.mozilla.vrbrowser.ui.views.settings.RadioGroupSetting;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.LocaleUtils;

class DisplayLanguageOptionsView extends SettingsView {

    private OptionsLanguageDisplayBinding mBinding;

    public DisplayLanguageOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
        super(aContext, aWidgetManager);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_language_display, this, true);

        mScrollbar = mBinding.scrollbar;

        // Header
        mBinding.headerLayout.setBackClickListener(view -> {
            mDelegate.showView(new LanguageOptionsView(getContext(), mWidgetManager));
        });
        mBinding.headerLayout.setHelpClickListener(view -> {
            SessionStore.get().getActiveSession().loadUri(getResources().getString(R.string.sumo_language_display_url));
            mDelegate.exitWholeSettings();
        });

        // Footer
        mBinding.footerLayout.setResetClickListener(mResetListener);

        String language = LocaleUtils.getDisplayLocale(getContext());
        mBinding.languageRadio.setOnCheckedChangeListener(mLanguageListener);
        setLanguage(mBinding.languageRadio.getIdForValue(language), false);
    }

    @Override
    protected boolean reset() {
        String systemLocale = LocaleUtils.getSystemLocale();
        String currentLocale = LocaleUtils.getCurrentLocale();
        if (!currentLocale.equalsIgnoreCase(systemLocale)) {
            setLanguage(mBinding.languageRadio.getIdForValue(systemLocale), true);
            return true;

        } else {
            setLanguage(mBinding.languageRadio.getIdForValue(systemLocale), false);
            return false;
        }
    }

    private RadioGroupSetting.OnCheckedChangeListener mLanguageListener = (radioGroup, checkedId, doApply) -> {
        String currentLocale = LocaleUtils.getCurrentLocale();
        String locale = mBinding.languageRadio.getValueForId(mBinding.languageRadio.getCheckedRadioButtonId()).toString();

        if (!locale.equalsIgnoreCase(currentLocale)) {
            setLanguage(checkedId, true);
        }
    };

    private OnClickListener mResetListener = (view) -> {
        reset();
    };

    private void setLanguage(int checkedId, boolean doApply) {
        mBinding.languageRadio.setOnCheckedChangeListener(null);
        mBinding.languageRadio.setChecked(checkedId, doApply);
        mBinding.languageRadio.setOnCheckedChangeListener(mLanguageListener);

        if (doApply) {
            String language = mBinding.languageRadio.getValueForId(checkedId).toString();
            LocaleUtils.setDisplayLocale(getContext(), language);

            if (mDelegate != null) {
                mDelegate.showRestartDialog();
            }
        }
    }

    @Override
    public Point getDimensions() {
        return new Point( WidgetPlacement.dpDimension(getContext(), R.dimen.language_options_width),
                WidgetPlacement.dpDimension(getContext(), R.dimen.language_options_height));
    }
}
