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
import org.mozilla.vrbrowser.databinding.OptionsHeaderBinding;

public class SettingsHeader extends FrameLayout {

    private OptionsHeaderBinding mBinding;

    public SettingsHeader(@NonNull Context context) {
        super(context);

        initialize(context, null);
    }

    public SettingsHeader(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.SettingsHeader, 0, 0);

        initialize(context, attributes);
    }

    public SettingsHeader(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.SettingsHeader, defStyleAttr, 0);

        initialize(context, attributes);
    }

    public SettingsHeader(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.SettingsHeader, defStyleAttr, defStyleRes);

        initialize(context, attributes);
    }

    private void initialize(@NonNull Context aContext, @NonNull TypedArray attributes) {
        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_header, this, true);

        String title = attributes.getString(R.styleable.SettingsHeader_title);
        String description = attributes.getString(R.styleable.SettingsHeader_description);
        int helpVisibility = attributes.getInt(R.styleable.SettingsHeader_helpVisibility, VISIBLE);

        if (title != null) {
            mBinding.setTitle(title);
        }

        if (description != null) {
            mBinding.setDescription(description);
        } else {
            mBinding.displayLanguageDescription.setVisibility(View.GONE);
        }

        mBinding.setHelpVisibility(helpVisibility);
    }

    public void setBackClickListener(@NonNull View.OnClickListener listener) {
        mBinding.setBackClickListener(listener);
    }

    public void setHelpClickListener(@NonNull View.OnClickListener listener) {
        mBinding.setHelpClickListener(listener);
    }

    public void setTitle(@NonNull String text) {
        mBinding.setTitle(text);
    }

    public void setTitle(@StringRes int textRes) {
        mBinding.setTitle(getResources().getString(textRes));
    }

    public void setDescription(@NonNull String text) {
        mBinding.setDescription(text);
        mBinding.displayLanguageDescription.setVisibility(View.VISIBLE);
    }

    public void setDescription(@StringRes int textRes) {
        mBinding.setDescription(getResources().getString(textRes));
        mBinding.displayLanguageDescription.setVisibility(View.VISIBLE);
    }

}
