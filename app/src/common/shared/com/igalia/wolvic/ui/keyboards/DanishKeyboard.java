package com.igalia.wolvic.ui.keyboards;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.R;
import com.igalia.wolvic.input.CustomKeyboard;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.utils.StringUtils;

import java.util.Locale;

public class DanishKeyboard extends BaseLatinKeyboard {
    private CustomKeyboard mKeyboard;
    private CustomKeyboard mSymbolsKeyboard;
    private final Locale mLocale;

    public DanishKeyboard(Context aContext) {
        super(aContext);
        mLocale = new Locale("da", "DK");
    }

    @NonNull
    @Override
    public CustomKeyboard getAlphabeticKeyboard() {
        if (mKeyboard == null) {
            mKeyboard = new CustomKeyboard(mContext.getApplicationContext(), R.xml.keyboard_qwerty_danish);
            loadDatabase();
        }
        return mKeyboard;
    }

    @Nullable
    @Override
    public CustomKeyboard getSymbolsKeyboard() {
        if (mSymbolsKeyboard == null) {
            mSymbolsKeyboard = new CustomKeyboard(mContext.getApplicationContext(), R.xml.keyboard_symbols_danish);
        }
        return mSymbolsKeyboard;
    }

    @Override
    public float getAlphabeticKeyboardWidth() {
        return WidgetPlacement.dpDimension(mContext, R.dimen.keyboard_alphabetic_width_danish);
    }

    @Override
    public String getKeyboardTitle() {
        return StringUtils.getStringByLocale(mContext, R.string.settings_language_danish, getLocale());
    }

    @Override
    public Locale getLocale() {
        return mLocale;
    }

    @Override
    public String getSpaceKeyText(String aComposingText) {
        return StringUtils.getStringByLocale(mContext, R.string.settings_language_danish, getLocale());
    }

    @Override
    public String[] getDomains(String... domains) {
        return super.getDomains(".dk");
    }
}
