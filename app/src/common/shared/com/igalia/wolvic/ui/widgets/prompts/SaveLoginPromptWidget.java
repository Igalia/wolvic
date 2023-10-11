package com.igalia.wolvic.ui.widgets.prompts;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.text.InputType;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.R;
import com.igalia.wolvic.databinding.PromptSaveLoginBinding;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.ui.widgets.dialogs.UIDialog;

import mozilla.components.concept.storage.Login;

@SuppressLint("ViewConstructor")
public class SaveLoginPromptWidget extends UIDialog {

    public interface Delegate {
        default void confirm(@NonNull Login login) {}
        default void dismiss(@NonNull Login login) {}
    }

    private PromptSaveLoginBinding mBinding;
    private Delegate mPromptDelegate;

    public SaveLoginPromptWidget(@NonNull Context aContext) {
        super(aContext);

        initialize(aContext);
    }

    public void setPromptDelegate(@NonNull Delegate delegate) {
        mPromptDelegate = delegate;
    }

    protected void initialize(Context aContext) {
        updateUI();
    }

    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.prompt_save_login, this, true);
        mBinding.neverButton.setOnClickListener(view -> {
            mPromptDelegate.dismiss(mBinding.getLogin());
            hide(KEEP_WIDGET);
        });
        mBinding.saveButton.setOnClickListener(view -> {
            mPromptDelegate.confirm(mBinding.getLogin());
            hide(KEEP_WIDGET);
        });
        mBinding.passwordToggle.setOnCheckedChangeListener((compoundButton, checked) -> {
            if (checked) {
                mBinding.passwordText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);

            } else {
                mBinding.passwordText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
        });
    }

    @Override
    protected void onDismiss() {
        hide(KEEP_WIDGET);

        if (mDelegate != null) {
            mDelegate.onDismiss();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateUI();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        // We align it at the same position as the settings panel
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.prompt_dialog_width);
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.prompt_dialog_height);
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.parentAnchorY = 0.0f;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.translationY = WidgetPlacement.unitFromMeters(getContext(), R.dimen.settings_world_y) -
                WidgetPlacement.unitFromMeters(getContext(), R.dimen.window_world_y);
        updatePlacementTranslationZ();
    }

    @Override
    public void updatePlacementTranslationZ() {
        getPlacement().translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.settings_world_z) -
                WidgetPlacement.getWindowWorldZMeters(getContext());
    }

    @Override
    public void show(@ShowFlags int aShowFlags) {
        mWidgetPlacement.parentHandle = mWidgetManager.getFocusedWindow().getHandle();
        super.show(aShowFlags);
    }

    public void setLogin(@NonNull Login login) {
        mBinding.setLogin(login);
    }

}
