package org.mozilla.vrbrowser.ui.views.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;

public class ButtonSetting extends LinearLayout {

    private AudioEngine mAudio;
    private String mDescription;
    private String mButtonText;
    private TextView mButton;
    private OnClickListener mListener;
    private Drawable mButtonBackground;
    private Drawable mButtonForeground;

    public ButtonSetting(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ButtonSetting(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.ButtonSetting, defStyleAttr, 0);
        mDescription = attributes.getString(R.styleable.ButtonSetting_description);
        mButtonText = attributes.getString(R.styleable.ButtonSetting_buttonText);
        attributes.recycle();

        initialize(context);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.setting_button, this);

        mAudio = AudioEngine.fromContext(aContext);

        TextView description = findViewById(R.id.setting_description);
        description.setText(mDescription);

        mButton = findViewById(R.id.button);
        mButton.setText(mButtonText);
        mButton.setOnClickListener(mInternalClickListener);
        mButtonBackground = mButton.getBackground();
        mButtonForeground = mButton.getForeground();
    }

    private View.OnClickListener mInternalClickListener = v -> onClickListener(v);

    protected void onClickListener(View v) {
        if (mAudio != null) {
            mAudio.playSound(AudioEngine.Sound.CLICK);
        }

        if (mListener != null) {
            mListener.onClick(v);
        }

        v.requestFocus();
    }

    public void setOnClickListener(OnClickListener aListener) {
        mListener = aListener;
    }

    public void setShowAsLabel(boolean aShowAsLabel) {
        if (aShowAsLabel) {
            mButton.setBackground(null);
            mButton.setForeground(null);
            mButton.setTextColor(getContext().getColor(R.color.fog));
        } else {
            mButton.setBackground(mButtonBackground);
            mButton.setForeground(mButtonForeground);
        }
    }

    public void setButtonText(String aText) {
        mButton.setText(aText);
    }

    public String getDescription() {
        return mDescription;
    }

}
