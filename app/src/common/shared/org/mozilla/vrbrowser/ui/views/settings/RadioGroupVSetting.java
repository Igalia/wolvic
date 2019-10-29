package org.mozilla.vrbrowser.ui.views.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.widget.RadioButton;
import android.widget.RelativeLayout;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.R;

public class RadioGroupVSetting extends RadioGroupSetting {

    private int mMargin;

    public RadioGroupVSetting(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RadioGroupVSetting(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void initialize(Context aContext, AttributeSet attrs, int defStyleAttr, @LayoutRes int layout) {
        super.initialize(aContext, attrs, defStyleAttr, layout);

        TypedArray attributes = aContext.obtainStyledAttributes(attrs, R.styleable.RadioGroupSetting, defStyleAttr, 0);
        mMargin = attributes.getInteger(R.styleable.RadioGroupSetting_itemMargin, 20);

        for (int i=0; i<mRadioGroup.getChildCount(); i++) {
            RadioButton button = (RadioButton)mRadioGroup.getChildAt(i);
            button.setInputType(InputType.TYPE_NULL);
            LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            if (i == 0) {
                params.bottomMargin = mMargin;

            } else if (i == mRadioGroup.getChildCount()-1) {
                params.topMargin = mMargin;

            } else {
                params.topMargin = mMargin;
                params.bottomMargin = mMargin;
            }
            button.setLayoutParams(params);
        }

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)mRadioGroup.getLayoutParams();
        if (mDescription == null) {
            params.setMarginStart(0);
            mRadioDescription.setVisibility(GONE);

        }
    }

    @Override
    public void setOptions(@NonNull String[] options) {
        mRadioGroup.clearCheck();
        mRadioGroup.removeAllViews();

        for (int i=0; i<options.length; i++) {
            RadioButton button = new RadioButton(new ContextThemeWrapper(getContext(), R.style.radioButtonTheme), null, 0);
            button.setInputType(InputType.TYPE_NULL);
            button.setClickable(true);
            button.setId(i);
            button.setText(options[i]);
            LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            if (i == 0) {
                params.bottomMargin = mMargin;

            } else if (i == mRadioGroup.getChildCount()-1) {
                params.topMargin = mMargin;

            } else {
                params.topMargin = mMargin;
                params.bottomMargin = mMargin;
            }
            button.setLayoutParams(params);
            mRadioGroup.addView(button);
        }
    }

}
