package com.igalia.wolvic.ui.widgets.prompts;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.R;
import com.igalia.wolvic.ui.adapters.LoginsAdapter;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.ui.widgets.dialogs.UIDialog;

import java.util.Collections;
import java.util.List;

import mozilla.components.concept.storage.Login;

@SuppressLint("ViewConstructor")
public class SelectLoginPromptWidget extends UIDialog implements LoginsAdapter.Delegate {

    public interface Delegate {
        void onLoginSelected(@NonNull Login login);
        void onSettingsClicked();
    }

    private Delegate mPromptDelegate;
    private List<Login> mItems;
    private LoginsAdapter mAdapter;

    public SelectLoginPromptWidget(@NonNull Context aContext) {
        super(aContext);

        mItems = Collections.emptyList();

        initialize(aContext);
    }

    public void setPromptDelegate(@NonNull Delegate delegate) {
        mPromptDelegate = delegate;
    }

    public void setItems(@NonNull List<Login> items) {
        mItems = items;
        mAdapter.setItems(mItems);
    }

    protected void initialize(Context aContext) {
        updateUI();
    }

    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        mAdapter = new LoginsAdapter(getContext(), this, LoginsAdapter.SELECTION_LIST);
        mAdapter.setItems(mItems);

        // Inflate this data binding layout
        com.igalia.wolvic.databinding.PromptSelectLoginBinding mBinding = DataBindingUtil.inflate(inflater, R.layout.prompt_select_login, this, true);
        mBinding.settings.setOnClickListener(view -> {
            if (mPromptDelegate != null) {
                mPromptDelegate.onSettingsClicked();
            }
            hide(KEEP_WIDGET);
        });
        mBinding.loginsList.setAdapter(mAdapter);
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

    // SelectLoginAdapter.Delegate

    @Override
    public void onLoginSelected(@NonNull View view, @NonNull Login login) {
        if (mPromptDelegate != null) {
            mPromptDelegate.onLoginSelected(login);
        }
        hide(KEEP_WIDGET);
    }

}
