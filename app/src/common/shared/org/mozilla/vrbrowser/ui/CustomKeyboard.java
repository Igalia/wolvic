/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;

import java.util.List;

public class CustomKeyboard extends Keyboard {

    private Key mEnterKey;
    private Key mSpaceKey;

    public static final int KEYCODE_SYMBOLS_CHANGE = -10;
    public static final int KEYCODE_VOICE_INPUT = -11;
    public static final int KEYCODE_STRING_COM = -12;

    public CustomKeyboard(Context context, int xmlLayoutResId) {
        super(context, xmlLayoutResId);
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
}
