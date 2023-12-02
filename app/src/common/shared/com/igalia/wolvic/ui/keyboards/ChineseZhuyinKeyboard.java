package com.igalia.wolvic.ui.keyboards;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.igalia.wolvic.input.Keyboard.Key;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.R;
import com.igalia.wolvic.input.CustomKeyboard;
import com.igalia.wolvic.utils.StringUtils;
import com.igalia.wolvic.utils.SystemUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import jp.co.omronsoft.openwnn.ComposingText;
import jp.co.omronsoft.openwnn.SymbolList;
import jp.co.omronsoft.openwnn.WnnWord;

public class ChineseZhuyinKeyboard extends BaseKeyboard {
    private static final String LOGTAG = SystemUtils.createLogtag(ChineseZhuyinKeyboard.class);
    private static final String nonZhuyinReg = "[^ㄅ-ㄩ˙ˊˇˋˉ]";
    private CustomKeyboard mKeyboard;
    private CustomKeyboard mSymbolsKeyboard;
    private SymbolList mSymbolsConverter;  // For Emoji characters.
    private List<Words> mEmojiList = null;
    private File mWordDB;
    private File mPhraseDB;
    private HashMap<String, KeyMap> mKeymaps = new HashMap<>();
    private HashMap<String, Words> mKeyCodes = new HashMap<>();
    private final String[] sqliteArgs = new String[2];
    private final String[] roughSqliteArgs = new String[3];


    public ChineseZhuyinKeyboard(Context aContext) {
        super(aContext);
    }

    @NonNull
    @Override
    public CustomKeyboard getAlphabeticKeyboard() {
        if (mKeyboard == null) {
            mKeyboard = new CustomKeyboard(mContext.getApplicationContext(), R.xml.keyboard_qwerty_zhuyin);
            loadDatabase();
        }
        return mKeyboard;
    }

    @Nullable
    @Override
    public CustomKeyboard getSymbolsKeyboard() {
        if (mSymbolsKeyboard == null) {
            mSymbolsKeyboard = new CustomKeyboard(mContext.getApplicationContext(), R.xml.keyboard_symbols_zhuyin);
            // We use openwnn to provide us Emoji character although we are not using JPN keyboard.
            mSymbolsConverter = new SymbolList(mContext, SymbolList.LANG_JA);
        }
        return mSymbolsKeyboard;
    }

    @Nullable
    @Override
    public CandidatesResult getCandidates(String aComposingText) {
        if (!usesComposingText() || aComposingText == null) {
            return null;
        }

        // Replacing all spaces to the first tone because Zhuyin input doesn't use spaces.
        aComposingText = aComposingText.replaceAll("\\s","ˉ");
        if (aComposingText.isEmpty()) {
            return null;
        }

        // If using non-Zhuyin symbols like numeric, abc, special symbols,
        // we just need to compose them.
        String lastChar = "" + aComposingText.charAt(aComposingText.length() - 1);
        if (lastChar.matches(nonZhuyinReg)) {
            CandidatesResult result = new CandidatesResult();
            result.words = getDisplays(aComposingText);
            result.action = CandidatesResult.Action.AUTO_COMPOSE;
            result.composing = aComposingText;
            return result;
        }

        ArrayList<Words> words = new ArrayList<>();
        if (aComposingText.length() > 0) {
            List<Words> displays = getDisplays(aComposingText);
            if (displays != null && displays.size() > 0) {
                words.addAll(displays);
            }
        }

        CandidatesResult result = new CandidatesResult();
        result.words = words;
        result.action = CandidatesResult.Action.SHOW_CANDIDATES;
        result.composing = aComposingText;
        if (result.words.size() > 0) {
            String codeWithoutSpaces = StringUtils.removeSpaces(result.words.get(0).code);
            result.composing = aComposingText.replaceFirst(Pattern.quote(codeWithoutSpaces), result.words.get(0).code);
        }

        return result;
    }

