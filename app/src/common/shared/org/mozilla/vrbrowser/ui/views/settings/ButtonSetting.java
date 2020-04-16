package org.mozilla.vrbrowser.ui.views.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.text.Spanned;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.utils.ViewUtils;

public class ButtonSetting extends LinearLayout {

    private AudioEngine mAudio;
    private String mDescription;
    private String mButtonText;
    private TextView mButton;
    private TextView mDescriptionView;
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

        mDescriptionView = findViewById(R.id.setting_description);
        mDescriptionView.setText(mDescription);

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

    public void setButtonText(@StringRes int aStringRes) {
        mButton.setText(aStringRes);
    }

    public void setDescription(String description) {
        mDescriptionView.setText(description);
    }

    public void setDescription(Spanned description) {
        mDescriptionView.setText(description);
    }

    public String getDescription() {
        return mDescriptionView.getText().toString();
    }

    public void setFooterButtonVisibility(int visibility) {
        mButton.setVisibility(visibility);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        mButton.setEnabled(enabled);
    }

    @Override
    public void setHovered(boolean hovered) {
        super.setHovered(hovered);

        mButton.setHovered(hovered);
    }

    public void setLinkClickListener(@NonNull ViewUtils.LinkClickableSpan listener) {
        ViewUtils.setTextViewHTML(mDescriptionView, mDescriptionView.getText().toString(), listener);
    }
}
