package org.mozilla.vrbrowser.ui.settings;

import android.content.Context;
import android.content.res.TypedArray;
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
    }

    private View.OnClickListener mInternalClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            onClickListener(v);
        }
    };

    protected void onClickListener(View v) {
        if (mAudio != null) {
            mAudio.playSound(AudioEngine.Sound.CLICK);
        }

        if (mListener != null) {
            mListener.onClick(v);
        }
    }

    public void setOnClickListener(OnClickListener aListener) {
        mListener = aListener;
    }

}
