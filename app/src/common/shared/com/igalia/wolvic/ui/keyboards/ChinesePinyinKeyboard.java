package com.igalia.wolvic.ui.keyboards;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
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
import java.util.regex.Pattern;

import jp.co.omronsoft.openwnn.ComposingText;
import jp.co.omronsoft.openwnn.SymbolList;
import jp.co.omronsoft.openwnn.WnnWord;

public class ChinesePinyinKeyboard extends BaseKeyboard {
    private static final String LOGTAG = SystemUtils.createLogtag(ChinesePinyinKeyboard.class);
    private CustomKeyboard mKeyboard;
    private CustomKeyboard mSymbolsKeyboard;
    private SymbolList mSymbolsConverter;  // For Emoji characters.
    private List<Words> mEmojiList = null;
    private File mDB;
    private HashMap<String, KeyMap> mKeymaps = new HashMap<>();
    private HashMap<String, KeyMap> mExtraKeymaps = new HashMap<>();

    public ChinesePinyinKeyboard(Context aContext) {
        super(aContext);
    }

    @NonNull
    @Override
    public CustomKeyboard getAlphabeticKeyboard() {
        if (mKeyboard == null) {
            mKeyboard = new CustomKeyboard(mContext.getApplicationContext(), R.xml.keyboard_qwerty_pinyin);
            loadDatabase();
        }
        return mKeyboard;
    }

    @Nullable
    @Override
    public CustomKeyboard getSymbolsKeyboard() {
        if (mSymbolsKeyboard == null) {
            mSymbolsKeyboard = new CustomKeyboard(mContext.getApplicationContext(), R.xml.keyboard_symbols_pinyin);
            // We use openwnn to provide us Emoji character although we are not using JPN keyboard.
            mSymbolsConverter = new SymbolList(mContext, SymbolList.LANG_JA);
        }
        return mSymbolsKeyboard;
    }

    @Override
    public String getModeChangeKeyText() {
        return mContext.getString(R.string.pinyin_keyboard_mode_change);
    }

