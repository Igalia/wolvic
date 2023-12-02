package com.igalia.wolvic.ui.keyboards;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.annotation.Nullable;

import com.igalia.wolvic.utils.DictionaryUtils;
import com.igalia.wolvic.utils.StringUtils;
import com.igalia.wolvic.utils.SystemUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class BaseLatinKeyboard extends BaseKeyboard {
    private static final String LOGTAG = SystemUtils.createLogtag(EnglishKeyboard.class);
    private final HashMap<String, ArrayList<Words>> mKeymaps = new HashMap<>();
    private File mDB;

    public BaseLatinKeyboard(Context aContext) {
        super(aContext);
    }

    @Nullable
    @Override
    public CandidatesResult getCandidates(String aComposingText) {
        if (!usesComposingText() || StringUtils.isEmpty(aComposingText)) {
            return null;
        }

        // Autocomplete when special characters are clicked
        final char lastChar = aComposingText.charAt(aComposingText.length() - 1);
        final boolean autocompose = ("" + lastChar).matches("[^\\p{L}]");

        aComposingText = aComposingText.replaceAll("\\s", "");
        if (aComposingText.isEmpty()) {
            return null;
        }

        ArrayList<Words> words = new ArrayList<>();
        words.add(new Words(1, aComposingText, aComposingText));

        String tempKey = aComposingText;
        while (tempKey.length() > 0) {
            List<Words> displays = getDisplays(tempKey);
            if (displays != null) {
                words.addAll(displays);
            }
            tempKey = tempKey.substring(0, tempKey.length() - 1);
        }
        cleanCandidates(words);

        CandidatesResult result = new CandidatesResult();
        result.words = words;
        result.action = autocompose ? CandidatesResult.Action.AUTO_COMPOSE : CandidatesResult.Action.SHOW_CANDIDATES;
        result.composing = aComposingText;
        return result;
    }

    private void cleanCandidates(ArrayList<Words> aCandidates) {
        Set<String> words = new HashSet<>();
        int n = aCandidates.size();
        for (int i = 0; i < n; ++i) {
            Words candidate = aCandidates.get(i);
            if (words.contains(candidate.value.toLowerCase())) {
                aCandidates.remove(i);
                i--;
                n--;
            } else {
                words.add(candidate.value.toLowerCase());
                // Make sure capital cases matches the composing text
                StringBuilder newValue = new StringBuilder(candidate.value);
                if (candidate.code.length() > 1 && candidate.code.toUpperCase().equals(candidate.code)) {
                    newValue = new StringBuilder(candidate.value.toUpperCase());
                } else {
                    for (int k = 0; k < candidate.code.length(); k++) {
                        if (Character.isUpperCase(candidate.code.charAt(k)) && newValue.length() > k) {
                            newValue.setCharAt(k, Character.toUpperCase(newValue.charAt(k)));
                        }
                    }
                }
                candidate.value = newValue.toString();
                aCandidates.set(i, candidate);
            }
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

    private List<Words> getDisplays(String aKey) {
        if (aKey.matches("^[^\\p{L}]+$")) {
            // Allow completion of numbers and symbols
            return Collections.singletonList(new Words(1, aKey, aKey));
        }
        loadKeymapIfNotLoaded(aKey);
        return mKeymaps.get(aKey);
    }

    protected void loadDatabase() {
        mDB = mContext.getDatabasePath(DictionaryUtils.getExternalDicFullName(getLocale().toString()));
    }

    private void loadKeymapIfNotLoaded(String aKey) {
        if (mKeymaps.containsKey(aKey)) {
            return;
        }
        loadAutoCorrectTable(aKey);
    }

    private void loadAutoCorrectTable(String aKey) {
        SQLiteDatabase reader = SQLiteDatabase.openDatabase(mDB.getPath(), null, SQLiteDatabase.OPEN_READONLY);;
        String[] sqliteArgs = new String[1];
        sqliteArgs[0] = aKey.toLowerCase() + "%";
        try (Cursor cursor = reader.rawQuery("SELECT word FROM autocorrect where LOWER(word) LIKE ? ORDER BY originalFreq DESC LIMIT 20", sqliteArgs)) {
            if (!cursor.moveToFirst()) {
                return;
            }
            do {
                String word = getString(cursor, 0);
                addToKeyMap(aKey, word);
            } while (cursor.moveToNext());
        }
    }

    private void addToKeyMap(String aKey, String aCode) {
        if (aKey == null || aKey.isEmpty()) {
            Log.e(LOGTAG, "key is null");
            return;
        }
        if (aCode == null || aCode.isEmpty()) {
            Log.e(LOGTAG, "code is null");
            return;
        }
        ArrayList<Words> keyMap = mKeymaps.computeIfAbsent(aKey, k -> new ArrayList<>());

        keyMap.add(new Words(1, aKey, aCode + " "));
    }

    private String getString(Cursor aCursor, int aIndex) {
        if (aCursor.isNull(aIndex)) {
            return null;
        }
        return aCursor.getString(aIndex);
    }
}
