package org.mozilla.vrbrowser.ui.keyboards;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.input.CustomKeyboard;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.StringUtils;

import java.util.Locale;

public class FinnishKeyboard extends BaseKeyboard {
    private CustomKeyboard mKeyboard;
    private CustomKeyboard mSymbolsKeyboard;
    private Locale mLocale;

    public FinnishKeyboard(Context aContext) {
        super(aContext);
        mLocale = new Locale("fi", "FI");
    }

    @NonNull
    @Override
    public CustomKeyboard getAlphabeticKeyboard() {
        if (mKeyboard == null) {
            mKeyboard = new CustomKeyboard(mContext.getApplicationContext(), R.xml.keyboard_qwerty_finnish);
        }
        return mKeyboard;
    }

    @Nullable
    @Override
    public CustomKeyboard getSymbolsKeyboard() {
        if (mSymbolsKeyboard == null) {
            mSymbolsKeyboard = new CustomKeyboard(mContext.getApplicationContext(), R.xml.keyboard_symbols_finnish);
        }
        return mSymbolsKeyboard;
    }

    @Override
    public float getAlphabeticKeyboardWidth() {
        return WidgetPlacement.dpDimension(mContext, R.dimen.keyboard_alphabetic_width_finnish);
    }

    @Nullable
    @Override
    public CandidatesResult getCandidates(String aText) {
        return null;
    }

    @Override
    public String getKeyboardTitle() {
        return StringUtils.getStringByLocale(mContext, R.string.settings_language_finnish, getLocale());
    }

    @Override
    public Locale getLocale() {
        return mLocale;
    }

    @Override
    public String getSpaceKeyText(String aComposingText) {
        return StringUtils.getStringByLocale(mContext, R.string.settings_language_finnish, getLocale());
    }

    @Override
    public String[] getDomains(String... domains) {
        return super.getDomains(".fi");
    }
}