    @Nullable
    @Override
    public CandidatesResult getCandidates(String aComposingText) {
        if (!usesComposingText() || StringUtils.isEmpty(aComposingText)) {
            return null;
        }

        // Autocomplete when special characters are clicked
        final char lastChar = aComposingText.charAt(aComposingText.length() - 1);
        final boolean autocompose = ("" + lastChar).matches("[^a-z]");

        aComposingText = aComposingText.replaceAll("\\s","");
        if (aComposingText.isEmpty()) {
            return null;
        }

        ArrayList<String> displayList = getDisplayCode(aComposingText);
        int syllables = 0;

        StringBuilder code = new StringBuilder();
        if (displayList != null) {
            syllables = displayList.size();
            for (String display: displayList) {
                if (code.length() != 0) {
                    code.append(' ');
                }
                code.append(display);
            }
        }

        ArrayList<Words> words = new ArrayList<>();
        StringBuilder candidate = new StringBuilder();
        String tempKey = aComposingText;
        String remainKey = "";

        // First candidate
        while (tempKey.length() > 0) {
            List<Words> displays = getDisplays(tempKey);
            if (displays != null && displays.size() > 0){
                candidate.append(displays.get(0).value);
                tempKey = remainKey;
                remainKey = "";
            } else {
                remainKey = tempKey.charAt(tempKey.length() - 1) + remainKey;
                tempKey = tempKey.substring(0, tempKey.length() - 1);
            }
        }

        // We can't find available candidates, so using the composing text
        // as the only item of candidates.
        if (candidate.length() == 0) {
            candidate.append(aComposingText);
        }
        words.add(new Words(syllables, code.toString(), candidate.toString()));

        // Extra candidates
        tempKey = aComposingText;
        while (tempKey.length() > 0) {
            List<Words> displays = getDisplays(tempKey);
            if (displays != null) {
                words.addAll(displays);
            }
            KeyMap map = mKeymaps.get(tempKey);
            if (map != null && map.candidates.size() > 0) {
                words.addAll(map.candidates);
            }
            tempKey = tempKey.substring(0, tempKey.length() - 1);
        }
        cleanCandidates(words);

        CandidatesResult result = new CandidatesResult();
        result.words = words;
        result.action = autocompose ? CandidatesResult.Action.AUTO_COMPOSE : CandidatesResult.Action.SHOW_CANDIDATES;
        result.composing = aComposingText;
        if (result.words.size() > 0) {
            final char kBackslashCode = 92;
            String newCode = result.words.get(0).code;

            // When using backslashes ({@code \}) in the replacement string
            // will cause crash at `replaceFirst()`, so we need to replace it first.
            if (result.words.get(0).code.length() > 0 &&
                result.words.get(0).code.charAt(result.words.get(0).code.length() - 1)
                        == kBackslashCode) {
                newCode = result.words.get(0).code.replace("\\", "\\\\");
                aComposingText = aComposingText.replace("\\", "\\\\");
            }
            String codeWithoutSpaces = StringUtils.removeSpaces(newCode);
            result.composing = aComposingText.replaceFirst(Pattern.quote(codeWithoutSpaces), newCode);
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

    @Override
    public String getComposingText(String aComposing, String aCode) {
        if (mEmojiList != null) {
            for (Words word : mEmojiList) {
                if (word.code.equals(aCode)) {
                    return "";
                }
            }
        }
        // If we don't have a text code from the code book,
        // just return an empty string to do composing.
        if (aCode.isEmpty()) {
            return "";
        }
        return aComposing.replaceFirst(Pattern.quote(aCode), "");
    }

    private ArrayList<String> getDisplayCode(String aKey) {
        ArrayList<String> result = new ArrayList<>();
        String remain = "";
        while (aKey.length() > 0) {
            List<Words> displays = getDisplays(aKey);
            if (displays != null && displays.size() > 0) {
                result.add(displays.get(0).code);
                aKey = remain;
                remain = "";
            } else {
                remain = aKey.charAt(aKey.length() - 1) + remain;
                aKey = aKey.substring(0, aKey.length() - 1);
            }
        }
        return result.size() > 0 ? result : null;
    }

    private void cleanCandidates(ArrayList<Words> aCandidates) {
        // Remove potential repeated value between first candidate and first extra
        if (aCandidates.size() > 1 && aCandidates.get(0).value.equals((aCandidates.get(1).value))) {
            aCandidates.remove(0);
        }

        int n = aCandidates.size();
        for (int i = 0; i < n; ++i) {
            Words candidate = aCandidates.get(i);
            if (candidate.value.matches("^[a-z]+$")) {
                // Move latin char fallbacks to the end of the list
                aCandidates.remove(i);
                aCandidates.add(candidate);
                i--;
                n--;
            } else if (candidate.value.matches("^[A-Z]$") && !candidate.code.contains(candidate.value)) {
                // Move uppercase latin char fallback to the end only when generated via lowercase input.
                aCandidates.remove(i);
                aCandidates.add(candidate);
                i--;
                n--;
            } else if (candidate.value.matches(".*[a-z]+$")) {
                // Discard latin char fallback at the end of chinese char fallbacks
                candidate.value = candidate.value.replaceAll("[a-z]+$", "").trim();
                candidate.code = candidate.code.replaceAll("[a-z]+$", "").trim();
            }
        }

        // Remove another potential repeated value between first candidate and first extra
        if (aCandidates.size() > 1 && aCandidates.get(0).value.equals((aCandidates.get(1).value))) {
            aCandidates.remove(0);
        }
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
        return mDB.exists();
    }

    @Override
    public String getKeyboardTitle() {
        return StringUtils.getStringByLocale(mContext, R.string.settings_language_simplified_chinese, getLocale());
    }

    @Override
    public Locale getLocale() {
        return Locale.SIMPLIFIED_CHINESE;
    }

    @Override
    public String getSpaceKeyText(String aComposingText) {
        if (aComposingText == null || aComposingText.trim().isEmpty()) {
            return StringUtils.getStringByLocale(mContext, R.string.settings_language_simplified_chinese, getLocale());
        } else {
            return mContext.getString(R.string.pinyin_spacebar_selection);
        }
    }

    @Override
    public String getEnterKeyText(int aIMEOptions, String aComposingText) {
        if (aComposingText == null || aComposingText.trim().isEmpty()) {
            return super.getEnterKeyText(aIMEOptions, aComposingText);
        } else {
            return mContext.getString(R.string.pinyin_enter_completion);
        }
    }

    private List<Words> getDisplays(String aKey) {
        if (aKey.matches("^[^a-z]+$")) {
            // Allow completion of uppercase letters, numbers and symbols
            return Collections.singletonList(new Words(1, aKey, aKey));
        }
        loadKeymapIfNotLoaded(aKey);
        KeyMap map = mKeymaps.get(aKey);
        return map != null ? map.displays : null;
    }


    private void loadDatabase() {
        try {
            mDB = mContext.getDatabasePath("google_pinyin.db");
            addExtraKeyMaps();
        }
        catch (Exception ex) {
            Log.e(LOGTAG, "Error reading pinyin database: " + ex.getMessage());
        }
    }

    private void addExtraKeyMaps() {
        addExtraKeyMap("a", "a", "a|A");
        addExtraKeyMap("b", "b", "b|B");
        addExtraKeyMap("c", "c", "c|C");
        addExtraKeyMap("d", "d", "d|D");
        addExtraKeyMap("e", "e", "e|E");
        addExtraKeyMap("f", "f", "f|F");
        addExtraKeyMap("g", "g", "g|G");
        addExtraKeyMap("h", "h", "h|H");
        addExtraKeyMap("i", "i", "i|I", "喔|哦|噢");
        addExtraKeyMap("j", "j", "j|J");
        addExtraKeyMap("k", "k", "k|K");
        addExtraKeyMap("l", "l", "l|L");
        addExtraKeyMap("m", "m", "m|M");
        addExtraKeyMap("n", "n", "n|N");
        addExtraKeyMap("o", "o", "o|O");
        addExtraKeyMap("p", "p", "p|P");
        addExtraKeyMap("q", "q", "q|Q");
        addExtraKeyMap("r", "r", "r|R");
        addExtraKeyMap("s", "s", "s|S");
        addExtraKeyMap("t", "t", "t|T");
        addExtraKeyMap("u", "u", "u|U", "有|要");
        addExtraKeyMap("v", "v", "v|V", "吧|被");
        addExtraKeyMap("w", "w", "w|W");
        addExtraKeyMap("x", "x", "x|X");
        addExtraKeyMap("y", "y", "y|Y");
        addExtraKeyMap("z", "z", "z|Z");
    }

    private void loadKeymapIfNotLoaded(String aKey) {
        if (mKeymaps.containsKey(aKey)) {
            return;
        }
        loadKeymapTable(aKey);
        loadAutoCorrectTable(aKey);
        KeyMap extra = mExtraKeymaps.get(aKey);
        if (extra != null) {
            KeyMap map = mKeymaps.get(aKey);
            if (map != null) {
                map.displays.addAll(extra.displays);
                map.candidates.addAll(extra.candidates);
            }
        }
    }

    private final String[] sqliteArgs = new String[1];

    private void loadKeymapTable(String aKey) {
        SQLiteDatabase reader = SQLiteDatabase.openDatabase(mDB.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        sqliteArgs[0] = aKey;
        try (Cursor cursor = reader.rawQuery("SELECT keymap, display, candidates FROM keymaps where keymap = ? ORDER BY _id ASC", sqliteArgs)) {
            if (!cursor.moveToFirst()) {
                return;
            }
            do {
                String key = getString(cursor, 0);
                String displays = getString(cursor, 1);
                String candidates = getString(cursor, 2);
                addToKeyMap(key, key, displays, candidates);
            } while (cursor.moveToNext());
        }
    }

    private void loadAutoCorrectTable(String aKey) {
        SQLiteDatabase reader = SQLiteDatabase.openDatabase(mDB.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        sqliteArgs[0] = aKey;
        try  (Cursor cursor = reader.rawQuery("SELECT inputcode, displaycode, display FROM autocorrect where inputcode = ? ORDER BY _id ASC", sqliteArgs)) {
            if (!cursor.moveToFirst()) {
                return;
            }
            do {
                String key = getString(cursor, 0);
                String code = getString(cursor, 1);
                String displays = getString(cursor, 2);
                addToKeyMap(key, code, displays);
            } while (cursor.moveToNext());
        }
    }


    private void addToKeyMap(String aKey, String aCode, String aDisplays) {
        addToKeyMap(aKey, aCode, aDisplays, null);
    }

    private void addToKeyMap(String aKey, String aCode, String aDisplays, String aCandidates) {
        if (aKey == null || aKey.isEmpty()) {
            Log.e(LOGTAG, "Pinyin key is null");
            return;
        }
        if (aCode == null || aCode.isEmpty()) {
            Log.e(LOGTAG, "Pinyin code is null");
            return;
        }
        KeyMap keyMap = mKeymaps.get(aKey);
        if (keyMap == null) {
            keyMap = new KeyMap();
            mKeymaps.put(aKey, keyMap);
        }

        if (aDisplays != null && !aDisplays.isEmpty()) {
            String[] displayList = aDisplays.split("\\|");
            for (String display: displayList) {
                keyMap.displays.add(new Words(syllableCount(aCode), aCode, display));
            }
        }


        if (aCandidates != null && !aCandidates.isEmpty()) {
            String[] candidateList = aCandidates.split("\\|");
            for (String candidate: candidateList) {
                keyMap.candidates.add(new Words(syllableCount(aCode), aCode, candidate));
            }
        }

    }

    private void addExtraKeyMap(String aKey, String aCode, String aDisplays) {
        addExtraKeyMap(aKey, aCode, aDisplays, null);
    }

    private void addExtraKeyMap(String aKey, String aCode, String aDisplays, String aCandidates) {
        KeyMap extra = new KeyMap();
        if (aDisplays != null && !aDisplays.isEmpty()) {
            String[] displayList = aDisplays.split("\\|");
            for (String display: displayList) {
                extra.displays.add(new Words(syllableCount(aCode), aCode, display));
            }
        }


        if (aCandidates != null && !aCandidates.isEmpty()) {
            String[] candidateList = aCandidates.split("\\|");
            for (String candidate: candidateList) {
                extra.candidates.add(new Words(syllableCount(aCode), aCode, candidate));
            }
        }
        mExtraKeymaps.put(aKey, extra);
    }

    private int syllableCount(String aCode) {
        if (aCode == null) {
            return 0;
        }
        aCode = aCode.trim();
        if (aCode.isEmpty()) {
            return 0;
        }

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
        ArrayList<Words> candidates = new ArrayList<>();
    }

    @Override
    public String[] getDomains(String... domains) {
        return super.getDomains(".cn");
    }
}
