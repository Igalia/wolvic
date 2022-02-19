/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.Keyboard;
import android.os.Handler;
import android.os.LocaleList;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.geckoview.GeckoSession;
import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.input.CustomKeyboard;
import com.igalia.wolvic.speech.SpeechRecognizer;
import com.igalia.wolvic.telemetry.TelemetryService;
import com.igalia.wolvic.ui.keyboards.ChinesePinyinKeyboard;
import com.igalia.wolvic.ui.keyboards.ChineseZhuyinKeyboard;
import com.igalia.wolvic.ui.keyboards.DanishKeyboard;
import com.igalia.wolvic.ui.keyboards.DutchKeyboard;
import com.igalia.wolvic.ui.keyboards.EnglishKeyboard;
import com.igalia.wolvic.ui.keyboards.FinnishKeyboard;
import com.igalia.wolvic.ui.keyboards.FrenchKeyboard;
import com.igalia.wolvic.ui.keyboards.GermanKeyboard;
import com.igalia.wolvic.ui.keyboards.ItalianKeyboard;
import com.igalia.wolvic.ui.keyboards.JapaneseKeyboard;
import com.igalia.wolvic.ui.keyboards.KeyboardInterface;
import com.igalia.wolvic.ui.keyboards.KoreanKeyboard;
import com.igalia.wolvic.ui.keyboards.NorwegianKeyboard;
import com.igalia.wolvic.ui.keyboards.PolishKeyboard;
import com.igalia.wolvic.ui.keyboards.RussianKeyboard;
import com.igalia.wolvic.ui.keyboards.SpanishKeyboard;
import com.igalia.wolvic.ui.keyboards.SwedishKeyboard;
import com.igalia.wolvic.ui.keyboards.ThaiKeyboard;
import com.igalia.wolvic.ui.views.AutoCompletionView;
import com.igalia.wolvic.ui.views.CustomKeyboardView;
import com.igalia.wolvic.ui.views.KeyboardSelectorView;
import com.igalia.wolvic.ui.widgets.dialogs.VoiceSearchWidget;
import com.igalia.wolvic.utils.StringUtils;
import com.igalia.wolvic.utils.ViewUtils;

import java.util.ArrayList;
import java.util.Locale;


