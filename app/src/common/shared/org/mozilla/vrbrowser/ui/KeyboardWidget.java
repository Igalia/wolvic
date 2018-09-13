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

import android.widget.ImageView;
import android.widget.RelativeLayout;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.SessionStore;
import org.mozilla.vrbrowser.WidgetPlacement;
import org.mozilla.vrbrowser.telemetry.TelemetryWrapper;


public class KeyboardWidget extends UIWidget implements CustomKeyboardView.OnKeyboardActionListener, GeckoSession.TextInputDelegate {

    private static final String LOGTAG = "VRB";

    private static int MAX_CHARS_PER_LINE_LONG = 9;
    private static int MAX_CHARS_PER_LINE_SHORT = 7;

    private CustomKeyboardView mKeyboardview;
    private CustomKeyboardView mPopupKeyboardview;
    private CustomKeyboard mKeyboardQuerty;
    private CustomKeyboard mKeyboardSymbols1;
    private CustomKeyboard mKeyboardSymbols2;
    private ViewGroup mVoiceInput;
    private Drawable mShiftOnIcon;
    private Drawable mShiftOffIcon;
    private Drawable mCapsLockOnIcon;
    private View mFocusedView;
    private BrowserWidget mBrowserWidget;
    private InputConnection mInputConnection;
    private EditorInfo mEditorInfo = new EditorInfo();

    private UIButton mKeyboardIcon;
    private int mKeyboardPopupLeftMargin;
    private int mKeyboardPopupTopMargin;
    private ImageButton mCloseKeyboardButton;
    private boolean mIsLongPress;
    private boolean mIsMultiTap;
    private boolean mIsCapsLock;
    private ImageView mPopupKeyboardLayer;

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
        mPopupKeyboardview = findViewById(R.id.popupKeyboard);
        mVoiceInput = findViewById(R.id.keyboard_microphone);
        mPopupKeyboardLayer = findViewById(R.id.popupKeyboardLayer);

        mKeyboardQuerty = new CustomKeyboard(aContext.getApplicationContext(), R.xml.keyboard_qwerty);
        mKeyboardSymbols1 = new CustomKeyboard(aContext.getApplicationContext(), R.xml.keyboard_symbols);
        mKeyboardSymbols2 = new CustomKeyboard(aContext.getApplicationContext(), R.xml.keyboard_symbols2);

