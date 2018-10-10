package org.mozilla.vrbrowser.ui.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.StringRes;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;

public class SingleEditSetting extends LinearLayout {

    private AudioEngine mAudio;
    private String mDescription;
    private TextView mDescriptionView;
    protected TextView mText1;
    protected EditText mEdit1;
    private TextView mButton;
    private OnClickListener mListener;

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

        mText1 = findViewById(R.id.text1);
        mEdit1 = findViewById(R.id.edit1);
        mEdit1.setSoundEffectsEnabled(false);

        mEdit1.setOnEditorActionListener(mInternalEditorActionListener);

        mButton = findViewById(R.id.settingButton);
        mButton.setOnClickListener(mInternalClickListener);
    }

    private View.OnClickListener mInternalClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            onClickListener(v);
        }
    };

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

}
