package org.mozilla.vrbrowser.ui.keyboards;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.input.CustomKeyboard;
import org.mozilla.vrbrowser.utils.StringUtils;

import java.util.ArrayList;
import java.util.Locale;

public class KoreanKeyboard extends BaseKeyboard {
    private CustomKoreanKeyboard mKeyboard;
    /*
     * The Korean Writing System:
     * http://gernot-katzers-spice-pages.com/var/korean_hangul_unicode.html
     * http://www.programminginkorean.com/programming/hangul-in-unicode/composing-syllables-in-unicode/
     */
    private static final String INITIALS = "ᄀㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
    private final static String VOWELS = "ᅡᅢᅣᅤᅥᅦᅧᅨᅩᅪᅫᅬᅭᅮᅯᅰᅱᅲᅳᅴᅵ";
    private final static String TAILS = "ᄀㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ";
    private final static String SINGLE_CONSONANTS = "ㅂㅈㄷᄀㅅ";
    private final static String DOUBLE_CONSONANTS = "ㅃㅉㄸㄲㅆ";
    private final static String SINGLE_VOWELS = "ᅢᅦ";
    private final static String DOUBLE_VOWELS = "ᅤᅨ";
    private final static String[] COMBINED_CONSONANTS = new String[] {
            "ㄳᄀㅅ", "ㄵㄴㅈ", "ㄶㄴㅎ", "ㄺㄹᄀ", "ㄻㄹㅁ", "ㄼㄹㅂ", "ㄽㄹㅅ", "ㄾㄹㅌ", "ㄿㄹㅍ", "ㅀㄹㅎ", "ㅄㅂㅅ"
    };
    private final static String[] COMBINED_VOWELS = new String[] {
            "ᅦᅥᅵ", "ᅪᅩᅡ", "ᅫᅩᅢ", "ᅬᅩᅵ", "ᅯᅮᅥ", "ᅰᅮᅦ", "ᅱᅮᅵ", "ᅴᅳᅵ"
    };
    private final static int HANGUL_UNICODE_START_VALUE = 44032;
    private final static int HANGUL_UNICODE_LAST_VALUE = 55203;
    private final static int AMOUNT_OF_TAILS = TAILS.length() + 1; // 28
    private final static int AMOUNT_OF_TAILS_THE_AMOUNT_OF_VOWELS = AMOUNT_OF_TAILS * VOWELS.length(); // 588


    class DecomposedHangul {
        int initialIndex = -1;
        int vowelIndex = -1;
        int tailIndex = -1;

        boolean isInitial() {
            return initialIndex >= 0 && vowelIndex < 0;
        }

        boolean isVowel() {
            return vowelIndex >= 0 && initialIndex < 0 && tailIndex < 0;
        }

        boolean isTail() {
            return tailIndex >= 0 && vowelIndex < 0;
        }

        boolean isCompleteHangul() {
            return initialIndex >= 0 && vowelIndex >= 0 && tailIndex >= 0;
        }

        boolean isInitialAndVowel() {
            return initialIndex >= 0 && vowelIndex >= 0 && tailIndex < 0;
        }

        boolean combineVowel(String aSufix) {
            if (!isVowel() && !isInitialAndVowel()) {
                return false;
            }
            String prefix = VOWELS.substring(vowelIndex, vowelIndex + 1);
            for (String values : COMBINED_VOWELS) {
                if (values.indexOf(prefix) == 1 && values.indexOf(aSufix) == 2) {
                    vowelIndex = VOWELS.indexOf(values.substring(0, 1));
                    return true;
                }
            }

            return false;
        }

        boolean combineTail(String aSufix) {
            if (!isCompleteHangul()) {
                return false;
            }
            String prefix = TAILS.substring(tailIndex, tailIndex + 1);
            for (String values : COMBINED_CONSONANTS) {
                if (values.indexOf(prefix) == 1 && values.indexOf(aSufix) == 2) {
                    tailIndex = TAILS.indexOf(values.substring(0, 1));
                    return true;
                }
            }

            return false;
        }

