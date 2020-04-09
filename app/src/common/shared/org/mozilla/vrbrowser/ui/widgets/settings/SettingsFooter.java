package org.mozilla.vrbrowser.ui.widgets.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.databinding.OptionsFooterBinding;

public class SettingsFooter extends FrameLayout {

    private OptionsFooterBinding mBinding;

    public SettingsFooter(@NonNull Context context) {
        super(context);

        initialize(context, null, 0, 0);
    }

    public SettingsFooter(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        initialize(context, attrs, 0, 0);
    }

    public SettingsFooter(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        initialize(context, attrs, defStyleAttr, 0);
    }

    public SettingsFooter(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        initialize(context, attrs, defStyleAttr, defStyleRes);
    }

    private void initialize(@NonNull Context aContext, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_footer, this, true);

        TypedArray attributes = aContext.obtainStyledAttributes(attrs, R.styleable.SettingsFooter, defStyleAttr, defStyleRes);
        String buttonText = attributes.getString(R.styleable.SettingsFooter_buttonText);
        String description = attributes.getString(R.styleable.SettingsFooter_description);

        if (buttonText != null) {
            mBinding.resetButton.setButtonText(buttonText);
        }

        if (description != null) {
            mBinding.resetButton.setDescription(description);
        }
        attributes.recycle();
    }

    public void setFooterButtonClickListener(@NonNull View.OnClickListener listener) {
       mBinding.setResetClickListener(listener);
    }

    public void setFooterButtonText(String text) {
        mBinding.resetButton.setButtonText(text);
    }

    public void setFooterButtonText(@StringRes int textRes) {
        mBinding.resetButton.setButtonText(textRes);
    }

    public void setDescription(@StringRes int textRes) {
        mBinding.resetButton.setDescription(getContext().getString(textRes));
    }

    public void setFooterButtonVisibility(int visibility) {
        mBinding.resetButton.setFooterButtonVisibility(visibility);
    }
}