        mKeyboardview.setPreviewEnabled(false);
        mKeyboardview.setKeyboard(mKeyboardQuerty);
        mPopupKeyboardview.setPreviewEnabled(false);
        mPopupKeyboardview.setKeyBackground(getContext().getDrawable(R.drawable.keyboard_popupkey_background));
        mPopupKeyboardview.setKeyCapStartBackground(getContext().getDrawable(R.drawable.keyboard_popupkey_capstart_background));
        mPopupKeyboardview.setKeyCapEndBackground(getContext().getDrawable(R.drawable.keyboard_popupkey_capend_background));
        mPopupKeyboardview.setKeySingleStartBackground(getContext().getDrawable(R.drawable.keyboard_popupkey_single_background));
        mPopupKeyboardview.setKeyTextColor(getContext().getColor(R.color.asphalt));
        mPopupKeyboardview.setSelectedForegroundColor(getContext().getColor(R.color.fog));
        mPopupKeyboardview.setForegroundColor(getContext().getColor(R.color.fog));
        mPopupKeyboardview.setKeyboardHoveredPadding(0);
        mPopupKeyboardview.setKeyboardPressedPadding(0);

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mPopupKeyboardview.setVisibility(View.GONE);
                mPopupKeyboardLayer.setVisibility(View.GONE);
            }
        });

        int[] featuredKeys = {' ', Keyboard.KEYCODE_DELETE, Keyboard.KEYCODE_DONE, Keyboard.KEYCODE_CANCEL, CustomKeyboard.KEYCODE_VOICE_INPUT, CustomKeyboard.KEYCODE_SYMBOLS_CHANGE};
        mKeyboardview.setFeaturedKeyBackground(R.drawable.keyboard_featured_key_background, featuredKeys);

        mKeyboardview.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                return false;
            }
        });
        mPopupKeyboardview.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                return false;
            }
        });
        mKeyboardview.setOnKeyboardActionListener(this);
        mPopupKeyboardview.setOnKeyboardActionListener(this);

        mShiftOnIcon = getResources().getDrawable(R.drawable.ic_icon_keyboard_shift_on, getContext().getTheme());
        mShiftOffIcon = getResources().getDrawable(R.drawable.ic_icon_keyboard_shift_off, getContext().getTheme());
        mCapsLockOnIcon = getResources().getDrawable(R.drawable.ic_icon_keyboard_caps, getContext().getTheme());
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

        mKeyboardPopupLeftMargin = getResources().getDimensionPixelSize(R.dimen.keyboard_popup_left_margin);
        mKeyboardPopupTopMargin  = getResources().getDimensionPixelSize(R.dimen.keyboard_key_pressed_padding) * 2;

        mPopupKeyboardLayer.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mPopupKeyboardview.setVisibility(View.GONE);
                mPopupKeyboardLayer.setVisibility(View.GONE);
            }
        });

        mKeyboardview.setKeyboard(mKeyboardQuerty);
        mKeyboardview.setVisibility(View.VISIBLE);
        mPopupKeyboardview.setVisibility(View.GONE);
        mPopupKeyboardLayer.setVisibility(View.GONE);
        mVoiceInput.setVisibility(View.GONE);

        mBackHandler = new Runnable() {
            @Override
            public void run() {
                onBackButton();
            }
        };

        SessionStore.get().addTextInputListener(this);
    }

    @Override
    public void releaseWidget() {
        SessionStore.get().removeTextInputListener(this);
        mBrowserWidget = null;
        super.releaseWidget();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        Context context = getContext();
        aPlacement.width = WidgetPlacement.dpDimension(context, R.dimen.keyboard_width);
        aPlacement.height = WidgetPlacement.dpDimension(context, R.dimen.keyboard_height);
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.0f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(context, R.dimen.keyboard_z_distance_from_browser);
        aPlacement.translationY = WidgetPlacement.unitFromMeters(context, R.dimen.keyboard_y_distance_from_browser);
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
            if ((mEditorInfo.inputType & EditorInfo.TYPE_CLASS_NUMBER) == EditorInfo.TYPE_CLASS_NUMBER)
                mKeyboardview.setKeyboard(mKeyboardSymbols1);
            else
                mKeyboardview.setKeyboard(mKeyboardQuerty);

        } else {
            mInputConnection = null;
        }

        boolean showKeyboard = mInputConnection != null;
        boolean keyboardIsVisible = this.getVisibility() == View.VISIBLE;
        if (showKeyboard != keyboardIsVisible) {
            if (showKeyboard) {
                mWidgetManager.pushBackHandler(mBackHandler);
            } else {
                mWidgetManager.popBackHandler(mBackHandler);
                mWidgetManager.keyboardDismissed();
            }
            getPlacement().visible = showKeyboard;
            mWidgetManager.updateWidget(this);
        }
    }

    public void dismiss() {
       if (mFocusedView != null && mFocusedView != mBrowserWidget) {
           mFocusedView.clearFocus();
       }
       mWidgetPlacement.visible = false;
       mWidgetManager.updateWidget(this);

       mIsCapsLock = false;
       mIsLongPress = false;
       handleShift(false);

       mPopupKeyboardview.setVisibility(View.GONE);
       mPopupKeyboardLayer.setVisibility(View.GONE);
    }

    protected void onBackButton() {
        dismiss();
    }

    @Override
    public void onPress(int primaryCode) {
        Log.d("VRB", "Keyboard onPress " + primaryCode);
    }

    @Override
    public void onLongPress(Keyboard.Key popupKey) {
        if (popupKey.popupCharacters != null) {
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);

            boolean rightAligned = false;
            int maxCharsPerLine = MAX_CHARS_PER_LINE_LONG;
            StringBuilder popupCharacters = new StringBuilder(popupKey.popupCharacters);
            switch (popupKey.codes[0]) {
                case 110: {
                    rightAligned = true;
                    maxCharsPerLine = MAX_CHARS_PER_LINE_SHORT;
                }
                break;
                case 98:
                case 104:
                case 105:
                case 106:
                case 107:
                case 108:
                case 109:
                case 111:
                case 112:
                case 117:
                case 187:{
                    rightAligned = true;
                    maxCharsPerLine = MAX_CHARS_PER_LINE_LONG;
                }
            }

            if (rightAligned) {
                params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                params.rightMargin = getWidth() - popupKey.x - mKeyboardPopupLeftMargin;
                if (popupCharacters.length() > maxCharsPerLine) {
                    popupCharacters.insert(maxCharsPerLine - 1, popupCharacters.charAt(0));
                    popupCharacters.replace(0,1, String.valueOf(popupCharacters.charAt(popupCharacters.length()-1)));
                    popupCharacters.deleteCharAt(popupCharacters.length()-1);
                } else {
                    popupCharacters.reverse();
                }
            } else {
                params.leftMargin = popupKey.x;
            }
            params.topMargin = popupKey.y + mKeyboardPopupTopMargin;

            CustomKeyboard popupKeyboard = new CustomKeyboard(getContext(), popupKey.popupResId,
                    popupCharacters, maxCharsPerLine, 0);
            mPopupKeyboardview.setKeyboard(popupKeyboard);
            mPopupKeyboardview.setLayoutParams(params);
            mPopupKeyboardview.setShifted(mIsCapsLock);
            mPopupKeyboardview.setVisibility(View.VISIBLE);
            mPopupKeyboardLayer.setVisibility(View.VISIBLE);

            mIsLongPress = true;

        } else if (popupKey.codes[0] == CustomKeyboard.KEYCODE_SHIFT) {
            mIsLongPress = !mIsCapsLock;

        } else if (popupKey.codes[0] == Keyboard.KEYCODE_DELETE) {
            handleBackspace(true);
        }
    }

    public void onMultiTap(Keyboard.Key key) {
        mIsMultiTap = true;
    }

    @Override
    public void onRelease(int primaryCode) {

    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes, boolean hasPopup) {
        Log.d("VRB", "Keyboard onPress++ " + primaryCode);
        switch (primaryCode) {
            case Keyboard.KEYCODE_MODE_CHANGE:
                handleModeChange();
                break;
            case CustomKeyboard.KEYCODE_SYMBOLS_CHANGE:
                handleSymbolsChange();
                break;
            case Keyboard.KEYCODE_SHIFT:
                mIsCapsLock = mIsLongPress || mIsMultiTap;
                handleShift(!mKeyboardview.isShifted());
                break;
            case Keyboard.KEYCODE_DELETE:
                handleBackspace(false);
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
                if (!mIsLongPress) {
                    handleKey(primaryCode, keyCodes);
                }

                if (!hasPopup) {
                    mPopupKeyboardview.setVisibility(View.GONE);
                    mPopupKeyboardLayer.setVisibility(View.GONE);
                }
                break;
        }

        if (!mIsCapsLock && primaryCode != CustomKeyboard.KEYCODE_SHIFT) {
            handleShift(false);
        }

        mIsLongPress = false;
        mIsMultiTap = false;
    }

    @Override
    public void onNoKey() {
        mPopupKeyboardview.setVisibility(View.GONE);
        mPopupKeyboardLayer.setVisibility(View.GONE);
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

    private void handleShift(boolean isShifted) {
        CustomKeyboard keyboard = (CustomKeyboard)mKeyboardview.getKeyboard();
        boolean shifted = isShifted;
        int[] shiftIndices = keyboard.getShiftKeyIndices();
        for (int shiftIndex: shiftIndices) {
            if (shiftIndex >= 0) {
                // Update shift icon
                Keyboard.Key key = keyboard.getKeys().get(shiftIndex);
                if (key != null) {
                    if (mIsCapsLock) {
                        key.icon = mCapsLockOnIcon;
                        key.pressed = true;

                    } else {
                        key.icon = shifted ? mShiftOnIcon : mShiftOffIcon;
                        key.pressed = false;
                    }
                }
            }
        }
        mKeyboardview.setShifted(shifted || mIsCapsLock);
        if (mFocusedView != null) {
            ((CustomKeyboard)mKeyboardview.getKeyboard()).setImeOptions(mEditorInfo.imeOptions);
        }
    }

    private void handleBackspace(final boolean isLongPress) {
        final InputConnection connection = mInputConnection;
        postInputCommand(new Runnable() {
            @Override
            public void run() {
                CharSequence selectedText = mInputConnection.getSelectedText(0);
                if (selectedText == null || selectedText.length() == 0) {
                    if (isLongPress) {
                        CharSequence currentText = connection.getExtractedText(new ExtractedTextRequest(), 0).text;
                        CharSequence beforeCursorText = connection.getTextBeforeCursor(currentText.length(), 0);
                        CharSequence afterCursorText = connection.getTextAfterCursor(currentText.length(), 0);
                        connection.deleteSurroundingText(beforeCursorText.length(), afterCursorText.length());

                    } else {
                        // No selected text to delete. Remove the character before the cursor.
                        connection.deleteSurroundingText(1, 0);
                    }

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
        TelemetryWrapper.voiceInputEvent();
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
    public void updateSelection(@NonNull GeckoSession session, final int selStart, final int selEnd, final int compositionStart, final int compositionEnd) {
        if (mFocusedView != mBrowserWidget || mInputConnection == null) {
            return;
        }

        final InputConnection connection = mInputConnection;
        postInputCommand(new Runnable() {
            @Override
            public void run() {
                if (compositionStart >= 0 && compositionEnd >= 0) {
                    connection.setComposingRegion(compositionStart, compositionEnd);
                }
                connection.setSelection(selStart, selEnd);
            }
        });
    }

    @Override
    public void updateExtractedText(@NonNull GeckoSession session, @NonNull ExtractedTextRequest request, @NonNull ExtractedText text) {

    }

    @Override
    public void updateCursorAnchorInfo(@NonNull GeckoSession session, @NonNull CursorAnchorInfo info) {

    }

    @Override
    public void notifyAutoFill(GeckoSession session, int notification, int virtualId) {

    }
}