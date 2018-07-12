package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.mozilla.vrbrowser.R;

public class SettingsButton extends LinearLayout {

    private ImageView mIcon;
    private TextView mText;
    private String mButtonText;
    private Drawable mButtonIcon;

    public SettingsButton(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.style.settingsButtonTheme);
    }

    public SettingsButton(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.SettingsButton, defStyleAttr, 0);
        mButtonText = attributes.getString(R.styleable.SettingsButton_settingsButtonText);
        mButtonIcon = attributes.getDrawable(R.styleable.SettingsButton_settingsButtonIcon);
        initialize(context);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.settings_btn, this);

        setClickable(true);
        setFocusable(true);

        mIcon = findViewById(R.id.settings_button_icon);
        if (mButtonIcon != null)
            mIcon.setImageDrawable(mButtonIcon);

        mText = findViewById(R.id.settings_button_text);
        if (mButtonText != null)
            mText.setText(mButtonText);

        setOnHoverListener(new OnHoverListener() {
            @Override
            public boolean onHover(View view, MotionEvent motionEvent) {
                return false;
            }
        });

        setSoundEffectsEnabled(false);
    }

    @Override
    public void setOnHoverListener(final OnHoverListener l) {
        super.setOnHoverListener(new OnHoverListener() {

            @Override
            public boolean onHover(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_HOVER_ENTER:
                        if (mIcon != null && mText != null) {
                            mIcon.setColorFilter(new PorterDuffColorFilter(getResources().getColor(R.color.asphalt, getContext().getTheme()), PorterDuff.Mode.MULTIPLY));
                            mText.setTextColor(getContext().getColor(R.color.asphalt));
                        }
                        break;
                    case MotionEvent.ACTION_HOVER_EXIT:
                        if (mIcon != null && mText != null) {
                            mIcon.setColorFilter(new PorterDuffColorFilter(getResources().getColor(R.color.fog, getContext().getTheme()), PorterDuff.Mode.MULTIPLY));
                            mText.setTextColor(getContext().getColor(R.color.fog));
                        }
                        break;
                }

                return l.onHover(view, motionEvent);
            }
        });
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return true;
    }
}
