/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.databinding.OptionsLanguageBinding;
import org.mozilla.vrbrowser.ui.adapters.Language;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.LocaleUtils;

import java.util.List;

class LanguageOptionsView extends SettingsView {

    private SharedPreferences mPrefs;
    private OptionsLanguageBinding mBinding;
    private SettingsView mContentLanguage;
    private SettingsView mVoiceLanguage;
    private SettingsView mDisplayLanguage;

    public LanguageOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
        super(aContext, aWidgetManager);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_language, this, true);

        // Header
        mBinding.headerLayout.setBackClickListener(view -> onDismiss());

        // Footer
        mBinding.footerLayout.setFooterButtonClickListener(mResetListener);

        // Set listeners
        mBinding.setContentClickListener(mContentListener);
        mBinding.setDisplayClickListener(mDisplayListener);
        mBinding.setVoiceSearchClickListener(mVoiceSearchListener);

        // Set descriptions
        setVoiceLanguage();
        setContentLanguage();
        setDisplayLanguage();

        mContentLanguage = new ContentLanguageOptionsView(getContext(), mWidgetManager);
        mVoiceLanguage = new VoiceSearchLanguageOptionsView(getContext(), mWidgetManager);
        mDisplayLanguage = new DisplayLanguageOptionsView(getContext(), mWidgetManager);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(aContext);
    }

    @Override
    public void onShown() {
        super.onShown();

        mPrefs.registerOnSharedPreferenceChangeListener(mPreferencesListener);
    }

    @Override
    protected void onDismiss() {
        super.onDismiss();

        mPrefs.unregisterOnSharedPreferenceChangeListener(mPreferencesListener);
    }

    private OnClickListener mResetListener = (view) -> {
        if (mContentLanguage.reset() |
            mDisplayLanguage.reset() |
            mVoiceLanguage.reset())
            showRestartDialog();
    };

    private void setVoiceLanguage() {
        mBinding.voiceSearchLanguageDescription.setText(getSpannedLanguageText(LocaleUtils.getVoiceSearchLanguage(getContext()).getName()),  TextView.BufferType.SPANNABLE);
    }

    private void setContentLanguage() {
        List<Language> preferredLanguages = LocaleUtils.getPreferredLanguages(getContext());
        String text = "";
        if (preferredLanguages.size() > 0) {
            text = preferredLanguages.get(0).getName();
        }
        mBinding.contentLanguageDescription.setText(getSpannedLanguageText(text));
    }

    private void setDisplayLanguage() {
        mBinding.displayLanguageDescription.setText(getSpannedLanguageText(LocaleUtils.getDisplayLanguage(getContext()).getName()));
    }

    private int getLanguageIndex(@NonNull String text) {
        if (text.contains("(")) {
            return text.indexOf("(");
        }

        if (text.contains("[")) {
            return text.indexOf("[");
        }

        return text.length() - 1;
    }

    private SpannableStringBuilder getSpannedLanguageText(@NonNull String language) {
        int end = getLanguageIndex(language);
        SpannableStringBuilder spanned = new SpannableStringBuilder(language);
        if (end > 0) {
            spanned.setSpan(new StyleSpan(Typeface.BOLD), 0, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return spanned;
    }

    private OnClickListener mContentListener = v -> mDelegate.showView(mContentLanguage);

    private OnClickListener mVoiceSearchListener = v -> mDelegate.showView(mVoiceLanguage);

    private OnClickListener mDisplayListener = v -> mDelegate.showView(mDisplayLanguage);

    private SharedPreferences.OnSharedPreferenceChangeListener mPreferencesListener = (sharedPreferences, key) -> {
        if (key.equals(getContext().getString(R.string.settings_key_content_languages))) {
            setContentLanguage();

        } else if (key.equals(getContext().getString(R.string.settings_key_voice_search_language))) {
            setVoiceLanguage();

        } else if(key.equals(getContext().getString(R.string.settings_key_display_language))) {
            setDisplayLanguage();
        }
    };

    @Override
    public Point getDimensions() {
        return new Point( WidgetPlacement.dpDimension(getContext(), R.dimen.settings_dialog_width),
                WidgetPlacement.dpDimension(getContext(), R.dimen.settings_dialog_height));
    }

}
