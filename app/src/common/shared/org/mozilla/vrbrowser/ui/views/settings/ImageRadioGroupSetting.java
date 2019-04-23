package org.mozilla.vrbrowser.ui.views.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;

import androidx.annotation.IdRes;

public class ImageRadioGroupSetting extends LinearLayout {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(@IdRes int checkedId, boolean apply);
    }

    private AudioEngine mAudio;
    private CharSequence[] mOptions;
    private Object[] mValues;
    private Drawable[] mImages;
    private OnCheckedChangeListener mRadioGroupListener;
    private ImageRadioButton[] mItems;

    public ImageRadioGroupSetting(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ImageRadioGroupSetting(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.RadioGroupSetting, defStyleAttr, 0);
        mOptions = attributes.getTextArray(R.styleable.RadioGroupSetting_options);
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
        array.recycle();

        id = attributes.getResourceId(R.styleable.RadioGroupSetting_images, 0);

        array = context.getResources().obtainTypedArray(id);
        mImages = new Drawable[mOptions.length];
        for (int i = 0; i < mOptions.length; ++i) {
            mImages[i] = array.getDrawable(i);
        }
        array.recycle();

        attributes.recycle();
        initialize(context);
    }

    protected void initialize(Context aContext) {
        mAudio = AudioEngine.fromContext(aContext);
        setOrientation(LinearLayout.VERTICAL);
        mItems = new ImageRadioButton[mOptions.length];

        for (int i= 0; i < mOptions.length; i++) {
            ImageRadioButton button = new ImageRadioButton(aContext);
            button.setValues(i, mOptions[i].toString(), mImages[i]);
            button.setChecked(false);
            final int checkedId = i;
            button.setOnClickListener(v -> {
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }
                setChecked(checkedId, true);

            });
            addView(button);
            mItems[i] = button;
        }
    }

    static class ImageRadioButton extends LinearLayout {
        private ImageView mImage;
        private RadioButton mRadioButton;
        public ImageRadioButton(Context context) {
            super(context);
            initialize(context);
        }

        private void initialize(Context aContext) {
            inflate(aContext, R.layout.setting_radio_item, this);
            mImage = findViewById(R.id.radioItemImage);
            mRadioButton = findViewById(R.id.radioItemButton);
            mRadioButton.setInputType(InputType.TYPE_NULL);
        }

        public void setValues(int aItemId, String aText, Drawable aImage) {
            mRadioButton.setId(aItemId);
            mRadioButton.setText(aText);
            if (aImage != null) {
                mImage.setImageDrawable(aImage);
            }
        }

        public void setChecked(boolean aChecked) {
            mRadioButton.setChecked(aChecked);
        }

        public boolean isChecked() {
            return mRadioButton.isChecked();
        }

        @Override
        public void setOnClickListener(View.OnClickListener aListener) {
            super.setOnClickListener(aListener);
            mRadioButton.setOnClickListener(aListener);
            mImage.setOnClickListener(aListener);
        }
    }

    public void setChecked(@IdRes int checkedId, boolean doApply) {
        for (int i = 0; i < mItems.length; i++) {
            mItems[i].setChecked(i == checkedId);
        }

        if (mRadioGroupListener != null && doApply) {
            mRadioGroupListener.onCheckedChanged(checkedId, doApply);
        }
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener aListener) {
        mRadioGroupListener = aListener;
    }

    public Object getValueForId(@IdRes int checkId) {
        return mValues[checkId];
    }

    public int getIdForValue(Object value) {
        for (int i = 0; i < mValues.length; i++) {
            if (mValues[i].equals(value)) {
                return i;
            }
        }

        return 0;
    }

    public int getCheckedRadioButtonId() {
        for (int i = 0; i < mItems.length; ++i) {
            if (mItems[i].isChecked()) {
                return i;
            }
        }
        return  -1;
    }

}
