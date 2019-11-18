package org.mozilla.vrbrowser.ui.keyboards;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.input.CustomKeyboard;
import org.mozilla.vrbrowser.utils.StringUtils;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import jp.co.omronsoft.openwnn.ComposingText;
import jp.co.omronsoft.openwnn.JAJP.OpenWnnEngineJAJP;
import jp.co.omronsoft.openwnn.JAJP.Romkan;
import jp.co.omronsoft.openwnn.LetterConverter;
import jp.co.omronsoft.openwnn.StrSegment;
import jp.co.omronsoft.openwnn.SymbolList;
import jp.co.omronsoft.openwnn.WnnEngine;
import jp.co.omronsoft.openwnn.WnnWord;

public class JapaneseKeyboard extends BaseKeyboard {

    private static final String LOGTAG = SystemUtils.createLogtag(JapaneseKeyboard.class);

    private CustomKeyboard mKeyboard;
    private CustomKeyboard mSymbolsKeyboard;
    private List<Character> mAutocompleteEndings = Arrays.asList(
            ' ', '、', '。','!','?','ー'
    );

    private SymbolList mSymbolsConverter;

    /** OpenWnn dictionary */
    private WnnEngine mConverter;

    /** Pre-converter (for Romaji-to-Kana input, Hangul input, etc.) */
    protected LetterConverter mPreConverter;

    /** The inputing/editing string */
    protected ComposingText  mComposingText;


    public JapaneseKeyboard(Context aContext) {
        super(aContext);

        mConverter = new OpenWnnEngineJAJP();
        ((OpenWnnEngineJAJP) mConverter).setKeyboardType(OpenWnnEngineJAJP.KEYBOARD_QWERTY);
        ((OpenWnnEngineJAJP) mConverter).setDictionary(OpenWnnEngineJAJP.DIC_LANG_JP);
        mConverter.init();

        mPreConverter = new Romkan();
        mComposingText = new ComposingText();
    }

    @NonNull
    @Override
    public CustomKeyboard getAlphabeticKeyboard() {
        if (mKeyboard == null) {
            mKeyboard = new CustomKeyboard(mContext.getApplicationContext(), R.xml.keyboard_qwerty_japanese);
        }

        return mKeyboard;
    }

    @Nullable
    @Override
    public CustomKeyboard getSymbolsKeyboard() {
        if (mSymbolsKeyboard == null) {
            mSymbolsKeyboard = new CustomKeyboard(mContext.getApplicationContext(), R.xml.keyboard_symbols_japanese);

            mSymbolsConverter = new SymbolList(mContext, SymbolList.LANG_JA);
        }
        return mSymbolsKeyboard;
    }

    @Nullable
    @Override
    public CandidatesResult getCandidates(String aComposingText) {
        if (StringUtils.isEmpty(aComposingText)) {
            mComposingText.clear();
            return null;
        }

        // Autocomplete when special characters are clicked
        char lastChar = aComposingText.charAt(aComposingText.length() - 1);
        boolean autocompose = mAutocompleteEndings.indexOf(lastChar) >= 0;

        aComposingText = aComposingText.replaceAll("\\s","");
        if (aComposingText.isEmpty()) {
            return null;
        }

        initializeComposingText(aComposingText);

        List<Words> words = new ArrayList<>();
        int candidates = mConverter.predict(mComposingText, 0, -1);
        if (candidates > 0) {
            WnnWord word;
            while ((word = mConverter.getNextCandidate()) != null) {
                words.add(new Words(1, word.stroke, word.candidate));
            }
        }

        CandidatesResult result = new CandidatesResult();
        result.words = words;

        if (autocompose) {
            result.action = CandidatesResult.Action.AUTO_COMPOSE;
            result.composing = aComposingText;

            mComposingText.clear();

        } else {
            result.action = CandidatesResult.Action.SHOW_CANDIDATES;
            result.composing = mComposingText.toString(ComposingText.LAYER2);
        }

        return result;
    }

    @Override
    public CandidatesResult getEmojiCandidates(String aComposingText) {
        ComposingText text = new ComposingText();
        mSymbolsConverter.convert(text);

        List<Words> words = new ArrayList<>();
        int candidates = mSymbolsConverter.predict(mComposingText, 0, -1);
        if (candidates > 0) {
            WnnWord word;
            while ((word = mSymbolsConverter.getNextCandidate()) != null) {
                words.add(new Words(1, word.stroke, word.candidate));
            }
        }

        CandidatesResult result = new CandidatesResult();
        result.words = words;
        result.action = CandidatesResult.Action.SHOW_CANDIDATES;
        result.composing = aComposingText;

        return result;
    }

    @Override
    public String getComposingText(String aComposing, String aCode) {
        return "";
    }

    private void initializeComposingText(String text) {
        mComposingText.clear();
        for (int i=0; i<text.length(); i++) {
            mComposingText.insertStrSegment(ComposingText.LAYER0, ComposingText.LAYER1, new StrSegment(text.substring(i, i+1)));
            mPreConverter.convert(mComposingText);
        }
        mComposingText.debugout();
    }

    @Override
    public boolean supportsAutoCompletion() {
        return true;
    }

    @Override
    public boolean usesComposingText() {
        return true;
    }

    @Override
    public String getKeyboardTitle() {
        return StringUtils.getStringByLocale(mContext, R.string.settings_language_japanese, getLocale());
    }

    @Override
    public Locale getLocale() {
        return Locale.JAPAN;
    }

    @Override
    public String getSpaceKeyText(String aComposingText) {
        if (aComposingText == null || aComposingText.trim().isEmpty()) {
            return StringUtils.getStringByLocale(mContext, R.string.settings_language_japanese, getLocale());
        } else {
            return mContext.getString(R.string.japanese_spacebar_selection);
        }
    }

    @Override
    public String getEnterKeyText(int aIMEOptions, String aComposingText) {
        if (aComposingText == null || aComposingText.trim().isEmpty()) {
            return super.getEnterKeyText(aIMEOptions, aComposingText);
        } else {
            return mContext.getString(R.string.japanese_enter_completion);
        }
    }

    @Override
    public String getModeChangeKeyText() {
        return mContext.getString(R.string.japanese_keyboard_mode_change);
    }

    @Override
    public void clear() {
        mConverter.init();
    }

    @Override
    public String[] getDomains(String... domains) {
        return super.getDomains(".jp");
    }

}
