package com.igalia.wolvic.ui.widgets.settings;

import android.content.Context;
import android.graphics.Point;
import android.view.LayoutInflater;

import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.R;
import com.igalia.wolvic.databinding.OptionsPrivacyPolicyBinding;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;

/**
 * A SettingsView that displays Wolvic's Privacy Policy.
 * The content itself is shared with PrivacyPolicyDialogWidget.
 */
public class PrivacyPolicyView extends SettingsView {

    private OptionsPrivacyPolicyBinding mBinding;

    public PrivacyPolicyView(Context aContext, WidgetManagerDelegate aWidgetManager) {
        super(aContext, aWidgetManager);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        updateUI();
    }

    @Override
    protected void updateUI() {
        super.updateUI();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_privacy_policy, this, true);

        mScrollbar = mBinding.scrollbar;

        // Header
        mBinding.headerLayout.setBackClickListener(view -> {
            mDelegate.showView(SettingViewType.PRIVACY);
        });
    }

    @Override
    public Point getDimensions() {
        return new Point(WidgetPlacement.dpDimension(getContext(), R.dimen.settings_dialog_width),
                WidgetPlacement.dpDimension(getContext(), R.dimen.privacy_options_height));
    }

    @Override
    protected SettingViewType getType() {
        return SettingViewType.PRIVACY_POLICY;
    }
}