    @Override
    public CandidatesResult getEmojiCandidates(String aComposingText) {
        if (mEmojiList == null) {
            List<Words> words = new ArrayList<>();
            ComposingText text = new ComposingText();
            mSymbolsConverter.convert(text);

            int candidates = mSymbolsConverter.predict(text, 0, -1);
            if (candidates > 0) {
                WnnWord word;
                while ((word = mSymbolsConverter.getNextCandidate()) != null) {
                    words.add(new Words(1, word.stroke, word.candidate));
                }
                mEmojiList = words;
            }
        }

        CandidatesResult result = new CandidatesResult();
        result.words = mEmojiList;
        result.action = CandidatesResult.Action.SHOW_CANDIDATES;
        result.composing = aComposingText;

        return result;
    }

    private String GetTransCode(String aText) {
        String code = aText;
        String transCode = "";
        while (code.length() > 0) {
            transCode += mKeyCodes.get(code.substring(0, 1)).code;
            code = code.replaceFirst(code.substring(0, 1), "");
        }
        return transCode;
    }

    @Override
    public String getComposingText(String aComposing, String aCode) {
        String sub;
        String display = "";
        Words value;
        final int shift = 2; // In Zhuyin input, we have two digits for every symbol.

        if (aComposing.matches(nonZhuyinReg)) {
            return aComposing.replaceFirst(Pattern.quote(aCode), "");
        }

        if (mEmojiList != null) {
            for (Words word : mEmojiList) {
                if (word.code.equals(aCode)) {
                    return "";
                }
            }
        }

        for (int i = 0; i <= aCode.length() - shift; i += shift) {
            sub = aCode.substring(i, i + shift);

            for (Map.Entry<String, Words> entry : mKeyCodes.entrySet()) {
                value = entry.getValue();
                if (value.code.equals(sub)) {
                    display += value.value;
                }
            }
        }

        // Finding the item in aComposing that is the same with display.
        String result = display.length() < aComposing.length() ? display : aComposing;
        return aComposing.replaceFirst(Pattern.quote(result), "");
    }

    @Override
    public boolean needsDatabase() {
        return true;
    }

    @Override
    public boolean supportsAutoCompletion() {
        return true;
    }

    @Override
    public boolean usesComposingText() {
        return mWordDB.exists() && mPhraseDB.exists();
    }

    @Override
    public String getKeyboardTitle() {
        return StringUtils.getStringByLocale(mContext, R.string.settings_language_traditional_chinese, getLocale());
    }

    @Override
    public Locale getLocale() {
        return Locale.TRADITIONAL_CHINESE;
    }

    @Override
    public String getSpaceKeyText(String aComposingText) {
        if (aComposingText == null || aComposingText.trim().isEmpty()) {
            return "";
        } else {
            return mContext.getString(R.string.zhuyin_spacebar_selection);
        }
    }

    @Override
    public String getEnterKeyText(int aIMEOptions, String aComposingText) {
        if (aComposingText == null || aComposingText.trim().isEmpty()) {
            return super.getEnterKeyText(aIMEOptions, aComposingText);
        } else {
            return mContext.getString(R.string.zhuyin_enter_completion);
        }
    }

    @Override
    public String getModeChangeKeyText() {
        return mContext.getString(R.string.zhuyin_keyboard_mode_change);
    }

    private List<Words> getDisplays(String aKey) {
        // Allow completion of uppercase/lowercase letters numbers, and symbols
        // aKey.length() > 1 only happens when switching from other keyboard.
        if (aKey.matches(nonZhuyinReg) ||
                (aKey.length() > 1 && mKeymaps.size() == 0)) {
            return Collections.singletonList(new Words(1, aKey, aKey));
        }

        String code = aKey.replaceAll(nonZhuyinReg, "");
        code = GetTransCode(code);
        loadKeymapIfNotLoaded(code);
        KeyMap map = mKeymaps.get(code);

        if (map == null) {
            return Collections.singletonList(new Words(1, aKey, aKey));
        }
        // When detecting special symbols at the last character, and
        // because special symbols are not defined in our code book. We
        // need to add it back to our generated word for doing following
        // AUTO_COMPOSE.
        final String lastChar = "" + aKey.charAt(aKey.length()-1);
        if (map != null && lastChar.matches(nonZhuyinReg))
        {
            Words word = map.displays.get(0);
            return Collections.singletonList(new Words(1,
                    word.code + lastChar, word.value + lastChar));
        }
        return map.displays;
    }


