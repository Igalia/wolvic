package org.mozilla.vrbrowser.ui.views.settings;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;

import java.util.stream.Stream;

public class RadioGroupSetting extends LinearLayout {

    private static final int MARGIN = 0;

    public interface OnCheckedChangeListener {
        void onCheckedChanged(RadioGroup compoundButton, @IdRes int checkedId, boolean apply);
    }

    private AudioEngine mAudio;
    protected String mDescription;
    private CharSequence[] mOptions;
    private CharSequence[] mDescriptions;
    private Object[] mValues;
    protected RadioGroup mRadioGroup;
    protected TextView mRadioDescription;
    private OnCheckedChangeListener mRadioGroupListener;
    protected float mMargin;
    private @LayoutRes int mLayout;

    public RadioGroupSetting(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RadioGroupSetting(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.RadioGroupSetting, defStyleAttr, 0);
        mLayout = attributes.getResourceId(R.styleable.RadioGroupSetting_layout, R.layout.setting_radio_group);
        mDescription = attributes.getString(R.styleable.RadioGroupSetting_description);
        mOptions = attributes.getTextArray(R.styleable.RadioGroupSetting_options);
        mDescriptions = attributes.getTextArray(R.styleable.RadioGroupSetting_descriptions);
        mMargin = attributes.getDimension(R.styleable.RadioGroupSetting_itemMargin, getDefaultMargin());
        int id = attributes.getResourceId(R.styleable.RadioGroupSetting_values, 0);
        try {
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
            array.recycle();

        } catch (Resources.NotFoundException ignored) {

        }
        attributes.recycle();

        initialize(context, attrs, defStyleAttr, mLayout);
    }

    protected void initialize(Context aContext, AttributeSet attrs, int defStyleAttr, @LayoutRes int layout) {
        inflate(aContext, layout, this);

        mAudio = AudioEngine.fromContext(aContext);

        mRadioDescription = findViewById(R.id.setting_description);
        if (mRadioDescription != null) {
            mRadioDescription.setText(mDescription);
        }

        mRadioGroup = findViewById(R.id.radio_group);

        if (mOptions != null) {
            setOptions(Stream.of(mOptions).map(CharSequence::toString).toArray(String[]::new));
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

    public OnCheckedChangeListener getOnCheckedChangeListener() {
        return mRadioGroupListener;
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

    public void setOptions(@NonNull String[] options) {
        mRadioGroup.removeAllViews();

        for (int i=0; i<options.length; i++) {
            SpannableString spannable = new SpannableString(options[i]);
            if (mDescriptions != null && mDescriptions[i] != null) {
                spannable = new SpannableString(options[i] +
                        System.getProperty ("line.separator") +
                        mDescriptions[i]);
                int start = (options[i] + System.getProperty ("line.separator")).length();
                int end = spannable.length();
                spannable.setSpan(new RelativeSizeSpan(0.8f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new ForegroundColorSpan(
                                getResources().getColor(R.color.rhino, getContext().getTheme())),
                        start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            RadioButton button = new RadioButton(new ContextThemeWrapper(getContext(), R.style.radioButtonTheme), null, 0);
            button.setInputType(InputType.TYPE_NULL);
            button.setClickable(true);
            button.setId(i);
            button.setText(spannable);
            button.setSingleLine(false);
            button.setLayoutParams(getItemParams(i));
            mRadioGroup.addView(button);
        }
    }

    protected int getDefaultMargin() {
        return MARGIN;
    }

    protected LayoutParams getItemParams(int itemPos) {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

}
