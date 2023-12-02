package com.igalia.wolvic.ui.keyboards;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.R;
import com.igalia.wolvic.input.CustomKeyboard;
import com.igalia.wolvic.utils.StringUtils;

import java.util.Locale;

public class FrenchKeyboard extends BaseLatinKeyboard {
    private CustomKeyboard mKeyboard;

    public FrenchKeyboard(Context aContext) {
        super(aContext);
    }

    @NonNull
    @Override
    public CustomKeyboard getAlphabeticKeyboard() {
        if (mKeyboard == null) {
            mKeyboard = new CustomKeyboard(mContext.getApplicationContext(), R.xml.keyboard_qwerty_french);
            loadDatabase();
        }
        return mKeyboard;
    }

    @Override
    public String getKeyboardTitle() {
        return StringUtils.getStringByLocale(mContext, R.string.settings_language_french, getLocale());
    }

    @Override
    public Locale getLocale() {
        return Locale.FRENCH;
    }

    @Override
    public String getSpaceKeyText(String aComposingText) {
        return StringUtils.getStringByLocale(mContext, R.string.settings_language_french, getLocale());
    }

    @Override
    public String[] getDomains(String... domains) {
        return super.getDomains(".fr");
    }
}