    private void loadDatabase() {
        try {
            mWordDB = mContext.getDatabasePath("zhuyin_words.db");
            mPhraseDB = mContext.getDatabasePath("zhuyin_phrases.db");
            addExtraKeyMaps();
        }
        catch (Exception ex) {
            Log.e(LOGTAG, "Error reading zhuyin database: " + ex.getMessage());
        }
    }

    private String findLabelFromKey(int primaryCode) {
        for (Key key : mKeyboard.getKeys()) {
            if (key.codes[0] == primaryCode) {
                return "" + key.label;
            }
        }

        Log.e(LOGTAG, "Error can't find label from Zhuyin keys: " + primaryCode);
        return null;
    }

    private void addExtraKeyMaps() {
//        List<Key> keys = mKeyboard.getKeys();
        String s =  Character.toString((char)0x3105);
        String aa = findLabelFromKey(0x3105);

        addKeyCode("ㄅ", "10", "ㄅ");
        addKeyCode("ㄆ", "11", "ㄆ");
        addKeyCode("ㄇ", "12", "ㄇ");
        addKeyCode("ㄈ", "13", "ㄈ");
        addKeyCode("ㄉ", "14", "ㄉ");
        addKeyCode("ㄊ", "15", "ㄊ");
        addKeyCode("ㄋ", "16", "ㄋ");
        addKeyCode("ㄌ", "17", "ㄌ");
        addKeyCode("ㄍ", "18", "ㄍ");
        addKeyCode("ㄎ", "19", "ㄎ");
        addKeyCode("ㄏ", "1A", "ㄏ");
        addKeyCode("ㄐ", "1B", "ㄐ");
        addKeyCode("ㄑ", "1C", "ㄑ");
        addKeyCode("ㄒ", "1D", "ㄒ");
        addKeyCode("ㄓ", "1E", "ㄓ");
        addKeyCode("ㄔ", "1F", "ㄔ");
        addKeyCode("ㄕ", "1G", "ㄕ");
        addKeyCode("ㄖ", "1H", "ㄖ");
        addKeyCode("ㄗ", "1I", "ㄗ");
        addKeyCode("ㄘ", "1J", "ㄘ");
        addKeyCode("ㄙ", "1K", "ㄙ");
        addKeyCode("ㄚ", "20", "ㄚ");
        addKeyCode("ㄛ", "21", "ㄛ");
        addKeyCode("ㄜ", "22", "ㄜ");
        addKeyCode("ㄝ", "23", "ㄝ");
        addKeyCode("ㄞ", "24", "ㄞ");
        addKeyCode("ㄟ", "25", "ㄟ");
        addKeyCode("ㄠ", "26", "ㄠ");
        addKeyCode("ㄡ", "27", "ㄡ");
        addKeyCode("ㄢ", "28", "ㄢ");
        addKeyCode("ㄣ", "29", "ㄣ");
        addKeyCode("ㄤ", "2A", "ㄤ");
        addKeyCode("ㄥ", "2B", "ㄥ");
        addKeyCode("ㄦ", "2C", "ㄦ");
        addKeyCode("ㄧ", "30", "ㄧ");
        addKeyCode("ㄨ", "31", "ㄨ");
        addKeyCode("ㄩ", "32", "ㄩ");

        addKeyCode("˙", "40", "˙");
        addKeyCode("ˊ", "41", "ˊ");
        addKeyCode("ˇ", "42", "ˇ");
        addKeyCode("ˋ", "43", "ˋ");
        addKeyCode("ˉ", "44", "ˉ");
    }

    private void loadKeymapIfNotLoaded(String aKey) {
        if (mKeymaps.containsKey(aKey)) {
            return;
        }
        loadKeymapTable(aKey);
    }

