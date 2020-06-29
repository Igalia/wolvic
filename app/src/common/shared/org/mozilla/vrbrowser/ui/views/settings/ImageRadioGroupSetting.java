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

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;

import java.util.ArrayList;
import java.util.Arrays;

public class ImageRadioGroupSetting extends LinearLayout {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(@IdRes int checkedId, boolean apply);
    }

    private AudioEngine mAudio;
    private ArrayList<CharSequence> mOptions;
    private ArrayList<Object> mValues;
    private Drawable[] mImages;
    private OnCheckedChangeListener mRadioGroupListener;
    private ArrayList<ImageRadioButton> mItems;

    public ImageRadioGroupSetting(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ImageRadioGroupSetting(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.RadioGroupSetting, defStyleAttr, 0);
        mOptions = new ArrayList<>(Arrays.asList(attributes.getTextArray(R.styleable.RadioGroupSetting_options)));
        int id = attributes.getResourceId(R.styleable.RadioGroupSetting_values, 0);
        TypedArray array = context.getResources().obtainTypedArray(id);
        if (array.getType(0) == TypedValue.TYPE_STRING) {
            mValues = new ArrayList<>(Arrays.asList(getResources().getStringArray(id)));

        } else if (array.getType(0) == TypedValue.TYPE_INT_HEX ||
                array.getType(0) == TypedValue.TYPE_INT_DEC) {
            int [] values = getResources().getIntArray(id);
            mValues = new ArrayList<>(Arrays.asList(new Integer[values.length]));
            for (int value : values) {
                mValues.add(value);
            }
        }
        array.recycle();

        id = attributes.getResourceId(R.styleable.RadioGroupSetting_images, 0);

        array = context.getResources().obtainTypedArray(id);
        mImages = new Drawable[mOptions.size()];
        for (int i = 0; i < mOptions.size(); ++i) {
            mImages[i] = array.getDrawable(i);
        }
        array.recycle();

        attributes.recycle();
        initialize(context);
    }

    protected void initialize(Context aContext) {
        mAudio = AudioEngine.fromContext(aContext);
        setOrientation(LinearLayout.VERTICAL);
        mItems = new ArrayList<>();

        for (int i= 0; i < mOptions.size(); i++) {
            ImageRadioButton button = new ImageRadioButton(aContext);
            button.setValues(i, mOptions.get(i).toString(), mImages[i]);
            button.setChecked(false);
            final int checkedId = i;
            button.setOnClickListener(v -> {
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }
                setChecked(checkedId, true);

            });
            addView(button);
            mItems.add(button);
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
        for (int i = 0; i < mItems.size(); i++) {
            mItems.get(i).setChecked(i == checkedId);
        }

        if (mRadioGroupListener != null && doApply) {
            mRadioGroupListener.onCheckedChanged(checkedId, doApply);
        }
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener aListener) {
        mRadioGroupListener = aListener;
    }

    public Object getValueForId(@IdRes int checkId) {
        return mValues.get(checkId);
    }

    public int getIdForValue(Object value) {
        for (int i = 0; i < mValues.size(); i++) {
            if (mValues.get(i).equals(value)) {
                return i;
            }
        }

        return 0;
    }

    public int getCheckedRadioButtonId() {
        for (int i = 0; i < mItems.size(); ++i) {
            if (mItems.get(i).isChecked()) {
                return i;
            }
        }
        return  -1;
    }

    public void addOption(@NonNull Object value, @NonNull String title, Drawable thumbnail) {
        int id = mItems.size();
        ImageRadioButton button = new ImageRadioButton(getContext());
        button.setValues(id, title, thumbnail);
        button.setChecked(false);
        final int checkedId = id;
        button.setOnClickListener(v -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            setChecked(checkedId, true);

        });
        addView(button);

        mValues.add(value);
        mOptions.add(title);
        mItems.add(button);
    }

    public void updateOption(@NonNull Object value, @NonNull String title, Drawable thumbnail) {
        int index = mValues.indexOf(value);
        mOptions.set(index, title);
        mItems.get(index).mImage.setImageDrawable(thumbnail);
    }

}
