package org.mozilla.vrbrowser.ui.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.mozilla.vrbrowser.utils.DeviceType;
import org.mozilla.vrbrowser.R;

import androidx.annotation.Nullable;

public class HoneycombButton extends LinearLayout {

    private static final String LOGTAG = "VRB";

    private ImageView mIcon;
    private TextView mText;
    private TextView mSecondaryText;
    private String mButtonText;
    private float mButtonTextSize;
    private String mSecondaryButtonText;
    private Drawable mButtonIcon;

    public HoneycombButton(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.style.honeycombButtonTheme);
    }

    public HoneycombButton(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.HoneycombButton, defStyleAttr, 0);
        mButtonText = attributes.getString(R.styleable.HoneycombButton_honeycombButtonText);
        mButtonTextSize = attributes.getDimension(R.styleable.HoneycombButton_honeycombButtonTextSize, 0.0f);
        mButtonIcon = attributes.getDrawable(R.styleable.HoneycombButton_honeycombButtonIcon);

        String iconIdStr = attributes.getString(R.styleable.HoneycombButton_honeycombButtonIcon);
        int deviceTypeId = DeviceType.getType();
        switch (deviceTypeId) {
          case DeviceType.OculusGo:
            if (attributes.hasValue(R.styleable.HoneycombButton_honeycombButtonIconOculus3dof)) {
              Log.d(LOGTAG, "Using Oculus 3DoF icon for '" + iconIdStr + "' honeycomb icon");
              try {
                mButtonIcon = attributes.getDrawable(R.styleable.HoneycombButton_honeycombButtonIconOculus3dof);
              } catch (Exception ex) {
                Log.d(LOGTAG, "Could not use Oculus 3DoF icon for '" + iconIdStr + "' honeycomb icon: " + ex.getMessage());
              }
            }
            break;
          case DeviceType.OculusQuest:
            if (attributes.hasValue(R.styleable.HoneycombButton_honeycombButtonIconOculus6dof)) {
              Log.d(LOGTAG, "Using Oculus 6DoF icon for '" + iconIdStr + "' honeycomb icon");
              try {
                mButtonIcon = attributes.getDrawable(R.styleable.HoneycombButton_honeycombButtonIconOculus6dof);
              } catch (Exception ex) {
                Log.d(LOGTAG, "Could not use Oculus 6DoF icon for '" + iconIdStr + "' honeycomb icon: " + ex.getMessage());
              }
            }
            break;
          default:
            if (attributes.hasValue(R.styleable.HoneycombButton_honeycombButtonIconOculus3dof) ||
                attributes.hasValue(R.styleable.HoneycombButton_honeycombButtonIconOculus6dof)) {
              Log.d(LOGTAG, "Using generic icon for '" + iconIdStr + "' honeycomb icon");
            }
            mButtonIcon = attributes.getDrawable(R.styleable.HoneycombButton_honeycombButtonIcon);
            break;
        }

        mSecondaryButtonText = attributes.getString(R.styleable.HoneycombButton_honeycombSecondaryText);
        initialize(context);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.honeycomb_button, this);

        setClickable(true);

        mIcon = findViewById(R.id.settings_button_icon);
        if (mIcon != null) {
            mIcon.setImageDrawable(mButtonIcon);
        }

        mText = findViewById(R.id.settings_button_text);
        if (mText != null) {
            mText.setText(mButtonText);
            if (mButtonTextSize > 0) {
                mText.getLayoutParams().width = (int) mButtonTextSize;
            }
        }

        mSecondaryText = findViewById(R.id.settings_secondary_text);
        if (mSecondaryText != null) {
            mSecondaryText.setText(mSecondaryButtonText);
        }

        setOnHoverListener((view, motionEvent) -> false);
    }

    @Override
    public void setOnHoverListener(final OnHoverListener l) {
        super.setOnHoverListener((view, motionEvent) -> {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    if (mIcon != null && mText != null) {
                        mIcon.setColorFilter(new PorterDuffColorFilter(getResources().getColor(R.color.asphalt, getContext().getTheme()), PorterDuff.Mode.MULTIPLY));
                        mText.setTextColor(getContext().getColor(R.color.asphalt));
                        mSecondaryText.setTextColor(getContext().getColor(R.color.asphalt));
                    }
                    break;
                case MotionEvent.ACTION_HOVER_EXIT:
                    if (mIcon != null && mText != null) {
                        mIcon.setColorFilter(new PorterDuffColorFilter(getResources().getColor(R.color.fog, getContext().getTheme()), PorterDuff.Mode.MULTIPLY));
                        mText.setTextColor(getContext().getColor(R.color.fog));
                        mSecondaryText.setTextColor(getContext().getColor(R.color.fog));
                    }
                    break;
            }

            return l.onHover(view, motionEvent);
        });
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return true;
    }
}
