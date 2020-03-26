package org.mozilla.vrbrowser.ui.views.settings;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

import androidx.annotation.LayoutRes;

public class RadioGroupVSetting extends RadioGroupSetting {

    private static final int MARGIN = 20;

    public RadioGroupVSetting(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RadioGroupVSetting(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void initialize(Context aContext, AttributeSet attrs, int defStyleAttr, @LayoutRes int layout) {
        super.initialize(aContext, attrs, defStyleAttr, layout);

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)mRadioGroup.getLayoutParams();
        if (mDescription == null) {
            params.setMarginStart(0);
            mRadioDescription.setVisibility(GONE);
        }
    }

    protected int getDefaultMargin() {
        return MARGIN;
    }

    protected LayoutParams getItemParams(int itemPos) {
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        if (itemPos == 0) {
            params.bottomMargin = (int)mMargin;

        } else if (itemPos == mRadioGroup.getChildCount()-1) {
            params.topMargin = (int)mMargin;

        } else {
            params.topMargin = (int)mMargin;
            params.bottomMargin = (int)mMargin;
        }

        return params;
    }

}
