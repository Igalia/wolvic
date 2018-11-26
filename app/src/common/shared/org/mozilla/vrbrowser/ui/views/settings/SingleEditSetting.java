package org.mozilla.vrbrowser.ui.views.settings;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;

import androidx.annotation.StringRes;

public class SingleEditSetting extends LinearLayout {

    private AudioEngine mAudio;
    private String mDescription;
    private TextView mDescriptionView;
    protected TextView mText1;
    protected EditText mEdit1;
    protected TextView mButton;
    private OnClickListener mListener;
    private int mEditTextSelectedColor;
    private int mEditTextUnelectedColor;

    public SingleEditSetting(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SingleEditSetting(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.EditSetting, defStyleAttr, 0);
        mDescription = attributes.getString(R.styleable.EditSetting_description);
        attributes.recycle();

        initialize(context);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.setting_edit, this);

        mAudio = AudioEngine.fromContext(aContext);

        mDescriptionView = findViewById(R.id.setting_description);
        mDescriptionView.setText(mDescription);

        mText1 = findViewById(R.id.textValue1);
        mText1.setSoundEffectsEnabled(false);
        mText1.setOnClickListener(mText1ClickListener);

        mEdit1 = findViewById(R.id.editValue1);
        mEdit1.setSoundEffectsEnabled(false);

        mEdit1.setOnEditorActionListener(mInternalEditorActionListener);
        ColorStateList colors = mEdit1.getTextColors();
        mEditTextSelectedColor = colors.getColorForState(View.SELECTED_STATE_SET, R.color.fog);
        mEditTextUnelectedColor = colors.getColorForState(View.EMPTY_STATE_SET, R.color.asphalt);
        mEdit1.setOnTouchListener((v, event) -> updateTouchTextSelection(v));
        mEdit1.setOnFocusChangeListener((v, hasFocus) -> updateFocusTextSelection(v, hasFocus));
        mEdit1.addTextChangedListener(new TextColorTextWatcher(mEdit1));
        mEdit1.setOnClickListener(v -> mEdit1.selectAll());

        mButton = findViewById(R.id.settingButton);
        mButton.setSoundEffectsEnabled(false);
        mButton.setOnClickListener(mInternalClickListener);
    }

    private OnClickListener mText1ClickListener = v -> mButton.performClick();

    private View.OnClickListener mInternalClickListener = v -> onClickListener(v);

    protected TextView.OnEditorActionListener mInternalEditorActionListener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                mButton.callOnClick();
                return true;
            }

            return false;
        }
    };

    protected void onClickListener(View v) {
        if (mAudio != null) {
            mAudio.playSound(AudioEngine.Sound.CLICK);
        }

        mText1.setVisibility(mEdit1.getVisibility());
        mEdit1.setVisibility(mEdit1.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        @StringRes int buttonText = mEdit1.getVisibility() == View.VISIBLE ?
                R.string.developer_options_save : R.string.developer_options_edit;
        mButton.setText(buttonText);

        if (mListener != null) {
            mListener.onClick(v);
        }
    }

    public String getFirstText() {
        return mEdit1.getText().toString();
    }

    public void setFirstText(String text) {
        mText1.setText(text);
        mEdit1.setText(text);
    }

    public void setOnClickListener(OnClickListener aListener) {
        mListener = aListener;
    }

    public void cancel() {
        mText1.setVisibility(VISIBLE);
        mEdit1.setVisibility(View.GONE);
        @StringRes int buttonText = mEdit1.getVisibility() == View.VISIBLE ?
                R.string.developer_options_save : R.string.developer_options_edit;
        mButton.setText(buttonText);
    }

    public boolean isEditing() {
        return mEdit1.getVisibility() == View.VISIBLE;
    }

    protected boolean updateTouchTextSelection(View v) {
        EditText editText = (EditText) v;
        if (editText.hasSelection()) {
            editText.setTextColor(mEditTextSelectedColor);

        } else {
            editText.setTextColor(mEditTextUnelectedColor);
        }

        editText.requestFocusFromTouch();
        
        return false;
    }

    protected void updateFocusTextSelection(View v, boolean hasFocus) {
        EditText editText = (EditText) v;
        if (editText.hasSelection()) {
            if (hasFocus) {
                editText.setTextColor(mEditTextSelectedColor);

            } else {
                editText.setTextColor(mEditTextUnelectedColor);
            }

        } else {
            editText.setTextColor(mEditTextUnelectedColor);
        }
    }

    protected class TextColorTextWatcher implements TextWatcher {

        private EditText mEditText;

        public TextColorTextWatcher(EditText view) {
            mEditText = view;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            mEditText.setTextColor(mEditTextUnelectedColor);
        }

        @Override
        public void afterTextChanged(Editable s) {

        }
    }

}
