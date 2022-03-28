package com.igalia.wolvic.ui.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.igalia.wolvic.R;

public class HoneycombSwitch extends LinearLayout {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(CompoundButton compoundButton, boolean b);
    }

    private TextView mText;
    private TextView mStateText;
    private Switch mSwitch;
    private String mSwitchText;
    private float mSwitchTextSize;
    private OnCheckedChangeListener mSwitchListener;

    public HoneycombSwitch(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.style.honeycombButtonTheme);
    }

    public HoneycombSwitch(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.HoneycombSwitch, defStyleAttr, 0);
        mSwitchText = attributes.getString(R.styleable.HoneycombSwitch_honeycombSwitchText);
        mSwitchTextSize = attributes.getDimension(R.styleable.HoneycombSwitch_honeycombSwitchTextSize, 0.0f);
        initialize(context);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.honeycomb_switch, this);

        mSwitch = findViewById(R.id.honeycombSwitchButton);
        mSwitch.setOnCheckedChangeListener(mInternalSwitchListener);

        mText = findViewById(R.id.honeycombSwitchText);
        if (mText != null) {
            mText.setText(mSwitchText);
            if (mSwitchTextSize > 0) {
                mText.getLayoutParams().width = (int) mSwitchTextSize;
            }
        }

        mStateText = findViewById(R.id.honeycombSwitchStateText);
    }

    public void setChecked(boolean aChecked) {
        mSwitch.setChecked(aChecked);
    }

    private CompoundButton.OnCheckedChangeListener mInternalSwitchListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
            mStateText.setText(b ?
                    getContext().getString(R.string.on).toUpperCase() :
                    getContext().getString(R.string.off).toUpperCase());

            if (mSwitchListener != null) {
                mSwitchListener.onCheckedChanged(compoundButton, b);
            }

        }
    };

    public void setOnCheckedChangeListener(OnCheckedChangeListener aListener) {
        mSwitchListener = aListener;
    }

}
