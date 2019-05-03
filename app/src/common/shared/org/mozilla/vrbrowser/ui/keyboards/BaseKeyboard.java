package org.mozilla.vrbrowser.ui.keyboards;

import android.content.Context;
import android.view.inputmethod.EditorInfo;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.utils.StringUtils;

import java.util.Locale;

public abstract class BaseKeyboard implements KeyboardInterface {
    protected Context mContext;
    BaseKeyboard(Context aContext) {
        mContext = aContext;
    }

    @Override
    public String getEnterKeyText(int aIMEOptions, String aComposingText) {
        Locale locale = getLocale();
        switch (aIMEOptions & (EditorInfo.IME_MASK_ACTION | EditorInfo.IME_FLAG_NO_ENTER_ACTION)) {
            case EditorInfo.IME_ACTION_GO:
                return StringUtils.getStringByLocale(mContext, R.string.keyboard_go_label, locale);
            case EditorInfo.IME_ACTION_NEXT:
                return StringUtils.getStringByLocale(mContext, R.string.keyboard_next_label, locale);
            case EditorInfo.IME_ACTION_SEARCH:
                return StringUtils.getStringByLocale(mContext, R.string.keyboard_search_label, locale);
            case EditorInfo.IME_ACTION_SEND:
                return StringUtils.getStringByLocale(mContext, R.string.keyboard_send_label, locale);
            default:
                return StringUtils.getStringByLocale(mContext, R.string.keyboard_enter_label, locale);
        }
    }
}
