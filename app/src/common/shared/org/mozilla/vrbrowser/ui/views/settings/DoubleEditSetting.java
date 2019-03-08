package org.mozilla.vrbrowser.ui.views.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.InputFilter;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import org.mozilla.vrbrowser.R;

public class DoubleEditSetting extends SingleEditSetting {

    private String mBy;
    private TextView mText2;
    private SettingsEditText mEdit2;
    private String mDefaultSecondValue;

    public DoubleEditSetting(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DoubleEditSetting(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.EditSetting, defStyleAttr, 0);
        mBy = attributes.getString(R.styleable.EditSetting_by);
        attributes.recycle();

        initialize(context);
    }

    private void initialize(Context aContext) {
        TextView by = findViewById(R.id.settingBy);
        by.setText(mBy);
        by.setVisibility(View.VISIBLE);

        mText2 = findViewById(R.id.textValue2);
        mText2.setSoundEffectsEnabled(false);
        mText2.setOnClickListener(mText2ClickListener);

        mEdit2 = findViewById(R.id.editValue2);
        mEdit2.setHighlightedTextColor(mHighlightedTextColor);
        mEdit2.setHighlightedTextColor(mHighlightedTextColor);
        mEdit2.setOnClickListener(view -> {
            if (mEdit2.getText().toString().equals(mEdit2.getHint())) {
                mEdit2.requestFocus();
                mEdit2.selectAll();
            }
        });
        mEdit2.setSoundEffectsEnabled(false);
        if (mMaxLength != 0) {
            mEdit2.setFilters(new InputFilter[]{
                    new InputFilter.LengthFilter(mMaxLength)
            });
        }
        if (mInputType != InputType.TYPE_NULL) {
            mEdit2.setInputType(mInputType);
        }
        if (mWidth > 0) {
            mEdit2.setWidth((int)mWidth);
        }

        mEdit2.setOnEditorActionListener(mInternalEditorActionListener);
    }

    private OnClickListener mText2ClickListener = v -> mButton.performClick();

    protected void onClickListener(View v) {
        mText2.setVisibility(mEdit2.getVisibility());
        mEdit2.setVisibility(mEdit2.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);

        super.onClickListener(v);
    }

    public void setDefaultSecondValue(String value) {
        mDefaultSecondValue = value;
    }

    public String getSecondText() {
        if (mDefaultSecondValue != null) {
            return mEdit2.getText().toString().equals(mEdit2.getHint()) ? mDefaultSecondValue : mEdit2.getText().toString();
        } else {
            return mEdit2.getText().toString();
        }
    }

    public void setSecondText(String text) {
        if (text.equals(mDefaultSecondValue)) {
            mText2.setText(mEdit2.getHint());
            mEdit2.setText(mEdit2.getHint());

        } else {
            mText2.setText(text);
            mEdit2.setText(text);
        }
    }

    public void setHint2(String hint) {
        mEdit2.setHint(hint);
    }

    @Override
    public void cancel() {
        super.cancel();

        mText2.setVisibility(VISIBLE);
        mEdit2.setVisibility(View.GONE);

        if (mEdit2.length() == 0) {
            setSecondText(mDefaultSecondValue != null ? mDefaultSecondValue : mText2.getText().toString());
        }
    }

}
