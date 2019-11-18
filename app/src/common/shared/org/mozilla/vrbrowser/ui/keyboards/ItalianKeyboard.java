package org.mozilla.vrbrowser.ui.keyboards;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.input.CustomKeyboard;
import org.mozilla.vrbrowser.utils.StringUtils;

import java.util.Locale;

public class ItalianKeyboard extends BaseKeyboard {
    private CustomKeyboard mKeyboard;

    public ItalianKeyboard(Context aContext) {
        super(aContext);
    }

    @NonNull
    @Override
    public CustomKeyboard getAlphabeticKeyboard() {
        if (mKeyboard == null) {
            mKeyboard = new CustomKeyboard(mContext.getApplicationContext(), R.xml.keyboard_qwerty_italian);
        }
        return mKeyboard;
    }

    @Nullable
    @Override
    public CandidatesResult getCandidates(String aText) {
        return null;
    }

    @Override
    public String getKeyboardTitle() {
        return StringUtils.getStringByLocale(mContext, R.string.settings_language_italian, getLocale());
    }

    @Override
    public Locale getLocale() {
        return Locale.ITALIAN;
    }

    @Override
    public String getSpaceKeyText(String aComposingText) {
        return StringUtils.getStringByLocale(mContext, R.string.settings_language_italian, getLocale());
    }

    @Override
    public String[] getDomains(String... domains) {
        return super.getDomains(".it");
    }
}
