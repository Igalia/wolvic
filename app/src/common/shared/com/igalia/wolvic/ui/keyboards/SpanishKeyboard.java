package com.igalia.wolvic.ui.keyboards;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.R;
import com.igalia.wolvic.input.CustomKeyboard;
import com.igalia.wolvic.utils.StringUtils;

import java.util.Locale;

public class SpanishKeyboard extends BaseLatinKeyboard {
    private final Locale mSpanishLocale = new Locale("es", "");
    private CustomKeyboard mKeyboard;

    public SpanishKeyboard(Context aContext) {
        super(aContext);
    }

    @NonNull
    @Override
    public CustomKeyboard getAlphabeticKeyboard() {
        if (mKeyboard == null) {
            mKeyboard = new CustomKeyboard(mContext.getApplicationContext(), R.xml.keyboard_qwerty_spanish);
            loadDatabase();
        }
        return mKeyboard;
    }

    @Override
    public String getKeyboardTitle() {
        return StringUtils.getStringByLocale(mContext, R.string.settings_language_spanish, getLocale());
    }

    @Override
    public Locale getLocale() {
        return mSpanishLocale;
    }

    @Override
    public String getSpaceKeyText(String aComposingText) {
        return StringUtils.getStringByLocale(mContext, R.string.settings_language_spanish, getLocale());
    }
}
