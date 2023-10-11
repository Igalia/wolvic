package com.igalia.wolvic.ui.views.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.slider.Slider;
import com.igalia.wolvic.R;
import com.igalia.wolvic.audio.AudioEngine;
import com.igalia.wolvic.ui.views.UIButton;

public class SliderSetting extends LinearLayout {

    private AudioEngine mAudio;
    private final String mDescription;
    private final float mStepSize;
    private final float mInitialValue;
    private final float mValueFrom;
    private final float mValueTo;
    private TextView mSliderDescription;
    private Slider mSlider;
    private UIButton mHelpButton;
    private OnValueChangeListener mSliderListener = null;

    public interface OnValueChangeListener {
        void onValueChange(@NonNull Slider slider, float value, boolean fromUser);
    }

    public SliderSetting(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SliderSetting(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.SliderSetting, defStyleAttr, 0);
        mDescription = attributes.getString(R.styleable.SliderSetting_description);
        mStepSize = attributes.getFloat(R.styleable.SliderSetting_stepSize, 1.0f);
        mInitialValue = attributes.getFloat(R.styleable.SliderSetting_value, 0.0f);
        mValueFrom = attributes.getFloat(R.styleable.SliderSetting_valueFrom, 0.0f);
        mValueTo = attributes.getFloat(R.styleable.SliderSetting_valueTo, 1.0f);
        attributes.recycle();

        initialize(context);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.setting_slider, this);

        mAudio = AudioEngine.fromContext(aContext);

        mSliderDescription = findViewById(R.id.setting_description);
        mSliderDescription.setText(mDescription);

        mSlider = findViewById(R.id.settings_slider);
        mSlider.setStepSize(mStepSize);
        mSlider.setValue(mInitialValue);
        mSlider.setValueFrom(mValueFrom);
        mSlider.setValueTo(mValueTo);
        mSlider.clearOnChangeListeners();
        mSlider.addOnChangeListener(mInternalOnChangeListener);

        mSliderDescription = findViewById(R.id.settings_switch_text);
        mHelpButton = findViewById(R.id.settings_help_button);
    }

    private final Slider.OnChangeListener mInternalOnChangeListener = new Slider.OnChangeListener() {
        @Override
        public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            setValue(value, true);
            mSlider.requestFocus();
        }
    };

    public void setValue(float value, boolean doApply) {
        mSlider.clearOnChangeListeners();
        mSlider.setValue(value);
        mSlider.addOnChangeListener(mInternalOnChangeListener);

        if (mSliderListener != null && doApply) {
            mSliderListener.onValueChange(mSlider, value, doApply);
        }
    }

    public float getValue() {
        return mSlider.getValue();
    }

    public void setOnValueChangeListener(OnValueChangeListener aListener) {
        mSliderListener = aListener;
    }

    public String getDescription() {
        return mSliderDescription.getText().toString();
    }

    public void setHelpDelegate(Runnable aDelegate) {
        if (aDelegate != null) {
            mHelpButton.setVisibility(View.VISIBLE);
            mHelpButton.setOnClickListener(v -> aDelegate.run());
        } else {
            mHelpButton.setVisibility(View.GONE);
        }
    }

    public void setDescription(@NonNull String description) {
        mSliderDescription.setText(description);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        mSlider.setEnabled(enabled);
    }

    @Override
    public void setHovered(boolean hovered) {
        super.setHovered(hovered);

        mSlider.setHovered(hovered);
    }
}
