package org.mozilla.vrbrowser.ui.views.settings;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RadioButton;

import androidx.annotation.LayoutRes;

public class RadioGroupVSetting extends RadioGroupSetting {

    public RadioGroupVSetting(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RadioGroupVSetting(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void initialize(Context aContext, @LayoutRes int layout) {
        super.initialize(aContext, layout);

        for (int i=0; i<mRadioGroup.getChildCount(); i++) {
            RadioButton button = (RadioButton)mRadioGroup.getChildAt(i);
            LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            params.topMargin = 20;
            params.bottomMargin = 20;
            button.setLayoutParams(params);
        }
    }

}
