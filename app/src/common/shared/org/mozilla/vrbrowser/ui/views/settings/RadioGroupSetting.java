package org.mozilla.vrbrowser.ui.views.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;

public class RadioGroupSetting extends LinearLayout {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(RadioGroup compoundButton, @IdRes int checkedId, boolean apply);
    }

    private AudioEngine mAudio;
    private String mDecription;
    private CharSequence[] mOptions;
    private Object[] mValues;
    protected RadioGroup mRadioGroup;
    private TextView mRadioDescription;
    private OnCheckedChangeListener mRadioGroupListener;
    private @LayoutRes int mLayout;

    public RadioGroupSetting(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RadioGroupSetting(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.RadioGroupSetting, defStyleAttr, 0);
        mDecription = attributes.getString(R.styleable.RadioGroupSetting_description);
        mOptions = attributes.getTextArray(R.styleable.RadioGroupSetting_options);
        mLayout = attributes.getResourceId(R.styleable.RadioGroupSetting_layout, R.layout.setting_radio_group);
        int id = attributes.getResourceId(R.styleable.RadioGroupSetting_values, 0);
        TypedArray array = context.getResources().obtainTypedArray(id);
        if (array.getType(0) == TypedValue.TYPE_STRING) {
            mValues = getResources().getStringArray(id);

        } else if (array.getType(0) == TypedValue.TYPE_INT_HEX ||
                array.getType(0) == TypedValue.TYPE_INT_DEC) {
            int [] values = getResources().getIntArray(id);
            mValues = new Integer[values.length];
            for (int i=0; i<values.length; i++) {
                mValues[i] = values[i];
            }
        }
        attributes.recycle();

        initialize(context, attrs, defStyleAttr, mLayout);
    }

    protected void initialize(Context aContext, AttributeSet attrs, int defStyleAttr, @LayoutRes int layout) {
        inflate(aContext, layout, this);

        mAudio = AudioEngine.fromContext(aContext);

        mRadioDescription = findViewById(R.id.setting_description);
        if (mRadioDescription != null) {
            mRadioDescription.setText(mDecription);
        }

        mRadioGroup = findViewById(R.id.radio_group);
        mRadioGroup.setSoundEffectsEnabled(false);

        for (int i=0; i<mOptions.length; i++) {
            RadioButton button = new RadioButton(new ContextThemeWrapper(getContext(), R.style.radioButtonTheme), null, 0);
            button.setInputType(InputType.TYPE_NULL);
            button.setClickable(true);
            button.setId(i);
            button.setText(mOptions[i]);
            button.setSoundEffectsEnabled(false);
            mRadioGroup.addView(button);
        }

        mRadioGroup.setOnCheckedChangeListener(mInternalRadioListener);
    }

    private RadioGroup.OnCheckedChangeListener mInternalRadioListener = new RadioGroup.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(RadioGroup compoundButton, @IdRes int checkedId) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            setChecked(checkedId, true);
            compoundButton.requestFocus();
        }
    };

    public void setChecked(@IdRes int checkedId, boolean doApply) {
        mRadioGroup.setOnCheckedChangeListener(null);
        for (int i=0; i<mRadioGroup.getChildCount(); i++) {
            RadioButton button = (RadioButton) mRadioGroup.getChildAt(i);
            if (i == checkedId) {
                button.setChecked(true);

            } else {
                button.setChecked(false);
            }
        }
        mRadioGroup.setOnCheckedChangeListener(mInternalRadioListener);

        if (mRadioGroupListener != null && doApply) {
            mRadioGroupListener.onCheckedChanged(mRadioGroup, checkedId, doApply);
        }
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener aListener) {
        mRadioGroupListener = aListener;
    }

    public Object getValueForId(@IdRes int checkId) {
        return mValues[checkId];
    }

    public int getIdForValue(Object value) {
        for (int i=0; i<mValues.length; i++) {
            if (mValues[i].equals(value)) {
                return i;
            }
        }

        return 0;
    }

    public int getCheckedRadioButtonId() {
        return mRadioGroup.getCheckedRadioButtonId();
    }

}