        boolean removeCombinedVowel() {
            if (!isVowel() && !isInitialAndVowel()) {
                return false;
            }

            String jamo = VOWELS.substring(vowelIndex, vowelIndex + 1);
            for (String values : COMBINED_VOWELS) {
                if (values.indexOf(jamo) == 0) {
                    vowelIndex = VOWELS.indexOf(values.substring(1, 2));
                    return true;
                }
            }
            return false;
        }

        boolean removeCombinedTail() {
            if (!isCompleteHangul()) {
                return false;
            }

            String jamo = TAILS.substring(tailIndex, tailIndex + 1);
            for (String values : COMBINED_CONSONANTS) {
                if (values.indexOf(jamo) == 0) {
                    tailIndex = TAILS.indexOf(values.substring(1, 2));
                    return true;
                }
            }
            return false;
        }

        String getHangul() {
            if (isVowel()) {
                return VOWELS.substring(vowelIndex, vowelIndex + 1);
            } else if (isInitial()) {
                return INITIALS.substring(initialIndex, initialIndex + 1);
            } else if (isTail()) {
                return TAILS.substring(tailIndex, tailIndex + 1);
            } else if (isInitialAndVowel() || isCompleteHangul()) {
                // Compose Hangul syllable using Unicode math (initial + vowel + tail)
                int charValue = HANGUL_UNICODE_START_VALUE + initialIndex * AMOUNT_OF_TAILS_THE_AMOUNT_OF_VOWELS;
                if (vowelIndex >= 0) {
                    charValue += vowelIndex * AMOUNT_OF_TAILS;
                    if (tailIndex >= 0) {
                        charValue += tailIndex + 1;
                    }
                }

                return new String(Character.toChars(charValue));
            }

            return "";
        }
    }

    class CustomKoreanKeyboard extends CustomKeyboard {
        private ArrayList<Key> mMutableConsonants;
        private ArrayList<Key> mMutableVowels;
        CustomKoreanKeyboard(Context context, int xmlLayoutResId) {
            super(context, xmlLayoutResId);
        }

        @Override
        protected Key createKeyFromXml(Resources res, Row parent, int x, int y, XmlResourceParser parser) {
            if (mMutableConsonants == null) {
                mMutableConsonants = new ArrayList<>();
                mMutableVowels = new ArrayList<>();
            }
            Key key = super.createKeyFromXml(res, parent, x, y, parser);
            if (key != null && !StringUtils.isEmpty(key.label) ) {
                // Consonants that are changed when the keyboard is shifted
                if (SINGLE_CONSONANTS.contains(key.label)) {
                    mMutableConsonants.add(key);
                }
                // Vowels that are changed when the keyboard is shifted
                if (SINGLE_VOWELS.contains(key.label)) {
                    mMutableVowels.add(key);
                }
            }
            return key;
        }


        @Override
        public boolean setShifted(boolean aShifted) {
            boolean result = super.setShifted(aShifted);
            // Update consonants depending on keyboard shift state.
            for (Key key: mMutableConsonants) {
                int index = SINGLE_CONSONANTS.indexOf(key.label.toString());
                if (index < 0) {
                    index = DOUBLE_CONSONANTS.indexOf(key.label.toString());
                }
                key.label = aShifted ? getDoubleConsonant(index) :getSingleConsonant(index);
                key.codes[0] = key.label.charAt(0);
            }
            // Update vowels depending on keyboard shift state.
            for (Key key: mMutableVowels) {
                int index = SINGLE_VOWELS.indexOf(key.label.toString());
                if (index < 0) {
                    index = DOUBLE_VOWELS.indexOf(key.label.toString());
                }
                key.label = aShifted ? DOUBLE_VOWELS.substring(index, index + 1) : SINGLE_VOWELS.substring(index, index + 1);
                key.codes[0] = key.label.charAt(0);
            }

            return result;
        }
    }

    public KoreanKeyboard(Context aContext) {
        super(aContext);
    }

    @NonNull
    @Override
    public CustomKeyboard getAlphabeticKeyboard() {
        if (mKeyboard == null) {
            mKeyboard = new CustomKoreanKeyboard(mContext.getApplicationContext(), R.xml.keyboard_qwerty_korean);
        }
        return mKeyboard;
    }

