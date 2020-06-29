/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.keyboards;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.input.CustomKeyboard;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.StringUtils;

import java.util.Locale;

public class ThaiKeyboard extends BaseKeyboard {
    private Locale mLocale;
    private CustomKeyboard mKeyboard;
    private CustomKeyboard mCapKeyboard;
    private CustomKeyboard mSymbolsKeyboard;

    public ThaiKeyboard(Context aContext) {
        super(aContext);
        mLocale = new Locale("th", "TH");
    }

    @NonNull
    @Override
    public CustomKeyboard getAlphabeticKeyboard() {
        if (mKeyboard == null) {
            mKeyboard = new CustomKeyboard(mContext.getApplicationContext(), R.xml.keyboard_qwerty_thai);
            mKeyboard.setShifted(false);
        }
        return mKeyboard;
    }

    @NonNull
    @Override
    public CustomKeyboard getAlphabeticCapKeyboard() {
        if (mCapKeyboard == null) {
            mCapKeyboard = new CustomKeyboard(mContext.getApplicationContext(), R.xml.keyboard_qwerty_thai_cap);
            mCapKeyboard.setShifted(true);
        }
        return mCapKeyboard;
    }

    @Nullable
    @Override
    public CustomKeyboard getSymbolsKeyboard() {
        if (mSymbolsKeyboard == null) {
            mSymbolsKeyboard = new CustomKeyboard(mContext.getApplicationContext(), R.xml.keyboard_symbols_thai);
        }
        return mSymbolsKeyboard;
    }

    @Override
    public float getKeyboardTranslateYInWorld() {
        return WidgetPlacement.unitFromMeters(mContext, R.dimen.keyboard_y_extra_row);
    }

    @Override
    public float getKeyboardWorldWidth() {
        return WidgetPlacement.floatDimension(mContext, R.dimen.keyboard_world_extra_row_width);
    }

    @Override
    public float getAlphabeticKeyboardWidth() {
        return WidgetPlacement.dpDimension(mContext, R.dimen.keyboard_alphabetic_width_extra_column);
    }

    @Override
    public float getAlphabeticKeyboardHeight() {
        return WidgetPlacement.dpDimension(mContext, R.dimen.keyboard_alphabetic_height_thai);
    }

    @Nullable
    @Override
    public CandidatesResult getCandidates(String aText) {
        return null;
    }

    @Override
    public String getKeyboardTitle() {
        return StringUtils.getStringByLocale(mContext, R.string.settings_language_thai, getLocale());
    }

    @Override
    public Locale getLocale() {
        return mLocale;
    }

    @Override
    public String getSpaceKeyText(String aComposingText) {
        return StringUtils.getStringByLocale(mContext, R.string.settings_language_thai, getLocale());
    }

    @Override
    public String getModeChangeKeyText() {
        return mContext.getString(R.string.thai_keyboard_mode_change);
    }

    @Override
    public String[] getDomains(String... domains) {
        return super.getDomains(".th");
    }
}
