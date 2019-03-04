package org.mozilla.vrbrowser.ui.views.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.InputType;
import android.util.AttributeSet;
import android.widget.RadioButton;

import org.mozilla.vrbrowser.R;

import androidx.annotation.LayoutRes;

public class RadioGroupVSetting extends RadioGroupSetting {

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
        int margin = attributes.getInteger(R.styleable.RadioGroupSetting_itemMargin, 20);

        for (int i=0; i<mRadioGroup.getChildCount(); i++) {
            RadioButton button = (RadioButton)mRadioGroup.getChildAt(i);
            button.setInputType(InputType.TYPE_NULL);
            LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            params.topMargin = margin;
            params.bottomMargin = margin;
            button.setLayoutParams(params);
        }
    }

}