    @Nullable
    @Override
    public CandidatesResult getCandidates(String aText) {
        return null;
    }

    @Nullable
    @Override
    public String overrideAddText(String aTextBeforeCursor, String aNextText) {
        if (StringUtils.isEmpty(aTextBeforeCursor) || StringUtils.isEmpty(aNextText)) {
            return null;
        }

        DecomposedHangul before = decompose(StringUtils.getLastCharacter(aTextBeforeCursor));
        DecomposedHangul after = decompose(StringUtils.getLastCharacter(aNextText));

        String result = null;

        if (before.isInitial() && after.isInitial() && before.initialIndex == after.initialIndex && SINGLE_CONSONANTS.contains(getInitial(before.initialIndex))) {
            // Generate double consonant from single consonants.
            // Example: ㅅㅅ will convert to ㅆ
            result = StringUtils.removeLastCharacter(aTextBeforeCursor);
            result += getDoubleConsonant(SINGLE_CONSONANTS.indexOf(getInitial(before.initialIndex)));
        } else if (after.isVowel() && before.combineVowel(getVowel(after.vowelIndex))) {
            // Combine vowels.
            // Example: ᅥᅵ will convert to ᅦ
            result = StringUtils.removeLastCharacter(aTextBeforeCursor);
            result += before.getHangul();
        } else if (after.isTail() && before.combineTail(getTail(after.tailIndex))) {
            // Combine tails for complete Hanguls.
            // Example: 식 will convert to 싟 (because the ending jamo tail ᄀㅅ will convert to ㄳ)
            result = StringUtils.removeLastCharacter(aTextBeforeCursor);
            result += before.getHangul();
        } else if (before.isInitial() && after.isVowel()) {
            // Combine initial and vowel.
            // Example: ㅂᅩ  will produce 보
            result = StringUtils.removeLastCharacter(aTextBeforeCursor);
            before.vowelIndex = after.vowelIndex;
            before.tailIndex = -1;
            result += before.getHangul();
        } else if (before.isInitialAndVowel() && after.isTail()) {
            // Add tail to a Hangul with no tail.
            // Example: 보ㅇ  will produce 봉
            result = StringUtils.removeLastCharacter(aTextBeforeCursor);
            before.tailIndex = after.tailIndex;
            result += before.getHangul();
        } else if (before.isCompleteHangul() && after.isVowel() && INITIALS.contains(getTail(before.tailIndex))) {
            // Split Hangul when vowel is added after a complete Hangul.
            // Example: ㅂㅏㅂ  will produce 밥. Another ㅏ will produce 바바 instead of 밥ㅏ
            result = StringUtils.removeLastCharacter(aTextBeforeCursor);
            after.initialIndex = INITIALS.indexOf(getTail(before.tailIndex));
            before.tailIndex = -1;
            result += before.getHangul();
            result += after.getHangul();
        }

        return result;
    }

    @Nullable
    @Override
    public String overrideBackspace(String aTextBeforeCursor) {
        if (StringUtils.isEmpty(aTextBeforeCursor)) {
            return null;
        }

        String result = null;
        DecomposedHangul last = decompose(StringUtils.getLastCharacter(aTextBeforeCursor));
        if (last.removeCombinedTail()) {
            // Remove the combined tail from a Hangul.
            // Example: 싟 will produce 식 (the ㅅ tail is removed from the combined ㄳ tail)
            result = StringUtils.removeLastCharacter(aTextBeforeCursor);
            result += last.getHangul();
        } else if (last.removeCombinedVowel()) {
            // Remove the combined vowel from a Hangul or a jamo.
            // Example: ᅦ will produce ᅥ (The ᅵ jamo was removed)
            result = StringUtils.removeLastCharacter(aTextBeforeCursor);
            result += last.getHangul();
        } else if (last.isCompleteHangul()) {
            // Remove the tail from a Hangul.
            // Example: 봉 will produce 보 (the ㅇ tail is removed)
            last.tailIndex = -1;
            result = StringUtils.removeLastCharacter(aTextBeforeCursor);
            result += last.getHangul();
        } else if (last.isInitialAndVowel()) {
            result = StringUtils.removeLastCharacter(aTextBeforeCursor);
            DecomposedHangul before = decompose(StringUtils.getLastCharacter(result));
            if (before.isInitialAndVowel() && TAILS.contains(getInitial(last.initialIndex))) {
                // Remove the vowel from a Hangul and use the remaining initial as tail for the previous Hangul.
                // Example: 바바 will produce 밥 (The ㅏ vowel is removed and ㅂㅏㅂ is combined into a single Hangul)
                result = StringUtils.removeLastCharacter(result);
                before.tailIndex = TAILS.indexOf(getInitial(last.initialIndex));
                result += before.getHangul();
            } else {
                // Remove the vowel from a Hangul.
                // Example: 보 will produce ㅂ (the ᅩ vowel is removed)
                result += getInitial(last.initialIndex);
            }
        } else if (last.isInitial() && DOUBLE_CONSONANTS.contains(getInitial(last.initialIndex))) {
            // Generate single consonant from double consonants.
            // Example: ㅆ will convert to ㅅ
            result = StringUtils.removeLastCharacter(aTextBeforeCursor);
            result += getSingleConsonant(DOUBLE_CONSONANTS.indexOf(getInitial(last.initialIndex)));
        }

        return result;
    }

