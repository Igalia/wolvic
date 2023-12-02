package com.igalia.wolvic.ui.keyboards;

import android.content.Context;

import androidx.annotation.NonNull;

import com.igalia.wolvic.R;
import com.igalia.wolvic.input.CustomKeyboard;
import com.igalia.wolvic.utils.StringUtils;

import java.util.Locale;

public class EnglishKeyboard extends BaseLatinKeyboard {
    private CustomKeyboard mKeyboard;

    public EnglishKeyboard(Context aContext) {
        super(aContext);
    }

    @NonNull
    @Override
    public CustomKeyboard getAlphabeticKeyboard() {
        if (mKeyboard == null) {
            mKeyboard = new CustomKeyboard(mContext.getApplicationContext(), R.xml.keyboard_qwerty);
            loadDatabase();
        }
        return mKeyboard;
    }

    @Override
    public String getKeyboardTitle() {
        return StringUtils.getStringByLocale(mContext, R.string.settings_language_english, getLocale());
    }

    @Override
    public Locale getLocale() {
        return Locale.ENGLISH;
    }

    @Override
    public String getSpaceKeyText(String aComposingText) {

        return StringUtils.getStringByLocale(mContext, R.string.settings_language_english, getLocale());
    }

    @Override
    public String[] getDomains(String... domains) {
        return super.getDomains(".uk", ".us");
    }
}