public class KeyboardWidget extends UIWidget implements CustomKeyboardView.OnKeyboardActionListener, AutoCompletionView.Delegate,
        GeckoSession.TextInputDelegate, WidgetManagerDelegate.FocusChangeListener, VoiceSearchWidget.VoiceSearchDelegate, TextWatcher, WindowWidget.WindowListener {

    private static int MAX_CHARS_PER_POPUP_LINE = 10;

    private CustomKeyboardView mKeyboardView;
    private CustomKeyboardView mKeyboardNumericView;
    private CustomKeyboardView mPopupKeyboardView;
    private ArrayList<KeyboardInterface> mKeyboards;
    private KeyboardInterface mCurrentKeyboard;
    private CustomKeyboard mDefaultKeyboardSymbols;
    private CustomKeyboard mKeyboardNumeric;
    private Drawable mShiftOnIcon;
    private Drawable mShiftOffIcon;
    private Drawable mShiftDisabledIcon;
    private Drawable mCapsLockOnIcon;
    private View mFocusedView;
    private LinearLayout mKeyboardLayout;
    private RelativeLayout mKeyboardContainer;
    private WindowWidget mAttachedWindow;
    private InputConnection mInputConnection;
    private EditorInfo mEditorInfo = new EditorInfo();
    private VoiceSearchWidget mVoiceSearchWidget;
    private AutoCompletionView mAutoCompletionView;
    private KeyboardSelectorView mLanguageSelectorView;
    private KeyboardSelectorView mDomainSelectorView;
    private LinearLayout mControlButtons;
    private ImageView mAutocompletionLayer;
    private ImageView mKeyboardNumericLayer;

    private int mKeyWidth;
    private int mKeyboardPopupTopMargin;
    private ImageButton mCloseKeyboardButton;
    private ImageButton mKeyboardMoveButton;
    private boolean mIsLongPress;
    private boolean mIsMultiTap;
    private boolean mIsCapsLock;
    private ImageView mPopupKeyboardLayer;
    private boolean mIsInVoiceInput = false;
    private String mComposingText = "";
    private String mComposingDisplayText = "";
    private boolean mInternalDeleteHint = false;
    private Session mSession;
    private boolean mInputRestarted = false;
    public int mPopUpHoverDeviceId = -1;
    public int mLanguageHoverDeviceId = -1;
    public int mDomainHoverDeviceId = -1;
    public int mAutoCompleteHoverDeviceId = -1;
    public int mControlHoverDeviceId = -1;

    private class MoveTouchListener implements OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    mWidgetManager.startWidgetMove(KeyboardWidget.this, WidgetManagerDelegate.WIDGET_MOVE_BEHAVIOUR_KEYBOARD);
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    mWidgetManager.finishWidgetMove();
                    break;
                default:
                    return false;

            }
            return true;
        }
    }

    private class ControlButtonHoverListener implements OnHoverListener {
        @Override
        public boolean onHover(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    v.setHovered(true);
                    break;
                case MotionEvent.ACTION_HOVER_EXIT:
                    v.setHovered(false);
            }
            return false;
        }
    }

    private int updateLastDeviceId(View view, int deviceId, int x, int y, int value) {
        if (view.getVisibility() == VISIBLE && ViewUtils.isInsideView(view, x, y)) {
            if (value == -1) {
                return deviceId;
            }
        } else {
            if (deviceId == value) {
                return -1;
            }
        }

        return value;
    }

    public boolean isValidPointer(int deviceId, int eventDeviceId) {
        return deviceId == -1 || deviceId == eventDeviceId;
    }

    private void clearKeyboardHover() {
        if (mKeyboardView.isHovered()) {
            mKeyboardView.setHovered(false);
        }
        if (mKeyboardNumericView.isHovered()) {
            mKeyboardNumericView.setHovered(false);
        }
    }

    @Override
    public void handleHoverEvent(MotionEvent aEvent) {
        int x = (int)aEvent.getRawX();
        int y = (int)aEvent.getRawY();

        int prevAutocompleteDeviceId = mAutoCompleteHoverDeviceId;
        int prevControlButtonsDeviceId = mControlHoverDeviceId;

        mPopUpHoverDeviceId = updateLastDeviceId(mPopupKeyboardView, aEvent.getDeviceId(), x, y, mPopUpHoverDeviceId);
        mLanguageHoverDeviceId = updateLastDeviceId(mLanguageSelectorView, aEvent.getDeviceId(), x, y, mLanguageHoverDeviceId);
        mDomainHoverDeviceId = updateLastDeviceId(mDomainSelectorView, aEvent.getDeviceId(), x, y, mDomainHoverDeviceId);
        mAutoCompleteHoverDeviceId = updateLastDeviceId(mAutoCompletionView, aEvent.getDeviceId(), x, y, mAutoCompleteHoverDeviceId);
        mControlHoverDeviceId = updateLastDeviceId(mControlButtons, aEvent.getDeviceId(), x, y, mControlHoverDeviceId);

        if (mPopupKeyboardView.getVisibility() == VISIBLE) {
            if (isValidPointer(mPopUpHoverDeviceId, aEvent.getDeviceId()) &&
                    isValidPointer(mControlHoverDeviceId, aEvent.getDeviceId())) {
                clearKeyboardHover();
                super.handleHoverEvent(aEvent);
            }

        } else if (mLanguageSelectorView.getVisibility() == VISIBLE) {
            if (isValidPointer(mLanguageHoverDeviceId, aEvent.getDeviceId()) &&
                    isValidPointer(mControlHoverDeviceId, aEvent.getDeviceId())) {
                clearKeyboardHover();
                super.handleHoverEvent(aEvent);
            }

        } else if (mDomainSelectorView.getVisibility() == VISIBLE) {
            if (isValidPointer(mDomainHoverDeviceId, aEvent.getDeviceId()) &&
                    isValidPointer(mControlHoverDeviceId, aEvent.getDeviceId())) {
                clearKeyboardHover();
                super.handleHoverEvent(aEvent);
            }

        } else if (mAutoCompletionView.getVisibility() == VISIBLE) {
            if (isValidPointer(mAutoCompleteHoverDeviceId, aEvent.getDeviceId()) &&
                    isValidPointer(mControlHoverDeviceId, aEvent.getDeviceId())) {
                if (prevAutocompleteDeviceId != mAutoCompleteHoverDeviceId ||
                        prevControlButtonsDeviceId != mControlHoverDeviceId) {
                    clearKeyboardHover();
                }
                super.handleHoverEvent(aEvent);
            }

        } else {
            if (isValidPointer(mControlHoverDeviceId, aEvent.getDeviceId())) {
                if (prevControlButtonsDeviceId != mControlHoverDeviceId) {
                    clearKeyboardHover();
                }
                super.handleHoverEvent(aEvent);
            }
        }
    }

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

        mWidgetManager.addFocusChangeListener(this);

        mKeyboardView = findViewById(R.id.keyboard);
        mKeyboardNumericView = findViewById(R.id.keyboardNumeric);
        mPopupKeyboardView = findViewById(R.id.popupKeyboard);
        mPopupKeyboardLayer = findViewById(R.id.popupKeyboardLayer);
        mAutocompletionLayer = findViewById(R.id.autoCompletionOverlay);
        mKeyboardNumericLayer = findViewById(R.id.numericKeyboardOverlay);
        mLanguageSelectorView = findViewById(R.id.langSelectorView);
        mKeyboardLayout = findViewById(R.id.keyboardLayout);
        mKeyboardContainer = findViewById(R.id.keyboardContainer);
        mLanguageSelectorView.setDelegate(aItem -> handleLanguageChange((KeyboardInterface) aItem.tag));
        mAutoCompletionView = findViewById(R.id.autoCompletionView);
        mAutoCompletionView.setExtendedHeight((int)(mWidgetPlacement.height * mWidgetPlacement.density));
        mAutoCompletionView.setDelegate(this);
        mControlButtons = findViewById(R.id.controlButtons);

        mDomainSelectorView = findViewById(R.id.domainSelectorView);
        mDomainSelectorView.setDelegate(this::handleDomainChange);

        mKeyboards = new ArrayList<>();
        mKeyboards.add(new EnglishKeyboard(aContext));
        mKeyboards.add(new ChinesePinyinKeyboard(aContext));
        mKeyboards.add(new ChineseZhuyinKeyboard(aContext));
        mKeyboards.add(new JapaneseKeyboard(aContext));
        mKeyboards.add(new FrenchKeyboard(aContext));
        mKeyboards.add(new GermanKeyboard(aContext));
        mKeyboards.add(new SpanishKeyboard(aContext));
        mKeyboards.add(new RussianKeyboard(aContext));
        mKeyboards.add(new KoreanKeyboard(aContext));
        mKeyboards.add(new ItalianKeyboard(aContext));
        mKeyboards.add(new DanishKeyboard(aContext));
        mKeyboards.add(new PolishKeyboard(aContext));
        mKeyboards.add(new NorwegianKeyboard(aContext));
        mKeyboards.add(new SwedishKeyboard(aContext));
        mKeyboards.add(new FinnishKeyboard(aContext));
        mKeyboards.add(new DutchKeyboard(aContext));
        mKeyboards.add(new ThaiKeyboard(aContext));

        mDefaultKeyboardSymbols = new CustomKeyboard(aContext.getApplicationContext(), R.xml.keyboard_symbols);
        mKeyboardNumeric = new CustomKeyboard(aContext.getApplicationContext(), R.xml.keyboard_numeric);
        mShiftOnIcon = getResources().getDrawable(R.drawable.ic_icon_keyboard_shift_on, getContext().getTheme());
        mShiftOffIcon = getResources().getDrawable(R.drawable.ic_icon_keyboard_shift_off, getContext().getTheme());
        mShiftDisabledIcon = getResources().getDrawable(R.drawable.ic_icon_keyboard_shift_disabled, getContext().getTheme());
        setDefaultKeyboard();

        mKeyboardView.setPreviewEnabled(false);
        mKeyboardView.setKeyboard(mCurrentKeyboard.getAlphabeticKeyboard());
        mPopupKeyboardView.setPreviewEnabled(false);
        mPopupKeyboardView.setKeyBackground(getContext().getDrawable(R.drawable.keyboard_popupkey_background));
        mPopupKeyboardView.setKeyCapStartBackground(getContext().getDrawable(R.drawable.keyboard_popupkey_capstart_background));
        mPopupKeyboardView.setKeyCapEndBackground(getContext().getDrawable(R.drawable.keyboard_popupkey_capend_background));
        mPopupKeyboardView.setKeySingleStartBackground(getContext().getDrawable(R.drawable.keyboard_popupkey_single_background));
        mPopupKeyboardView.setKeyTextColor(getContext().getColor(R.color.asphalt));
        mPopupKeyboardView.setSelectedForegroundColor(getContext().getColor(R.color.fog));
        mPopupKeyboardView.setForegroundColor(getContext().getColor(R.color.fog));
        mPopupKeyboardView.setKeyboardHoveredPadding(0);
        mPopupKeyboardView.setKeyboardPressedPadding(0);

        mKeyboardNumericView.setPreviewEnabled(false);

        int[] featuredKeys = {
            ' ', Keyboard.KEYCODE_DELETE, Keyboard.KEYCODE_DONE, Keyboard.KEYCODE_CANCEL, Keyboard.KEYCODE_MODE_CHANGE,
            CustomKeyboard.KEYCODE_VOICE_INPUT, CustomKeyboard.KEYCODE_SYMBOLS_CHANGE, CustomKeyboard.KEYCODE_LANGUAGE_CHANGE,
        };
        mKeyboardView.setFeaturedKeyBackground(R.drawable.keyboard_featured_key_background, featuredKeys);

        mKeyboardView.setOnKeyListener((view, i, keyEvent) -> false);
        mPopupKeyboardView.setOnKeyListener((view, i, keyEvent) -> false);
        mKeyboardNumericView.setOnKeyListener((view, i, keyEvent) -> false);
        mKeyboardView.setOnKeyboardActionListener(this);
        mPopupKeyboardView.setOnKeyboardActionListener(this);
        mKeyboardNumericView.setOnKeyboardActionListener(this);

        mCapsLockOnIcon = getResources().getDrawable(R.drawable.ic_icon_keyboard_caps, getContext().getTheme());
        mCloseKeyboardButton = findViewById(R.id.keyboardCloseButton);
        mCloseKeyboardButton.setOnClickListener(v -> dismiss());
        mCloseKeyboardButton.setOnHoverListener(new ControlButtonHoverListener());
        mKeyboardMoveButton = findViewById(R.id.keyboardMoveButton);
        mKeyboardMoveButton.setOnTouchListener(new MoveTouchListener());
        mKeyboardMoveButton.setOnHoverListener(new ControlButtonHoverListener());
        mKeyWidth = getResources().getDimensionPixelSize(R.dimen.keyboard_key_width);
        mKeyboardPopupTopMargin  = getResources().getDimensionPixelSize(R.dimen.keyboard_key_pressed_padding) * 2;

        setOnClickListener(view -> hideOverlays());
        setOnTouchListener((v, event) -> {
            v.performClick();
            dismiss();
            return true;
        });

        mKeyboardView.setVisibility(View.VISIBLE);
        mKeyboardNumericView.setKeyboard(mKeyboardNumeric);

        mAutocompletionLayer.setOnClickListener(view -> hideOverlays());
        mKeyboardNumericLayer.setOnClickListener(view -> hideOverlays());
        mPopupKeyboardLayer.setOnClickListener(view -> hideOverlays());
        hideOverlays();

        mBackHandler = this::onDismiss;

        mAutoCompletionView = findViewById(R.id.autoCompletionView);
        mAutoCompletionView.setExtendedHeight((int)(mWidgetPlacement.height * mWidgetPlacement.density));
        mAutoCompletionView.setDelegate(this);

        updateCandidates();
    }

    @Override
    public void releaseWidget() {
        detachFromWindow();
        mWidgetManager.removeFocusChangeListener(this);
        mAutoCompletionView.setDelegate(null);
        mAttachedWindow = null;
        super.releaseWidget();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        Context context = getContext();
        aPlacement.width = getKeyboardWidth(WidgetPlacement.dpDimension(context, R.dimen.keyboard_alphabetic_width));
        aPlacement.height = getKeyboardHeight(WidgetPlacement.dpDimension(context, R.dimen.keyboard_height));
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.0f;
        aPlacement.parentAnchorY = 0.0f;
        aPlacement.translationX = WidgetPlacement.unitFromMeters(context, R.dimen.keyboard_x);
        aPlacement.translationY = WidgetPlacement.unitFromMeters(context, R.dimen.keyboard_y) - WidgetPlacement.unitFromMeters(context, R.dimen.window_world_y);
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(context, R.dimen.keyboard_z) - WidgetPlacement.unitFromMeters(context, R.dimen.window_world_z);
        aPlacement.rotationAxisX = 1.0f;
        aPlacement.rotation = (float)Math.toRadians(WidgetPlacement.floatDimension(context, R.dimen.keyboard_world_rotation));
        aPlacement.worldWidth = WidgetPlacement.floatDimension(context, R.dimen.keyboard_world_width);
        aPlacement.visible = false;
        aPlacement.cylinder = true;
        aPlacement.layerPriority = 1;
    }

    @Override
    public void detachFromWindow() {
        if (mAttachedWindow != null) {
            mAttachedWindow.removeWindowListener(this);
        }
        
        if (mSession != null) {
            mSession.removeTextInputListener(this);
            mSession = null;
        }
    }

    @Override
    public void attachToWindow(@NonNull WindowWidget aWindow) {
        if (mAttachedWindow == aWindow) {
            return;
        }
        mAttachedWindow = aWindow;
        mAttachedWindow.addWindowListener(this);
        mWidgetPlacement.parentHandle = aWindow.getHandle();

        mSession = aWindow.getSession();
        if (mSession != null) {
            mSession.addTextInputListener(this);
        }
    }

    private int getKeyboardWidth(float aAlphabeticWidth) {
        float width = aAlphabeticWidth;
        width += WidgetPlacement.dpDimension(getContext(), R.dimen.keyboard_layout_padding) * 2;
        width += WidgetPlacement.dpDimension(getContext(), R.dimen.keyboard_numeric_width);
        width += WidgetPlacement.dpDimension(getContext(), R.dimen.keyboard_key_width); // Close button
        return (int) width;
    }

     private int getKeyboardHeight(float aAlphabeticHeight) {
        float height = aAlphabeticHeight;
        height += WidgetPlacement.dpDimension(getContext(), R.dimen.autocompletion_widget_line_height);
        height += WidgetPlacement.dpDimension(getContext(), R.dimen.keyboard_layout_padding);
        return (int) height;
     }

    private void resetKeyboardLayout() {
        if ((mEditorInfo.inputType & EditorInfo.TYPE_CLASS_NUMBER) == EditorInfo.TYPE_CLASS_NUMBER) {
            mKeyboardView.setKeyboard(getSymbolsKeyboard());
        } else {
            mKeyboardView.setKeyboard(mCurrentKeyboard.getAlphabeticKeyboard());
        }
        handleShift(false);
        updateCandidates();
    }

    private boolean isAttachToWindowWidget() {
        return mFocusedView instanceof WindowWidget;
    }

    public void updateFocusedView(View aFocusedView) {
        if (mFocusedView != null && mFocusedView instanceof TextView) {
            ((TextView)mFocusedView).removeTextChangedListener(this);
        }
        mFocusedView = aFocusedView;
        if (aFocusedView != null && aFocusedView.onCheckIsTextEditor()) {
            mInputConnection = aFocusedView.onCreateInputConnection(mEditorInfo);
            resetKeyboardLayout();
            if (mFocusedView != null && mFocusedView instanceof TextView) {
                ((TextView)mFocusedView).addTextChangedListener(this);
            }
        } else {
            cleanComposingText();
            hideOverlays();
            mInputConnection = null;
        }

        boolean showKeyboard = mInputConnection != null;
        boolean keyboardIsVisible = this.getVisibility() == View.VISIBLE;
        if (showKeyboard != keyboardIsVisible) {
            if (showKeyboard) {
                mWidgetManager.pushBackHandler(mBackHandler);
                handleShowKeyboard(mCurrentKeyboard.getAlphabeticKeyboard());
            } else {
                mWidgetManager.popBackHandler(mBackHandler);
                mWidgetManager.keyboardDismissed();
            }
            getPlacement().visible = showKeyboard;
            mWidgetManager.updateWidget(this);
        }

        mCurrentKeyboard.clear();
        updateCandidates();
        updateSpecialKeyLabels();
    }

    public void dismiss() {
        if (mPopupKeyboardLayer.getVisibility() == VISIBLE ||
                mAutocompletionLayer.getVisibility() == VISIBLE ||
                mKeyboardNumericLayer.getVisibility() == VISIBLE) {
            hideOverlays();
            return;
        }

        exitVoiceInputMode();
        if (mFocusedView != null && mFocusedView != mAttachedWindow) {
            mFocusedView.clearFocus();
        }
        mWidgetPlacement.visible = false;
        mWidgetManager.updateWidget(this);

        mWidgetManager.popBackHandler(mBackHandler);

        mIsCapsLock = false;
        mIsLongPress = false;
        handleShift(false);
    }

    public void proxifyLayerIfNeeded(ArrayList<WindowWidget> aWindows) {
        if (!SettingsStore.getInstance(getContext()).getLayersEnabled()) {
            return;
        }
        boolean proxify = false;
        for (WindowWidget window: aWindows) {
            if (window.getPlacement().borderColor != 0) {
                proxify = true;
                break;
            }
        }
        if (mWidgetPlacement.proxifyLayer != proxify) {
            mWidgetPlacement.proxifyLayer = proxify;
            mWidgetManager.updateWidget(this);
        }
    }

    private void hideOverlays() {
        mPopupKeyboardView.setVisibility(View.GONE);
        mPopupKeyboardLayer.setVisibility(View.GONE);
        mAutocompletionLayer.setVisibility(View.GONE);
        mKeyboardNumericLayer.setVisibility(View.GONE);
        mLanguageSelectorView.setVisibility(View.GONE);
        mDomainSelectorView.setVisibility(View.GONE);

        mPopUpHoverDeviceId = -1;
        mLanguageHoverDeviceId = -1;
        mDomainHoverDeviceId = -1;
    }

    protected void onDismiss() {
        dismiss();
    }

    @Override
    public void onPress(int primaryCode) {
        Log.d(LOGTAG, "Keyboard onPress " + primaryCode);
    }

    @Override
    public void onLongPress(Keyboard.Key popupKey) {
        if (popupKey.popupCharacters != null) {
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);

            float totalWidth = WidgetPlacement.convertDpToPixel(getContext(), mCurrentKeyboard.getAlphabeticKeyboardWidth());
            totalWidth -= mKeyboardView.getPaddingLeft() + mKeyboardView.getPaddingRight();
            float keyX = popupKey.x + WidgetPlacement.convertDpToPixel(getContext(), mKeyWidth * 0.5f);
            boolean rightAligned = keyX >= totalWidth * 0.5f;
            StringBuilder popupCharacters = new StringBuilder(popupKey.popupCharacters);

            if (rightAligned) {
                params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                params.rightMargin = mKeyboardView.getWidth() - popupKey.x - mKeyWidth - mKeyboardNumericView.getPaddingRight();
                if (popupCharacters.length() > MAX_CHARS_PER_POPUP_LINE) {
                    popupCharacters.insert(MAX_CHARS_PER_POPUP_LINE - 1, popupCharacters.charAt(0));
                    popupCharacters.replace(0,1, String.valueOf(popupCharacters.charAt(popupCharacters.length()-1)));
                    popupCharacters.deleteCharAt(popupCharacters.length()-1);
                } else {
                    popupCharacters.reverse();
                }
            } else {
                params.leftMargin = popupKey.x + mKeyboardNumericView.getPaddingLeft();
            }
            params.topMargin = popupKey.y + mKeyboardPopupTopMargin + mKeyboardView.getPaddingTop();

            CustomKeyboard popupKeyboard = new CustomKeyboard(getContext(), popupKey.popupResId,
                    popupCharacters, MAX_CHARS_PER_POPUP_LINE, 0, getContext().getResources().getDimensionPixelSize(R.dimen.keyboard_vertical_gap));
            mPopupKeyboardView.setKeyboard(popupKeyboard);
            mPopupKeyboardView.setLayoutParams(params);
            mPopupKeyboardView.setShifted(mIsCapsLock || mKeyboardView.isShifted());
            mPopupKeyboardView.setVisibility(View.VISIBLE);
            mPopupKeyboardLayer.setVisibility(View.VISIBLE);
            mKeyboardNumericLayer.setVisibility(View.VISIBLE);
            mAutocompletionLayer.setVisibility(mAutoCompletionView.getVisibility());
            mPopUpHoverDeviceId = -1;

            mIsLongPress = true;
        } else if (popupKey.codes[0] == CustomKeyboard.KEYCODE_SHIFT) {
            mIsLongPress = !mIsCapsLock;
        } else if (popupKey.codes[0] == CustomKeyboard.KEYCODE_DOMAIN) {
            handleDomainLongPress();
            mIsLongPress = true;
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
        Log.d(LOGTAG, "Keyboard onPress++ " + primaryCode);
        switch (primaryCode) {
            case Keyboard.KEYCODE_MODE_CHANGE:
                handleModeChange();
                break;
            case Keyboard.KEYCODE_SHIFT:
                mIsCapsLock = mIsLongPress || mIsMultiTap;
                handleShift(!mKeyboardView.isShifted());
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
            case CustomKeyboard.KEYCODE_LANGUAGE_CHANGE:
                handleGlobeClick();
                break;
            case CustomKeyboard.KEYCODE_EMOJI:
                handleEmojiInput();
                break;
            case CustomKeyboard.KEYCODE_DOMAIN:
                handleDomain();
                break;
            case ' ':
                handleSpace();
                break;
            default:
                if (!mIsLongPress) {
                    handleKey(primaryCode, keyCodes);
                }

                if (!hasPopup) {
                    hideOverlays();
                }
                break;
        }

        mIsLongPress = false;
        mIsMultiTap = false;
    }

    @Override
    public void onNoKey() {
       hideOverlays();
    }

    @Override
    public void onText(CharSequence text) {
        if (!mIsLongPress) {
            handleText(text.toString());
        }

        mIsLongPress = false;
        mIsMultiTap = false;
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

    private void setDefaultKeyboard() {
        KeyboardInterface keyboard = getKeyboardForLocale(SettingsStore.getInstance(getContext()).getKeyboardLocale());
        if (keyboard != null) {
            handleLanguageChange(keyboard);
            return;
        }

        // If the user has not selected any keyboard, find the best match from system locales.
        LocaleList localeList = getResources().getConfiguration().getLocales();
        String[] supportedLocales = new String[mKeyboards.size()];
        for (int i = 0; i < mKeyboards.size(); ++i) {
            supportedLocales[i] = mKeyboards.get(i).getLocale().toLanguageTag();
        }
        Locale bestMatch = localeList.getFirstMatch(supportedLocales);
        keyboard = getKeyboardForLocale(bestMatch);
        if (keyboard == null) {
            // Fall back to english.
            keyboard = getKeyboardForLocale(Locale.ENGLISH);
        }
        handleLanguageChange(keyboard);
    }

    private KeyboardInterface getKeyboardForLocale(@Nullable Locale aLocale) {
        if (aLocale == null) {
            return null;
        }
        // Check perfect locale mach
        for (KeyboardInterface keyboard: mKeyboards) {
            if (keyboard.getLocale().equals(aLocale)) {
                return  keyboard;
            }
        }
        // Fall back to language check
        for (KeyboardInterface keyboard: mKeyboards) {
            if (keyboard.getLocale().getLanguage().equalsIgnoreCase(aLocale.getLanguage())) {
                return keyboard;
            }
        }
        return null;
    }

    private CustomKeyboard getSymbolsKeyboard() {
        CustomKeyboard custom = mCurrentKeyboard.getSymbolsKeyboard();
        return  custom != null ? custom : mDefaultKeyboardSymbols;
    }

    private void cleanComposingText() {
        if (mComposingText.length() > 0 && mInputConnection != null) {
            mComposingText = "";
            // Clear composited text when the keyboard is dismissed
            postInputCommand(() -> {
                displayComposingText("", ComposingAction.FINISH);
            });
        }
    }

    private void handleShift(boolean isShifted) {
        final boolean statusChanged = mKeyboardView.isShifted() != isShifted;

        if (mKeyboardView.getKeyboard() != getSymbolsKeyboard()) {
            if (isShifted || mIsCapsLock) {
                if (mCurrentKeyboard.getAlphabeticCapKeyboard() != null) {
                    mKeyboardView.setKeyboard(mCurrentKeyboard.getAlphabeticCapKeyboard());
                }

            } else if (mKeyboardView.getKeyboard() != mCurrentKeyboard.getAlphabeticKeyboard()) {
                mKeyboardView.setKeyboard(mCurrentKeyboard.getAlphabeticKeyboard());
            }
        }

        CustomKeyboard keyboard = (CustomKeyboard) mKeyboardView.getKeyboard();
        int[] shiftIndices = keyboard.getShiftKeyIndices();
        for (int shiftIndex: shiftIndices) {
            if (shiftIndex >= 0) {
                // Update shift icon
                Keyboard.Key key = keyboard.getKeys().get(shiftIndex);
                if (key != null) {
                    if (keyboard == getSymbolsKeyboard()) {
                        key.icon = mShiftDisabledIcon;
                        key.pressed = false;

                    } else if (mIsCapsLock) {
                        key.icon = mCapsLockOnIcon;
                        key.pressed = true;

                    } else {
                        key.icon = isShifted ? mShiftOnIcon : mShiftOffIcon;
                        key.pressed = false;
                    }
                }
            }
        }

        // setShifted trigger a full keyboard redraw.
        // To avoid this we only call setShifted if it's state has changed.
        if (statusChanged) {
            mKeyboardView.setShifted(isShifted || mIsCapsLock);
        }
    }

    private void handleBackspace() {
        final InputConnection connection = mInputConnection;
        if (mComposingText.length() > 0) {
            CharSequence selectedText = mInputConnection.getSelectedText(0);
            if (selectedText != null && selectedText.length() > 0) {
                // Clean composing text if selected when backspace is clicked
                mComposingText = "";
            } else {
                mComposingText = mComposingText.substring(0, mComposingText.length() - 1);
                mComposingText = mComposingText.trim();
            }
            updateCandidates();
            return;
        }

        postInputCommand(() -> {
            CharSequence selectedText = connection.getSelectedText(0);
            if (selectedText != null && selectedText.length() > 0) {
                // Delete the selected text
                connection.commitText("", 1);
                return;
            }

            if (mCurrentKeyboard.usesTextOverride()) {
                String beforeText = getTextBeforeCursor(connection);
                String newBeforeText = mCurrentKeyboard.overrideBackspace(beforeText);
                if (newBeforeText != null) {
                    // Replace whole before text
                    connection.deleteSurroundingText(beforeText.length(), 0);
                    connection.commitText(newBeforeText, 1);
                    return;
                }
            }
            // Remove the character before the cursor.
            connection.deleteSurroundingText(1, 0);
        });
    }

    private void handleGlobeClick() {
        if (mLanguageSelectorView.getItems() == null || mLanguageSelectorView.getItems().size() == 0) {
            ArrayList<KeyboardSelectorView.Item> items = new ArrayList<>();
            for (KeyboardInterface keyboard: mKeyboards) {
                items.add(new KeyboardSelectorView.Item(StringUtils.capitalize(keyboard.getKeyboardTitle()), keyboard));
            }
            mLanguageSelectorView.setItems(items);
        }
        mLanguageSelectorView.setSelectedItem(mCurrentKeyboard);
        mLanguageSelectorView.setVisibility(View.VISIBLE);
        mPopupKeyboardLayer.setVisibility(View.VISIBLE);
        mKeyboardNumericLayer.setVisibility(View.VISIBLE);
        mAutocompletionLayer.setVisibility(mAutoCompletionView.getVisibility());
        mLanguageHoverDeviceId = -1;
    }

    private void handleEmojiInput() {
        final KeyboardInterface.CandidatesResult candidates = mCurrentKeyboard.getEmojiCandidates(mComposingText);
        setAutoCompletionVisible(candidates != null && candidates.words.size() > 0);
        mAutoCompletionView.setItems(candidates != null ? candidates.words : null);
    }

    private void handleDomain() {
        if (!mIsLongPress) {
            handleText(mCurrentKeyboard.getDomains()[0], true);
        }
    }

    private void handleDomainLongPress() {
        ArrayList<KeyboardSelectorView.Item> items = new ArrayList<>();
        for (String item: mCurrentKeyboard.getDomains()) {
            items.add(new KeyboardSelectorView.Item(item, item));
        }
        mDomainSelectorView.setItems(items);

        mDomainSelectorView.setVisibility(View.VISIBLE);
        mPopupKeyboardLayer.setVisibility(View.VISIBLE);
        mKeyboardNumericLayer.setVisibility(View.VISIBLE);
        mAutocompletionLayer.setVisibility(mAutoCompletionView.getVisibility());
        mDomainHoverDeviceId = -1;
    }

    private void handleDomainChange(KeyboardSelectorView.Item aItem) {
        handleText(aItem.title, true);

        disableShift(getSymbolsKeyboard());
        handleShift(false);
        hideOverlays();
        updateCandidates();
    }

    private void handleLanguageChange(KeyboardInterface aKeyboard) {
        cleanComposingText();

        mCurrentKeyboard = aKeyboard;

        // For the case when switching from a symbol keyboard to a alphabetic keyboard.
        float currentHeight = 0.0f;
        if (mKeyboardView.getKeyboard() == mCurrentKeyboard.getSymbolsKeyboard()) {
            currentHeight = mCurrentKeyboard.getSymbolKeyboardHeight();
        } else {
            currentHeight = mCurrentKeyboard.getAlphabeticKeyboardHeight();
        }
        final int width = getKeyboardWidth(mCurrentKeyboard.getAlphabeticKeyboardWidth());
        final int height = getKeyboardHeight(currentHeight);
        if (width != mWidgetPlacement.width || height != mWidgetPlacement.height) {
            mWidgetPlacement.width = width;
            mWidgetPlacement.height = getKeyboardHeight(mCurrentKeyboard.getAlphabeticKeyboardHeight());
            mWidgetPlacement.translationY = mCurrentKeyboard.getKeyboardTranslateYInWorld() -
                                            WidgetPlacement.unitFromMeters(getContext(), R.dimen.window_world_y);
            float defaultWorldWidth = mCurrentKeyboard.getKeyboardWorldWidth();
            int defaultKeyboardWidth = getKeyboardWidth(mKeyboards.get(0).getAlphabeticKeyboardWidth());
            mWidgetPlacement.worldWidth = defaultWorldWidth * ((float) width / (float) defaultKeyboardWidth);
            mWidgetManager.updateWidget(this);
            ViewGroup.LayoutParams params = mKeyboardContainer.getLayoutParams();
            params.width = WidgetPlacement.convertDpToPixel(getContext(), mCurrentKeyboard.getAlphabeticKeyboardWidth());
            params.height = WidgetPlacement.convertDpToPixel(getContext(), mCurrentKeyboard.getAlphabeticKeyboardHeight());
            mKeyboardContainer.setLayoutParams(params);
        }

        final int pixelHeight = WidgetPlacement.convertDpToPixel(getContext(), mCurrentKeyboard.getAlphabeticKeyboardHeight());
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)mKeyboardLayout.getLayoutParams();
        if (pixelHeight != params.height) {
            // We can consider to make mKeyboardLayout height is the maximum of height value in the future
            // then we can avoid resize.
            params.height = pixelHeight;
        }
        params.topMargin = mCurrentKeyboard.supportsAutoCompletion() ? WidgetPlacement.pixelDimension(getContext(), R.dimen.keyboard_margin_top_without_autocompletion) : 0;
        mKeyboardLayout.setLayoutParams(params);

        SettingsStore.getInstance(getContext()).setSelectedKeyboard(aKeyboard.getLocale());
        mKeyboardView.setKeyboard(mCurrentKeyboard.getAlphabeticKeyboard());
        updateSpaceBarLanguageLabel();
        disableShift(getSymbolsKeyboard());
        mIsCapsLock = false;
        handleShift(false);
        hideOverlays();
        updateCandidates();

        String spaceText = mCurrentKeyboard.getSpaceKeyText(mComposingText).toUpperCase();
        mCurrentKeyboard.getAlphabeticKeyboard().setSpaceKeyLabel(spaceText);
    }

    private void disableShift(@NonNull CustomKeyboard keyboard) {
        int[] shiftIndices = keyboard.getShiftKeyIndices();
        for (int shiftIndex: shiftIndices) {
            if (shiftIndex >= 0) {
                Keyboard.Key key = keyboard.getKeys().get(shiftIndex);
                if (key != null) {
                    key.icon = mShiftDisabledIcon;
                    key.pressed = false;
                }
            }
        }
        keyboard.disableKeys(shiftIndices);
    }

    private void handleSpace() {
        if (mCurrentKeyboard.usesComposingText() && mComposingText.length() == 0) {
            // Do not compose text when space is clicked on an empty composed text
            final InputConnection connection = mInputConnection;
            postInputCommand(() -> connection.commitText(" ", 1));
        } else {
            handleText(" ");
        }
    }

    private void handleDone() {
        if (mComposingDisplayText.length() > 0) {
            // Finish current composing
            mComposingText = "";
            postInputCommand(() -> {
                displayComposingText(StringUtils.removeSpaces(mComposingDisplayText), ComposingAction.FINISH);
                postUICommand(this::updateCandidates);
            });
            return;
        }
        final InputConnection connection = mInputConnection;
        final int action = mEditorInfo.imeOptions & EditorInfo.IME_MASK_ACTION;
        postInputCommand(() -> connection.performEditorAction(action));

        boolean hide = (action == EditorInfo.IME_ACTION_DONE) || (action == EditorInfo.IME_ACTION_GO) ||
                (action == EditorInfo.IME_ACTION_SEARCH) || (action == EditorInfo.IME_ACTION_SEND);

        if (hide && mFocusedView != null) {
            mFocusedView.clearFocus();
        }
    }

    private void handleShowKeyboard(Keyboard aKeyboard) {
        Keyboard alphabetic = mCurrentKeyboard.getAlphabeticKeyboard();
        Keyboard alphabeticCap = mCurrentKeyboard.getAlphabeticCapKeyboard();
        final boolean switchToAlphabeticMode = aKeyboard == alphabetic || aKeyboard == alphabeticCap;

        mKeyboardView.setKeyboard(aKeyboard);
        mKeyboardView.setLayoutParams(mKeyboardView.getLayoutParams());

        // Adjust the layout of the keyboard container because it might be changed by alphabetic keyboards
        // which have various height.
        if (switchToAlphabeticMode) {
            ViewGroup.LayoutParams params = mKeyboardContainer.getLayoutParams();
            params.height = WidgetPlacement.convertDpToPixel(getContext(), mCurrentKeyboard.getAlphabeticKeyboardHeight());
            mKeyboardContainer.setLayoutParams(params);
        } else {
            ViewGroup.LayoutParams params = mKeyboardContainer.getLayoutParams();
            params.height = WidgetPlacement.convertDpToPixel(getContext(), mCurrentKeyboard.getSymbolKeyboardHeight());
            mKeyboardContainer.setLayoutParams(params);
        }
    }

    private void handleModeChange() {
        Keyboard current = mKeyboardView.getKeyboard();
        Keyboard alphabetic = mCurrentKeyboard.getAlphabeticKeyboard();
        Keyboard alphabeticCap = mCurrentKeyboard.getAlphabeticCapKeyboard();
        final boolean switchToSymbolMode = current == alphabetic || current == alphabeticCap;

        // We currently don't need to take care of presenting the space key label
        // on an alphabetic cap keyboard.
        if (current == alphabetic) {
            mCurrentKeyboard.getAlphabeticKeyboard().setSpaceKeyLabel("");
        }

        if (!switchToSymbolMode && alphabeticCap != null &&
                alphabeticCap.isShifted()) {
            alphabetic = alphabeticCap;
        }
        handleShowKeyboard(switchToSymbolMode ? getSymbolsKeyboard() : alphabetic);
    }

    private void handleKey(int primaryCode, int[] keyCodes) {
        if (mFocusedView == null || mInputConnection == null || primaryCode <= 0) {
            return;
        }

        String str = String.valueOf((char) primaryCode);
        if (mKeyboardView.isShifted() && Character.isLowerCase(str.charAt(0))) {
            str = str.toUpperCase();
        }
        handleText(str);
    }

    private void handleText(String aText) {
        handleText(aText, false);
    }

    private void handleText(String aText, boolean skipCase) {
        if (mFocusedView == null || mInputConnection == null) {
            return;
        }

        if (mKeyboardView.isShifted() && !skipCase) {
            aText = aText.toUpperCase();
        }

        final String text = aText;
        if (mCurrentKeyboard.usesComposingText()) {
            CharSequence seq = mInputConnection.getSelectedText(0);
            String selected = seq != null ? seq.toString() : "";
            if (selected.length() > 0 && StringUtils.removeSpaces(selected).contains(mComposingText)) {
                // Clean composing text if the text is selected.
                mComposingText = "";
            }
            mComposingText += text;
        } else if (mCurrentKeyboard.usesTextOverride()) {
            String beforeText = getTextBeforeCursor(mInputConnection);
            final String newBeforeText = mCurrentKeyboard.overrideAddText(beforeText, text);
            final InputConnection connection = mInputConnection;
            postInputCommand(() -> {
                if (newBeforeText != null) {
                    connection.deleteSurroundingText(beforeText.length(), 0);
                    connection.commitText(newBeforeText, 1);
                } else {
                    connection.commitText(text, 1);
                }
            });

        } else {
            final InputConnection connection = mInputConnection;
            postInputCommand(() -> connection.commitText(text, 1));
        }

        if (!mIsCapsLock) {
            handleShift(false);
        }

        updateCandidates();
    }

    private void handleVoiceInput() {
        if (mIsInVoiceInput) {
            return;
        }
        if (mVoiceSearchWidget == null) {
            mVoiceSearchWidget = createChild(VoiceSearchWidget.class, false);
            mVoiceSearchWidget.setPlacementForKeyboard(this.getHandle());
            mVoiceSearchWidget.setDelegate(this); // VoiceSearchDelegate
            mVoiceSearchWidget.setDelegate(() -> exitVoiceInputMode()); // DismissDelegate
        }
        mIsInVoiceInput = true;
        TelemetryService.voiceInputEvent();
        mVoiceSearchWidget.show(CLEAR_FOCUS);
        mWidgetPlacement.visible = false;
        mWidgetManager.updateWidget(this);
    }

    private String getTextBeforeCursor(InputConnection aConnection) {
        if (aConnection == null) {
            return "";
        }

        ExtractedText extracted = aConnection.getExtractedText(new ExtractedTextRequest(),0);
        if ((extracted == null) || extracted.text == null) {
            return "";
        }

        String fullText = extracted.text.toString();
        return aConnection.getTextBeforeCursor(fullText.length(),0).toString();
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

    private void postUICommand(Runnable aRunnable) {
        ((Activity)getContext()).runOnUiThread(aRunnable);
    }

    private void updateCandidates() {
        if (mInputConnection == null || !mCurrentKeyboard.supportsAutoCompletion()) {
            setAutoCompletionVisible(false);
            updateSpecialKeyLabels();
            return;
        }

        if (mCurrentKeyboard.usesComposingText()) {
            final KeyboardInterface.CandidatesResult candidates = mCurrentKeyboard.getCandidates(mComposingText);
            setAutoCompletionVisible(candidates != null && candidates.words.size() > 0);
            mAutoCompletionView.setItems(candidates != null ? candidates.words : null);
            if (candidates != null && candidates.action == KeyboardInterface.CandidatesResult.Action.AUTO_COMPOSE) {
                setAutoCompletionVisible(false);
                onAutoCompletionItemClick(candidates.words.get(0));
            } else if (candidates != null) {
                postInputCommand(() -> displayComposingText(candidates.composing, ComposingAction.DO_NOT_FINISH));
            } else {
                mComposingText = "";

                postInputCommand(() -> {
                    displayComposingText("", ComposingAction.FINISH);
                });
            }
        } else {
            String fullText = mInputConnection.getExtractedText(new ExtractedTextRequest(),0).text.toString();
            String beforeText = mInputConnection.getTextBeforeCursor(fullText.length(),0).toString();
            final KeyboardInterface.CandidatesResult candidates = mCurrentKeyboard.getCandidates(beforeText);
            setAutoCompletionVisible(candidates != null && candidates.words.size() > 0);
            mAutoCompletionView.setItems(candidates != null ? candidates.words : null);
        }

        updateSpecialKeyLabels();
    }

    private void updateSpecialKeyLabels() {
        String enterText = mCurrentKeyboard.getEnterKeyText(mEditorInfo.imeOptions, mComposingText);
        String modeChangeText = mCurrentKeyboard.getModeChangeKeyText();
        boolean changed = mCurrentKeyboard.getAlphabeticKeyboard().setEnterKeyLabel(enterText);

        if (mCurrentKeyboard.getAlphabeticCapKeyboard() != null) {
            mCurrentKeyboard.getAlphabeticCapKeyboard().setEnterKeyLabel(enterText);
        }
        CustomKeyboard symbolsKeyboard = getSymbolsKeyboard();
        changed |= symbolsKeyboard.setModeChangeKeyLabel(modeChangeText);
        symbolsKeyboard.setEnterKeyLabel(enterText);
        if (changed) {
            mKeyboardView.invalidateAllKeys();
        }

        if (!isVisible()) {
            updateSpaceBarLanguageLabel();
        }
    }

    private Runnable mHideSpaceBarLanguageLabel = () -> {
        mCurrentKeyboard.getAlphabeticKeyboard().setSpaceKeyLabel("");
        mKeyboardView.invalidateAllKeys();
    };

    private void updateSpaceBarLanguageLabel() {
        if (getHandler() != null) {
            getHandler().removeCallbacks(mHideSpaceBarLanguageLabel);
            getHandler().postDelayed(mHideSpaceBarLanguageLabel, 3000);
        }
    }

    private void setAutoCompletionVisible(boolean aVisible) {
        mAutoCompletionView.setVisibility(aVisible ? View.VISIBLE : View.GONE);
        mAutoCompleteHoverDeviceId = -1;
    }

    // Must be called in the input thread, see postInputCommand.
    enum ComposingAction {
        FINISH,
        DO_NOT_FINISH
    }
    private void displayComposingText(String aText, ComposingAction aAction) {
        if (mInputConnection == null) {
            return;
        }
        boolean succeeded = mInputConnection.setComposingText(aText, 1);
        if (!succeeded) {
            // Fix for InlineAutocompleteEditText failed setComposingText() calls
            String fullText = mInputConnection.getExtractedText(new ExtractedTextRequest(),0).text.toString();
            String beforeText = mInputConnection.getTextBeforeCursor(fullText.length(),0).toString();
            if (beforeText.endsWith(mComposingDisplayText)) {
                mInternalDeleteHint = true;
                mInputConnection.deleteSurroundingText(mComposingDisplayText.length(), 0);
            }
            mInputConnection.setComposingText(aText, 1);
        }
        mComposingDisplayText = aText;
        if (aAction == ComposingAction.FINISH) {
            mInputConnection.finishComposingText();
            mComposingText = "";
        }
    }

    private void moveCursor(final int direction) {
        EditText textView;
        if (mFocusedView != null && mFocusedView instanceof EditText) {
            textView = (EditText)mFocusedView;
            final int cursor = textView.getSelectionStart() + direction;
            if ((cursor <= textView.length()) && (cursor >= 0)) {
                textView.setSelection(cursor);
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(final KeyEvent event) {
        final int keyCode = event.getKeyCode();
        final InputConnection connection = mInputConnection;
        if (connection != null) {
            if (isAttachToWindowWidget()) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    return false;
                } else {
                    connection.sendKeyEvent(event);
                    hide(UIWidget.KEEP_WIDGET);
                }
                return true;
            }
            // Android Components do not support InputConnection.sendKeyEvent()
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                Log.e("reb", "key = " + KeyEvent.keyCodeToString(keyCode));

                switch (keyCode) {
                    case KeyEvent.KEYCODE_DEL:
                        handleBackspace();
                        return true;
                    case KeyEvent.KEYCODE_ENTER:
                    case KeyEvent.KEYCODE_NUMPAD_ENTER:
                        handleDone();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        moveCursor(-1);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        moveCursor(1);
                        return true;
                    default:
                        break;
                }
                if (event.getUnicodeChar() != 0) {
                    KeyCharacterMap map = event.getKeyCharacterMap();
                    String value = String.valueOf((char) map.get(keyCode, event.getMetaState()));
                    connection.commitText(value, 1);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean supportsMultipleInputDevices() {
        return true;
    }

    // GeckoSession.TextInputDelegate

    @Override
    public void restartInput(@NonNull GeckoSession session, int reason) {
        resetKeyboardLayout();
        mInputRestarted = true;
    }

    @Override
    public void showSoftInput(@NonNull GeckoSession session) {
        if (mFocusedView != mAttachedWindow || getVisibility() != View.VISIBLE || mInputRestarted) {
            post(() -> updateFocusedView(mAttachedWindow));
        }
        mInputRestarted = false;
    }

    @Override
    public void hideSoftInput(@NonNull GeckoSession session) {
        if (mFocusedView == mAttachedWindow && getVisibility() == View.VISIBLE) {
            dismiss();
        }
    }

    @Override
    public void updateSelection(@NonNull GeckoSession session, final int selStart, final int selEnd, final int compositionStart, final int compositionEnd) {
        if (mFocusedView != mAttachedWindow || mInputConnection == null) {
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

    // FocusChangeListener

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        updateFocusedView(newFocus);
    }


    // VoiceSearch Delegate
    @Override
    public void OnVoiceSearchResult(String aTranscription, float confidence) {
        if (aTranscription != null && !aTranscription.isEmpty()) {
            handleText(aTranscription);
        }
        exitVoiceInputMode();
    }

    @Override
    public void OnVoiceSearchError(@SpeechRecognizer.Callback.ErrorType int errorType) {
        exitVoiceInputMode();
    }

    private void exitVoiceInputMode() {
        if (mIsInVoiceInput && mVoiceSearchWidget != null) {
            mVoiceSearchWidget.hide(KEEP_WIDGET);
            mWidgetPlacement.visible = true;
            mWidgetManager.updateWidget(this);
            mIsInVoiceInput = false;
        }
    }

    // AutoCompletionDelegate

    @Override
    public void onAutoCompletionItemClick(final KeyboardInterface.Words aItem) {
        if (mFocusedView == null || mInputConnection == null) {
            return;
        }
        if (mCurrentKeyboard.usesComposingText()) {
            String code = StringUtils.removeSpaces(aItem.code);
            mComposingText = mCurrentKeyboard.getComposingText(mComposingText, code).trim();

            postInputCommand(() -> {
                displayComposingText(aItem.value, ComposingAction.FINISH);
                postUICommand(KeyboardWidget.this::updateCandidates);
            });

        } else {
            handleText(aItem.value);
        }
    }

    @Override
    public void onAutoCompletionExtendedChanged() {
        mKeyboardView.setVisibility(mAutoCompletionView.isExtended() ? View.INVISIBLE : View.VISIBLE);
    }

    // TextWatcher
    private String mTextBefore = "";
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        mTextBefore = s.toString();
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    @Override
    public void afterTextChanged(Editable aEditable) {
        if (mFocusedView == null || mInputConnection == null) {
            return;
        }

        if (!mInternalDeleteHint && mCurrentKeyboard.usesComposingText() && mComposingText.length() > 0 && mTextBefore.length() > 0 && aEditable.toString().length() == 0) {
            // Text has been cleared externally (e.g. URLBar text clear button)
            mComposingText = "";
            mCurrentKeyboard.clear();
            updateCandidates();
        }
        mInternalDeleteHint = false;
    }

    // WindowListener

    @Override
    public void onSessionChanged(@NonNull Session aOldSession, @NonNull Session aSession) {
        aOldSession.removeTextInputListener(this);
        aSession.addTextInputListener(this);
    }
}
