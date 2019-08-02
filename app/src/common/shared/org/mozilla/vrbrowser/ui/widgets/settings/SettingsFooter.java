package org.mozilla.vrbrowser.ui.widgets.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.databinding.OptionsFooterBinding;

public class SettingsFooter extends FrameLayout {

    private OptionsFooterBinding mBinding;

    public SettingsFooter(@NonNull Context context) {
        super(context);

        initialize(context, null);
    }

    public SettingsFooter(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.SettingsHeader, 0, 0);

        initialize(context, attributes);
    }

    public SettingsFooter(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.SettingsFooter, defStyleAttr, 0);

        initialize(context, attributes);
    }

    public SettingsFooter(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.SettingsFooter, defStyleAttr, 0);

        initialize(context, attributes);
    }

    private void initialize(@NonNull Context aContext, @NonNull TypedArray attributes) {
        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_footer, this, true);

        if (attributes != null) {
            String description = attributes.getString(R.styleable.SettingsFooter_description);

            if (description != null)
                mBinding.setDescription(description);
        }
    }

    public void setResetClickListener(@NonNull View.OnClickListener listener) {
       mBinding.setResetClickListener(listener);
    }
}
