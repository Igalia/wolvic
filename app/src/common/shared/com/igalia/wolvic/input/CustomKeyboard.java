/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.input;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import com.igalia.wolvic.input.Keyboard;
import android.view.KeyEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class CustomKeyboard extends Keyboard {

    private Key mEnterKey;
    private Key mSpaceKey;
    private Key mModeChangeKey;
    private int mMaxColumns;
    private int[] mDisabledKeysIndexes;

    public static final int KEYCODE_SYMBOLS_CHANGE = -10;
    public static final int KEYCODE_VOICE_INPUT = -11;
    public static final int KEYCODE_LANGUAGE_CHANGE = -12;
    public static final int KEYCODE_EMOJI = -13;
    public static final int KEYCODE_DOMAIN = -14;

    public CustomKeyboard(Context context, int xmlLayoutResId) {
        super(context, xmlLayoutResId, 0);
    }


    public CustomKeyboard (Context context, int layoutTemplateResId, CharSequence characters, int columns, int horizontalPadding, int verticalGap) {
        this(context, layoutTemplateResId);

        mMaxColumns = columns;

        int x = 0;
        int y = 0;
        int column = 0;
        setParentField(this, "mTotalWidth", 0);

        int mDefaultWidth = getParentFieldInt(this, "mDefaultWidth");
        int mDefaultHeight = getParentFieldInt(this, "mDefaultHeight");
        int mDefaultHorizontalGap = getParentFieldInt(this, "mDefaultHorizontalGap");
        int mDefaultVerticalGap = verticalGap >= 0 ? verticalGap : getParentFieldInt(this, "mDefaultVerticalGap");
        int mDisplayWidth = getParentFieldInt(this, "mDisplayWidth");
        Object rowsObj = getParentFieldObject(this, "rows");
        @SuppressWarnings("unchecked")
        ArrayList<Row> rows = rowsObj instanceof ArrayList ? (ArrayList<Row>) rowsObj : null;

        int rowsNum = (int)Math.ceil((characters.length()+0.1)/columns);
        final int maxColumns = columns == -1 ? Integer.MAX_VALUE : columns;
        Row[] mRows = new Row[maxColumns];
        for (int i=0; i<rowsNum; i++) {
            Row row = new Row(this);
            row.defaultHeight = mDefaultHeight;
            row.defaultWidth = mDefaultWidth;
            row.defaultHorizontalGap = mDefaultHorizontalGap;
            row.verticalGap = mDefaultVerticalGap;
            row.rowEdgeFlags = EDGE_TOP;
            mRows[i] = row;
        }
        for (int i = 0; i < characters.length(); i++) {
            char c = characters.charAt(i);
            int rowIndex = (int)Math.floor(i/columns);

            final Key key = new Key(mRows[rowIndex]);

            if (column >= maxColumns || x + mDefaultWidth + horizontalPadding > mDisplayWidth) {

                mRows[rowIndex].rowEdgeFlags = EDGE_BOTTOM;

                x = 0;
                y += mDefaultVerticalGap + mDefaultHeight;
                column = 0;
            }

            key.x = x;
            key.y = y;
            key.label = String.valueOf(c);
            key.codes = new int[]{c};
            column++;
            x += key.width + key.gap;
            getKeys().add(key);
            Object keysObj = getFieldObject(mRows[rowIndex], "mKeys");
            if (keysObj != null && getFieldObject(mRows[rowIndex], "mKeys") instanceof ArrayList) {
                @SuppressWarnings("unchecked")
                ArrayList<Key> mKeys = (ArrayList<Key>) keysObj;
                if (mKeys != null) {
                    mKeys.add(key);
                }
            }
            int mTotalWidth = getParentFieldInt(this, "mTotalWidth");
            if (x > mTotalWidth) {
                setParentField(this, "mTotalWidth", x);
            }
        }

        for (Row row : mRows) {
            if (rows != null) {
                rows.add(row);
            }
        }
        setParentField(this, "mTotalHeight", y + mDefaultHeight);
    }

    private int getParentFieldInt(Object obj, String fieldName) {
        try {
            Field mField = getField(obj.getClass().getSuperclass(), fieldName);
            mField.setAccessible(true);
            return mField.getInt(obj);
        } catch (IllegalAccessException | RuntimeException e) {
            e.printStackTrace();
            return 0;
        }
    }

    private Object getFieldObject(Object obj, String fieldName) {
        try {
            Field mField = getField(obj.getClass(), fieldName);
            mField.setAccessible(true);
            return mField.get(obj);
        } catch (IllegalAccessException | RuntimeException e) {
            e.printStackTrace();
            return null;
        }
    }
    private Object getParentFieldObject(Object obj, String fieldName) {
        try {
            Field mField = getField(obj.getClass().getSuperclass(), fieldName);
            mField.setAccessible(true);
            return mField.get(this);
        } catch (IllegalAccessException| RuntimeException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Field getField(Class<?> clazz, String fieldName) {
        Class<?> tmpClass = clazz;
        do {
            try {
                Field f = tmpClass.getDeclaredField(fieldName);
                return f;
            } catch (NoSuchFieldException e) {
                tmpClass = tmpClass.getSuperclass();
            }
        } while (tmpClass != null);

        throw new RuntimeException("Field '" + fieldName
                + "' not found on class " + clazz);
    }

    public static void setParentField(Object obj, String fieldName, Object value) {
        if (obj.getClass().getSuperclass() == null) {
            return;
        }
        try {
            Field privateField = obj.getClass().getSuperclass().getDeclaredField(fieldName);
            privateField.setAccessible(true);
            privateField.set(obj, value);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected Key createKeyFromXml(Resources res, Row parent, int x, int y, XmlResourceParser parser) {
        Key key = super.createKeyFromXml(res, parent, x, y, parser);
        if (key.codes[0] == KeyEvent.KEYCODE_ENTER || key.codes[0] == Keyboard.KEYCODE_DONE) {
            mEnterKey = key;
        } else if (key.codes[0] == ' ') {
            mSpaceKey = key;
        } else if (key.codes[0] == Keyboard.KEYCODE_MODE_CHANGE) {
            mModeChangeKey = key;
        }

        return key;
    }

    // Override to fix the bug of not all the touch area covered in wide buttons (e.g. space)
    @Override
    public int[] getNearestKeys(int x, int y) {
        List<Key> keys = getKeys();
        Key[] mKeys = keys.toArray(new Key[keys.size()]);
        int i = 0;
        for (Key key : mKeys) {
            if(key.isInside(x, y))
                return new int[]{i};
            i++;
        }
        return new int[0];
    }

    public boolean setEnterKeyLabel(String aText) {
        if (mEnterKey != null) {
            boolean changed = !aText.equalsIgnoreCase(mEnterKey.label.toString());
            mEnterKey.label = aText;
            return changed;
        }
        return false;
    }

    public boolean setSpaceKeyLabel(String aText) {
        if (mSpaceKey != null) {
            boolean changed = !aText.equalsIgnoreCase(mSpaceKey.label.toString());
            mSpaceKey.label = aText;
            return changed;
        }
        return false;
    }

    public boolean setModeChangeKeyLabel(String aText) {
        if (mModeChangeKey != null) {
            boolean changed = !aText.equalsIgnoreCase(mModeChangeKey.label.toString());
            mModeChangeKey.label = aText;
            return changed;
        }
        return false;
    }

    public int[] getShiftKeyIndices() {
        try {
            Field mField = getField(getClass().getSuperclass(), "mShiftKeyIndices");
            mField.setAccessible(true);
            return (int[])mField.get(this);
        } catch (Exception e) {
            return new int[]{super.getShiftKeyIndex()};
        }
    }

    public int getMaxColumns() {
        return mMaxColumns;
    }

    public void disableKeys(int[] disabledKeyIndexes) {
        mDisabledKeysIndexes = disabledKeyIndexes;
    }

    public boolean isKeyEnabled(int keyIndex) {
        if (mDisabledKeysIndexes != null) {
            for (int key : mDisabledKeysIndexes) {
                if (key == keyIndex) {
                    return false;
                }
            }
        }

        return true;
    }
}