    private void loadKeymapTable(String aKey) {
        SQLiteDatabase reader = SQLiteDatabase.openDatabase(mWordDB.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        String transCode = aKey;
        int limit = 50;
        boolean exactQuery = false;
        final char firstKeyCodeInTones = '4'; // the first keycode of tones[˙, ˊ, ˋ, ˉ].

        // Finding if aKey contains tones.
        if (transCode.charAt(transCode.length() - 2) == firstKeyCodeInTones) {
            exactQuery = true;
        }
        // We didn't store the first tone in DB.
        transCode = transCode.replaceAll("44", "");

        sqliteArgs[0] = transCode;
        sqliteArgs[1] = "" + limit;

        // Query word exactly
        try (Cursor cursor = reader.rawQuery("SELECT code, word FROM words_" + transCode.substring(0, 2)
                + " WHERE code = ? GROUP BY word ORDER BY frequency DESC LIMIT ?", sqliteArgs)) {
            if (cursor.moveToFirst()) {
                do {
                    String key = getString(cursor, 0);
                    String displays = getString(cursor, 1);
                    addToKeyMap(aKey, key, displays);
                    --limit;
                } while (limit >= 0 && cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(LOGTAG, "Querying Zhuyin db failed");
        }

        if (!exactQuery) {
            // Query word roughly
            roughSqliteArgs[0] = transCode + "%";
            roughSqliteArgs[1] = "" + transCode;
            roughSqliteArgs[2] = "" + limit;
            try (Cursor cursor = reader.rawQuery("SELECT code, word FROM words_" + transCode.substring(0, 2)
                    + " WHERE code like ? and code!= ? GROUP BY word ORDER BY frequency DESC LIMIT ?", roughSqliteArgs)) {
                if (cursor.moveToFirst()) {
                    do {
                        String key = getString(cursor, 0);
                        String word = getString(cursor, 1);
                        addToKeyMap(aKey, key, word);
                        --limit;
                    } while (limit >= 0 && cursor.moveToNext());
                }
            } catch (Exception e) {
                Log.e(LOGTAG, "Querying Zhuyin db failed");
            }
        }

        if (limit <= 0) {
            return;
        }

        // Query phrase
        reader = SQLiteDatabase.openDatabase(mPhraseDB.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        sqliteArgs[0] = transCode + '%';
        sqliteArgs[1] = "" + limit;
        try (Cursor cursor = reader.rawQuery("SELECT code, word FROM phrases_" + transCode.substring(0, 2)
                + " WHERE code like ? GROUP BY word ORDER BY frequency DESC LIMIT ?", sqliteArgs)) {
            if (cursor.moveToFirst()) {
                do {
                    String key = getString(cursor, 0);
                    String word = getString(cursor, 1);
                    addToKeyMap(aKey, key, word);
                    --limit;
                } while (limit >= 0 && cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(LOGTAG, "Querying Zhuyin db failed");
        }
    }

    private void addToKeyMap(String aKey, String aCode, String aDisplays) {
        if (aKey == null || aKey.isEmpty()) {
            Log.e(LOGTAG, "Zhuyin key is null");
            return;
        }
        if (aCode == null || aCode.isEmpty()) {
            Log.e(LOGTAG, "Zhuyin code is null");
            return;
        }
        KeyMap keyMap = mKeymaps.get(aKey);
        if (keyMap == null) {
            keyMap = new KeyMap();
            mKeymaps.put(aKey, keyMap);
        }

        if (aDisplays != null && !aDisplays.isEmpty()) {
            String[] displayList = aDisplays.split("\\|");
            if (displayList != null) {
                for (String display: displayList) {
                    keyMap.displays.add(new Words(syllableCount(aCode), aCode, display));
                }
            }
        }
    }

    private void addKeyCode(String aKey, String aCode, String aDisplay) {
        mKeyCodes.put(aKey, new Words(syllableCount(aCode), aCode, aDisplay));
    }

    private int syllableCount(String aCode) {
        if (aCode == null) {
            return 0;
        }
        aCode = aCode.trim();
        if (aCode.isEmpty()) {
            return 0;
        }

        // An empty cell indicates that the corresponding syllable does not exist.
        return (int)aCode.chars().filter(ch -> ch == ' ').count() + 1;
    }

    private String getString(Cursor aCursor, int aIndex) {
        if (aCursor.isNull(aIndex)) {
            return null;
        }
        return aCursor.getString(aIndex);
    }

    class KeyMap {
        ArrayList<Words> displays = new ArrayList<>();
    }

    @Override
    public String[] getDomains(String... domains) {
        return super.getDomains(".tw");
    }
}
