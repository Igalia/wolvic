/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.Keyboard;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.SessionStore;
import org.mozilla.vrbrowser.WidgetPlacement;


public class KeyboardWidget extends UIWidget implements CustomKeyboardView.OnKeyboardActionListener, GeckoSession.TextInputDelegate {
    private static final String LOGTAG = "VRB";
    private CustomKeyboardView mKeyboardview;
    private CustomKeyboard mKeyboardQuerty;
    private CustomKeyboard mKeyboardSymbols1;
    private CustomKeyboard mKeyboardSymbols2;
    private ViewGroup mVoiceInput;
    private Drawable mShiftOnIcon;
    private Drawable mShiftOffIcon;
    private View mFocusedView;
    private BrowserWidget mBrowserWidget;
    private InputConnection mInputConnection;
    private EditorInfo mEditorInfo = new EditorInfo();

    private boolean mIsPopupVisible = false;
    private RelativeLayout.LayoutParams mOriginalLayoutParams;
    private NavigationBarButton mKeyboardIcon;
    private int mKeyboardPopupLeftMargin;
    private ImageButton mCloseKeyboardButton;

    public KeyboardWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public KeyboardWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public KeyboardWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }


    private void initialize(Context aContext) {
        inflate(aContext, R.layout.keyboard, this);
        mKeyboardview = findViewById(R.id.keyboard);
        mVoiceInput = findViewById(R.id.keyboard_microphone);

        mKeyboardQuerty = new CustomKeyboard(aContext.getApplicationContext(), R.xml.keyboard_qwerty);
        mKeyboardSymbols1 = new CustomKeyboard(aContext.getApplicationContext(), R.xml.keyboard_symbols);
        mKeyboardSymbols2 = new CustomKeyboard(aContext.getApplicationContext(), R.xml.keyboard_symbols2);

        mKeyboardview.setPreviewEnabled(false);
        mKeyboardview.setKeyboard(mKeyboardQuerty);

        int[] featuredKeys = {' ', Keyboard.KEYCODE_DELETE, Keyboard.KEYCODE_DONE, Keyboard.KEYCODE_CANCEL, CustomKeyboard.KEYCODE_VOICE_INPUT};
        mKeyboardview.setFeaturedKeyBackground(R.drawable.keyboard_featured_button_background, featuredKeys);

        mKeyboardview.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                Log.e("VRB", "OnKeyListener: " + i + " " + keyEvent.toString());
                return false;
            }
        });
        mKeyboardview.setOnKeyboardActionListener(this);

        mShiftOnIcon = getResources().getDrawable(R.drawable.ic_icon_keyboard_shift_on, getContext().getTheme());
        mShiftOffIcon = getResources().getDrawable(R.drawable.ic_icon_keyboard_shift_off, getContext().getTheme());
        mCloseKeyboardButton = findViewById(R.id.keyboardCloseButton);
        mCloseKeyboardButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        mKeyboardIcon = findViewById(R.id.keyboard_icon);
        mKeyboardIcon.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mKeyboardview.setVisibility(View.VISIBLE);
                mVoiceInput.setVisibility(View.GONE);
            }
        });

        mKeyboardPopupLeftMargin = new Float(getResources().getDimension(
                R.dimen.keyboard_popup_left_margin)).intValue();

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mIsPopupVisible) {
                    mKeyboardview.setKeyboard(mKeyboardQuerty);
                    mKeyboardview.setLayoutParams(mOriginalLayoutParams);
                    mIsPopupVisible = false;

                    mCloseKeyboardButton.setVisibility(View.VISIBLE);
                }
            }
        });

        mKeyboardview.setKeyboard(mKeyboardQuerty);
        if (mOriginalLayoutParams != null)
            mKeyboardview.setLayoutParams(mOriginalLayoutParams);
        mKeyboardview.setVisibility(View.VISIBLE);
        mVoiceInput.setVisibility(View.GONE);
        mIsPopupVisible = false;

        SessionStore.get().addTextInputListener(this);
    }

    @Override
    public void releaseWidget() {
        SessionStore.get().removeTextInputListener(this);
        mBrowserWidget = null;
        super.releaseWidget();
    }

    @Override
    void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        Context context = getContext();
        aPlacement.width = WidgetPlacement.dpDimension(context, R.dimen.keyboard_width);
        aPlacement.height = WidgetPlacement.dpDimension(context, R.dimen.keyboard_height);
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.5f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(context, R.dimen.keyboard_distance_from_browser);
        aPlacement.translationY = -415;
        aPlacement.rotationAxisX = 1.0f;
        aPlacement.rotation = (float)Math.toRadians(WidgetPlacement.floatDimension(context, R.dimen.keyboard_world_rotation));
        aPlacement.worldWidth = WidgetPlacement.floatDimension(context, R.dimen.keyboard_world_width);
        aPlacement.visible = false;
    }

    public void setBrowserWidget(BrowserWidget aWidget) {
        mBrowserWidget = aWidget;
        if (mBrowserWidget != null) {
            mWidgetPlacement.parentHandle = mBrowserWidget.getHandle();
        }
    }

    public void updateFocusedView(View aFocusedView) {
        mFocusedView = aFocusedView;
        if (aFocusedView != null && aFocusedView.onCheckIsTextEditor()) {
            mInputConnection = aFocusedView.onCreateInputConnection(mEditorInfo);
            ((CustomKeyboard)mKeyboardview.getKeyboard()).setImeOptions(mEditorInfo.imeOptions);
        } else {
            mInputConnection = null;
        }

        boolean showKeyboard = mInputConnection != null;
        boolean keyboardIsVisible = this.getVisibility() == View.VISIBLE;
        if (showKeyboard != keyboardIsVisible) {
            getPlacement().visible = showKeyboard;
            mWidgetManager.updateWidget(this);
        }
    }

    public void dismiss() {
       if (mFocusedView != null) {
           mFocusedView.clearFocus();
       }
       mWidgetPlacement.visible = false;
       mWidgetManager.updateWidget(this);
    }


    @Override
    public void onPress(int primaryCode) {
        Log.d("VRB", "Keyboard onPress " + primaryCode);
    }

    @Override
    public void onLongPress(Keyboard.Key popupKey) {
        if (popupKey.popupCharacters != null) {
            mOriginalLayoutParams = (RelativeLayout.LayoutParams) mKeyboardview.getLayoutParams();
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);

            StringBuilder popupCharacters = new StringBuilder(popupKey.popupCharacters);
            switch (popupKey.codes[0]) {
                case 98:
                case 104:
                case 105:
                case 106:
                case 107:
                case 108:
                case 109:
                case 110:
                case 111:
                case 112:
                case 117:
                case 187:{
                    params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                    params.rightMargin = getWidth() - popupKey.x - mKeyboardPopupLeftMargin;
                    if (popupCharacters.length() > 6) {
                        popupCharacters.insert(5, popupCharacters.charAt(0));
                        popupCharacters.substring(1);
                    } else {
                        popupCharacters.reverse();
                    }
                }
                break;
                default:{
                    params.leftMargin = popupKey.x;
                }
            }

            CustomKeyboard popupKeyboard = new CustomKeyboard(getContext(), popupKey.popupResId,
                    popupCharacters, 6, 0);
            mKeyboardview.setKeyboard(popupKeyboard);
            params.topMargin= popupKey.y;
            mKeyboardview.setLayoutParams(params);
            mIsPopupVisible = true;

            mCloseKeyboardButton.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onRelease(int primaryCode) {

    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
        Log.d("VRB", "Keyboard onPress++ " + primaryCode);
        switch (primaryCode) {
            case Keyboard.KEYCODE_MODE_CHANGE:
                handleModeChange();
                break;
            case CustomKeyboard.KEYCODE_SYMBOLS_CHANGE:
                handleSymbolsChange();
                break;
            case Keyboard.KEYCODE_SHIFT:
                handleShift();
                break;
            case Keyboard.KEYCODE_DELETE:
                handleBackspace();
                break;
            case Keyboard.KEYCODE_DONE:
                handleDone();
                break;
            case CustomKeyboard.KEYCODE_VOICE_INPUT:
                handleVoiceInput();
                break;
            case CustomKeyboard.KEYCODE_STRING_COM:
                handleText(".com");
                break;
            default:
                handleKey(primaryCode, keyCodes);
                if (mIsPopupVisible) {
                    mKeyboardview.setKeyboard(mKeyboardQuerty);
                    mKeyboardview.setLayoutParams(mOriginalLayoutParams);
                    mIsPopupVisible = false;

                    mCloseKeyboardButton.setVisibility(View.VISIBLE);
                }
                break;
        }
    }

    @Override
    public void onText(CharSequence text) {

    }

    @Override
    public void swipeLeft() {

    }

    @Override
    public void swipeRight() {

    }

    @Override
    public void swipeDown() {

    }

    @Override
    public void swipeUp() {

    }

    private void handleShift() {
        Keyboard keyboard = mKeyboardview.getKeyboard();
        boolean shifted = !mKeyboardview.isShifted();
        int shiftIndex = keyboard.getShiftKeyIndex();
        if (shiftIndex >= 0) {
            // Update shift icon
            Keyboard.Key key = keyboard.getKeys().get(shiftIndex);
            if (key != null) {
                key.icon = shifted ? mShiftOnIcon : mShiftOffIcon;
            }
        }
        mKeyboardview.setShifted(shifted);
        if (mFocusedView != null) {
            ((CustomKeyboard)mKeyboardview.getKeyboard()).setImeOptions(mEditorInfo.imeOptions);
        }
    }

    private void handleBackspace() {
        final InputConnection connection = mInputConnection;
        postInputCommand(new Runnable() {
            @Override
            public void run() {
                CharSequence selectedText = mInputConnection.getSelectedText(0);
                if (selectedText == null || selectedText.length() == 0) {
                    // No selected text to delete. Remove the character before the cursor.
                    connection.deleteSurroundingText(1, 0);
                } else {
                    // Delete the selected text
                    connection.commitText("", 1);
                }
            }
        });

    }

    private void handleDone() {
        final InputConnection connection = mInputConnection;
        final int action = mEditorInfo.imeOptions & EditorInfo.IME_MASK_ACTION;
        postInputCommand(new Runnable() {
            @Override
            public void run() {
                connection.performEditorAction(action);

            }
        });

        boolean hide = (action & (EditorInfo.IME_ACTION_DONE | EditorInfo.IME_ACTION_GO |
                                 EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_ACTION_SEND)) != 0;
        if (hide && mFocusedView != null) {
            mFocusedView.clearFocus();
        }
    }


    private void handleModeChange() {
        Keyboard current = mKeyboardview.getKeyboard();
        mKeyboardview.setKeyboard(current == mKeyboardQuerty ? mKeyboardSymbols1 : mKeyboardQuerty);
    }

    private void handleSymbolsChange() {
        Keyboard current = mKeyboardview.getKeyboard();
        mKeyboardview.setKeyboard(current == mKeyboardSymbols1 ? mKeyboardSymbols2 : mKeyboardSymbols1);
    }

    private void handleKey(int primaryCode, int[] keyCodes) {
        if (mFocusedView == null || mInputConnection == null || primaryCode <= 0) {
            return;
        }

        String str = String.valueOf((char) primaryCode);
        if (mKeyboardview.isShifted() && Character.isLowerCase(str.charAt(0))) {
            str = str.toUpperCase();
        }
        final String result = str;
        final InputConnection connection = mInputConnection;
        postInputCommand(new Runnable() {
            @Override
            public void run() {
                connection.commitText(result, 1);
            }
        });
    }

    private void handleText(final String aText) {
        if (mFocusedView == null || mInputConnection == null) {
            return;
        }

        final InputConnection connection = mInputConnection;
        postInputCommand(new Runnable() {
            @Override
            public void run() {
                connection.commitText(aText, 1);
            }
        });
    }

    private void handleVoiceInput() {
        mKeyboardview.setVisibility(View.GONE);
        mVoiceInput.setVisibility(View.VISIBLE);
    }

    private void postInputCommand(Runnable aRunnable) {
        if (mInputConnection == null) {
            Log.e(LOGTAG, "InputConnection command not submitted, mInputConnection was null");
            return;
        }

        Handler handler = mInputConnection.getHandler();
        if (handler != null) {
            handler.post(aRunnable);
        } else {
            aRunnable.run();
        }
    }

    // GeckoSession.TextInputDelegate

    @Override
    public void restartInput(@NonNull GeckoSession session, int reason) {

    }

    @Override
    public void showSoftInput(@NonNull GeckoSession session) {
        if (mFocusedView != mBrowserWidget || getVisibility() != View.VISIBLE) {
            updateFocusedView(mBrowserWidget);
        }
    }

    @Override
    public void hideSoftInput(@NonNull GeckoSession session) {
        if (mFocusedView == mBrowserWidget && getVisibility() == View.VISIBLE) {
            dismiss();
        }
    }

    @Override
    public void updateSelection(@NonNull GeckoSession session, int selStart, int selEnd, int compositionStart, int compositionEnd) {
        if (mFocusedView != mBrowserWidget || mInputConnection == null) {
            return;
        }

        if (compositionStart >= 0 && compositionEnd >= 0) {
            mInputConnection.setComposingRegion(compositionStart, compositionEnd);
        }
        mInputConnection.setSelection(selStart, selEnd);
    }

    @Override
    public void updateExtractedText(@NonNull GeckoSession session, @NonNull ExtractedTextRequest request, @NonNull ExtractedText text) {

    }

    @Override
    public void updateCursorAnchorInfo(@NonNull GeckoSession session, @NonNull CursorAnchorInfo info) {

    }
}