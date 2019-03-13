package org.mozilla.vrbrowser.ui.views.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.ui.views.UIButton;

public class SwitchSetting extends LinearLayout {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(CompoundButton compoundButton, boolean b, boolean apply);
    }

    private AudioEngine mAudio;
    private String mText;
    private Switch mSwitch;
    private TextView mSwitchText;
    private TextView mSwitchDescription;
    private OnCheckedChangeListener mSwitchListener;
    private String mOnText;
    private String mOffText;
    private UIButton mHelpButton;

    public SwitchSetting(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwitchSetting(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.SwitchSetting, defStyleAttr, 0);
        mText = attributes.getString(R.styleable.SwitchSetting_description);
        attributes.recycle();

        initialize(context);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.setting_switch, this);

        mAudio = AudioEngine.fromContext(aContext);
        mOnText = aContext.getString(R.string.on);
        mOffText = aContext.getString(R.string.off);

        mSwitchDescription = findViewById(R.id.setting_description);
        mSwitchDescription.setText(mText);

        mSwitch = findViewById(R.id.settings_switch);
        mSwitch.setOnCheckedChangeListener(mInternalSwitchListener);
        mSwitch.setSoundEffectsEnabled(false);

        mSwitchText = findViewById(R.id.settings_switch_text);
        mHelpButton = findViewById(R.id.settings_help_button);
    }

    private CompoundButton.OnCheckedChangeListener mInternalSwitchListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            setValue(b, true);
            mSwitch.requestFocus();
        }
    };

    public void setValue(boolean value, boolean doApply) {

        mSwitch.setOnCheckedChangeListener(null);
        mSwitch.setChecked(value);
        mSwitch.setOnCheckedChangeListener(mInternalSwitchListener);
        updateSwitchText();

        if (mSwitchListener != null && doApply) {
            mSwitchListener.onCheckedChanged(mSwitch, value, doApply);
        }
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener aListener) {
        mSwitchListener = aListener;
    }

    public boolean isChecked() {
        return mSwitch.isChecked();
    }

    public void setChecked(boolean value) {
        mSwitch.setChecked(value);
    }


    public String getDescription() {
        return mSwitchDescription.getText().toString();
    }

    private void updateSwitchText() {
        mSwitchText.setText(mSwitch.isChecked() ? mOnText : mOffText);
    }

    public void setOnText(String aText) {
        mOnText = aText;
        updateSwitchText();
    }

    public void setOffText(String aText) {
        mOffText = aText;
        updateSwitchText();
    }

    public void setHelpDelegate(Runnable aDelegate) {
        if (aDelegate != null) {
            mHelpButton.setVisibility(View.VISIBLE);
            mHelpButton.setOnClickListener(v -> aDelegate.run());
        } else {
            mHelpButton.setVisibility(View.GONE);
        }
    }

}
