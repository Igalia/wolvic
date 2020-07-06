package org.mozilla.vrbrowser.ui.keyboards;

import android.content.Context;
import android.view.inputmethod.EditorInfo;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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

    @Override
    public String getSpaceKeyText(String aComposingText) {
        return StringUtils.getStringByLocale(mContext, R.string.keyboard_space_label, getLocale()).toUpperCase();
    }

    @Override
    public String getComposingText(String aComposing, String aCode) {
        return aComposing.replaceFirst(Pattern.quote(aCode), "");
    }

    @Override
    public String getModeChangeKeyText() {
        return mContext.getString(R.string.keyboard_mode_change);
    }

    @Override
    public float getKeyboardTranslateYInWorld() {
        return WidgetPlacement.unitFromMeters(mContext, R.dimen.keyboard_y);
    }

    @Override
    public float getKeyboardWorldWidth() {
        return WidgetPlacement.floatDimension(mContext, R.dimen.keyboard_world_width);
    }

    public float getAlphabeticKeyboardWidth() {
        return WidgetPlacement.dpDimension(mContext, R.dimen.keyboard_alphabetic_width);
    }

    public float  getAlphabeticKeyboardHeight() {
        return WidgetPlacement.dpDimension(mContext, R.dimen.keyboard_height);
    }

    public float  getSymbolKeyboardHeight() {
        return WidgetPlacement.dpDimension(mContext, R.dimen.keyboard_height);
    }

    @Override
    public String[] getDomains(String... domains) {
        return Stream.of(new String[]{".com", ".net", ".org", ".co"}, domains).flatMap(Stream::of)
                .toArray(String[]::new);
    }
}