    private DecomposedHangul decompose(String aCharacter) {
        DecomposedHangul result = new DecomposedHangul();
        if (StringUtils.isEmpty(aCharacter)) {
            return result;
        }

        // Check if it's a Hangul character
        final int charValue = aCharacter.codePointAt(0); // Unicode value
        if (charValue >= HANGUL_UNICODE_START_VALUE &&  charValue <= HANGUL_UNICODE_LAST_VALUE) {
            result.initialIndex = (charValue - HANGUL_UNICODE_START_VALUE) / AMOUNT_OF_TAILS_THE_AMOUNT_OF_VOWELS;
            result.tailIndex = ((charValue - HANGUL_UNICODE_START_VALUE) % AMOUNT_OF_TAILS) - 1;
            result.vowelIndex = ((charValue - HANGUL_UNICODE_START_VALUE - result.tailIndex) % AMOUNT_OF_TAILS_THE_AMOUNT_OF_VOWELS) / AMOUNT_OF_TAILS;
            return result;
        }

        // Check if it is a Jamo character
        result.initialIndex = INITIALS.indexOf(aCharacter);
        result.vowelIndex = VOWELS.indexOf(aCharacter);
        result.tailIndex = TAILS.indexOf(aCharacter);

        return result;
    }

    private String getVowel(int aIndex) {
        if (aIndex < 0 || aIndex >= VOWELS.length()) {
            return "";
        }

        return VOWELS.substring(aIndex, aIndex + 1);
    }


    private String getTail(int aIndex) {
        if (aIndex < 0 || aIndex >= TAILS.length()) {
            return "";
        }

        return TAILS.substring(aIndex, aIndex + 1);
    }

    private String getInitial(int aIndex) {
        if (aIndex < 0 || aIndex >= INITIALS.length()) {
            return "";
        }

        return INITIALS.substring(aIndex, aIndex + 1);
    }

    private String getSingleConsonant(int aIndex) {
        if (aIndex < 0 || aIndex >= SINGLE_CONSONANTS.length()) {
            return "";
        }

        return SINGLE_CONSONANTS.substring(aIndex, aIndex + 1);
    }

    private String getDoubleConsonant(int aIndex) {
        if (aIndex < 0 || aIndex >= DOUBLE_CONSONANTS.length()) {
            return "";
        }

        return DOUBLE_CONSONANTS.substring(aIndex, aIndex + 1);
    }

    @Override
    public boolean usesTextOverride() { return true; }

    @Override
    public String getKeyboardTitle() {
        return StringUtils.getStringByLocale(mContext, R.string.settings_language_korean, getLocale());
    }

    @Override
    public Locale getLocale() {
        return Locale.KOREAN;
    }

    @Override
    public String getSpaceKeyText(String aComposingText) {
        return StringUtils.getStringByLocale(mContext, R.string.settings_language_korean, getLocale());
    }

    @Override
    public String[] getDomains(String... domains) {
        return super.getDomains(".kr");
    }
}
