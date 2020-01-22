package org.mozilla.vrbrowser.ui.keyboards;

import org.mozilla.vrbrowser.input.CustomKeyboard;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface KeyboardInterface {
    class Words {
        public int syllable;
        public String code;
        public String value;

        public Words(int aSyllable, String aCode, String aValue) {
            syllable = aSyllable;
            code = aCode;
            value = aValue;
        }
    }
    class CandidatesResult {
        public enum Action {
            SHOW_CANDIDATES,
            AUTO_COMPOSE
        }
        public List<Words> words;
        public Action action = Action.SHOW_CANDIDATES;
        public String composing;
    }
    @NonNull CustomKeyboard getAlphabeticKeyboard();
    float getAlphabeticKeyboardWidth();
    default @Nullable CustomKeyboard getSymbolsKeyboard() { return null; }
    default @Nullable CandidatesResult getCandidates(String aComposingText) { return null; }
    default @Nullable String overrideAddText(String aTextBeforeCursor, String aNextText) { return null; }
    default @Nullable String overrideBackspace(String aTextBeforeCursor) { return null; }
    default @Nullable CandidatesResult getEmojiCandidates(String aComposingText) { return null; }
    default boolean supportsAutoCompletion() { return false; }
    default boolean usesComposingText() { return false; }
    default boolean usesTextOverride() { return false; }
    String getComposingText(String aComposing, String aCode);
    String getKeyboardTitle();
    default String[] getDomains(String... domains) { return null; }
    Locale getLocale();
    String getSpaceKeyText(String aComposingText);
    String getEnterKeyText(int aIMEOptions, String aComposingText);
    String getModeChangeKeyText();
    default @Nullable void clear() {}
}