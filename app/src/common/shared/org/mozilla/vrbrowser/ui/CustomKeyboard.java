/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.inputmethodservice.Keyboard;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class CustomKeyboard extends Keyboard {

    private Key mEnterKey;
    private Key mSpaceKey;
    private int mMaxColums;

    public static final int KEYCODE_SYMBOLS_CHANGE = -10;
    public static final int KEYCODE_VOICE_INPUT = -11;
    public static final int KEYCODE_STRING_COM = -12;

    public CustomKeyboard(Context context, int xmlLayoutResId) {
        super(context, xmlLayoutResId, 0);
    }


    public CustomKeyboard (Context context, int layoutTemplateResId, CharSequence characters, int columns, int horizontalPadding) {
        this(context, layoutTemplateResId);

        mMaxColums = columns;

        int x = 0;
        int y = 0;
        int column = 0;
        setParentField(this, "mTotalWidth", 0);

        int mDefaultWidth = getParentFieldInt(this, "mDefaultWidth");
        int mDefaultHeight = getParentFieldInt(this, "mDefaultHeight");
        int mDefaultHorizontalGap = getParentFieldInt(this, "mDefaultHorizontalGap");
        int mDefaultVerticalGap = getParentFieldInt(this, "mDefaultVerticalGap");
        int mDisplayWidth = getParentFieldInt(this, "mDisplayWidth");
        ArrayList<Row> rows = null;
        Object rowsObj = getParentFieldObject(this, "rows");
        if (rowsObj != null && rowsObj instanceof ArrayList) {
            rows = (ArrayList<Row>)rowsObj;
        }

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

            if (column >= maxColumns
                    || x + mDefaultWidth + horizontalPadding > mDisplayWidth) {

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
            if (rows != null)
                rows.add(row);
        }
        setParentField(this, "mTotalHeight", y + mDefaultHeight);
    }

    private int getParentFieldInt(Object obj, String fieldName) {
        try {
            Field mField = getField(obj.getClass().getSuperclass(), fieldName);
            mField.setAccessible(true);
            return mField.getInt(obj);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return 0;
        }
    }

    private Object getFieldObject(Object obj, String fieldName) {
        try {
            Field mField = getField(obj.getClass(), fieldName);
            mField.setAccessible(true);
            return mField.get(obj);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }
    private Object getParentFieldObject(Object obj, String fieldName) {
        try {
            Field mField = getField(obj.getClass().getSuperclass(), fieldName);
            mField.setAccessible(true);
            return mField.get(this);
        } catch (IllegalAccessException e) {
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
        try {
            Field privateField = obj.getClass().getSuperclass().getDeclaredField(fieldName);
            privateField.setAccessible(true);
            privateField.set(obj, value);

        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected Key createKeyFromXml(Resources res, Row parent, int x, int y, XmlResourceParser parser) {
        Key key = super.createKeyFromXml(res, parent, x, y, parser);
        if (key.codes[0] == KeyEvent.KEYCODE_ENTER) {
            mEnterKey = key;
        } else if (key.codes[0] == ' ') {
            mSpaceKey = key;
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

    void setImeOptions(int options) {
        if (mEnterKey == null) {
            return;
        }

        switch (options & (EditorInfo.IME_MASK_ACTION | EditorInfo.IME_FLAG_NO_ENTER_ACTION)) {
            case EditorInfo.IME_ACTION_GO:
                mEnterKey.label = "GO";
                break;
            case EditorInfo.IME_ACTION_NEXT:
                mEnterKey.label = "NEXT";
                break;
            case EditorInfo.IME_ACTION_SEARCH:
                mEnterKey.label = "SEARCH";
                break;
            case EditorInfo.IME_ACTION_SEND:
                mEnterKey.label = "SEND";
                break;
            default:
                mEnterKey.label = "ENTER";
                break;
        }
    }

    public int[] getShiftKeyIndices() {
        try {
            Field mField = getField(getClass().getSuperclass(), "mShiftKeyIndices");
            mField.setAccessible(true);
            return (int[])mField.get(this);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return new int[]{-1, -1};
        }
    }

    public int getMaxColums() {
        return mMaxColums;
    }
}
