package org.mozilla.vrbrowser.ui.views.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.InputFilter;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;

public class SingleEditSetting extends LinearLayout {

    private AudioEngine mAudio;
    private String mDescription;
    protected int mMaxLength;
    protected float mWidth;
    protected int mInputType;
    private TextView mDescriptionView;
    private TextView mText1;
    private SettingsEditText mEdit1;
    protected TextView mButton;
    private OnClickListener mListener;
    protected int mHighlightedTextColor;
    private String mDefaultFirstValue;

    public SingleEditSetting(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SingleEditSetting(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.EditSetting, defStyleAttr, 0);
        mDescription = attributes.getString(R.styleable.EditSetting_description);
        mMaxLength = attributes.getInt(R.styleable.EditSetting_android_maxLength, 0);
        mWidth = attributes.getDimension(R.styleable.EditSetting_android_width, 0.0f);
        mInputType = attributes.getInt(R.styleable.EditSetting_android_inputType, InputType.TYPE_NULL);
        mHighlightedTextColor = attributes.getColor(R.styleable.EditSetting_highlightedTextColor, 0);
        initialize(context);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.setting_edit, this);

        mAudio = AudioEngine.fromContext(aContext);

        mDescriptionView = findViewById(R.id.setting_description);
        mDescriptionView.setText(mDescription);

        mText1 = findViewById(R.id.textValue1);
        mText1.setOnClickListener(mText1ClickListener);

        mEdit1 = findViewById(R.id.editValue1);
        mEdit1.setHighlightedTextColor(mHighlightedTextColor);
        mEdit1.setOnEditorActionListener(mInternalEditorActionListener);
        mEdit1.setOnClickListener(view -> {
            if (mEdit1.getText().toString().equals(mEdit1.getHint())) {
                mEdit1.requestFocus();
                mEdit1.selectAll();
            }
        });
        if (mMaxLength != 0) {
            mEdit1.setFilters(new InputFilter[]{
                    new InputFilter.LengthFilter(mMaxLength)
            });
        }
        if (mInputType != InputType.TYPE_NULL) {
            mEdit1.setInputType(mInputType);
        }
        if (mWidth > 0) {
            mEdit1.setWidth((int)mWidth);
        }

        mButton = findViewById(R.id.settingButton);
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

        mEdit1.requestFocus();
    }

    public void setDefaultFirstValue(String value) {
        mDefaultFirstValue = value;
    }

    public String getFirstText() {
        return mEdit1.getText().toString().equals(mEdit1.getHint()) ? mDefaultFirstValue : mEdit1.getText().toString();
    }

    public void setFirstText(String text) {
        if (text.equals(mDefaultFirstValue)) {
            mText1.setText(mEdit1.getHint());
            mEdit1.setText(mEdit1.getHint());
            mEdit1.requestFocus();
            mEdit1.selectAll();

        } else {
            mText1.setText(text);
            mEdit1.setText(text);
        }
    }

    public void setHint1(String hint) {
        mEdit1.setHint(hint);
    }

    public void setOnClickListener(OnClickListener aListener) {
        mListener = aListener;
    }

    public void cancel() {
        mText1.setVisibility(VISIBLE);
        mEdit1.setVisibility(View.GONE);
        @StringRes int buttonText = mEdit1.getVisibility() == View.VISIBLE ?
                R.string.developer_options_save : R.string.developer_options_edit;
        if (mEdit1.length() == 0) {
            setFirstText(mDefaultFirstValue != null ? mDefaultFirstValue : mText1.getText().toString());
        }
        mButton.setText(buttonText);
    }

    public boolean isEditing() {
        return mEdit1.getVisibility() == View.VISIBLE;
    }

    public boolean contains(@NonNull View view) {
        return findViewById(view.getId()) == view;
    }

}
