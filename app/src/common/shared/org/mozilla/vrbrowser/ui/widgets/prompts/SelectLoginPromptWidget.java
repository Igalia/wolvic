package org.mozilla.vrbrowser.ui.widgets.prompts;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.ui.adapters.LoginsAdapter;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.ui.widgets.dialogs.UIDialog;

import java.util.List;

import mozilla.components.concept.storage.Login;

@SuppressLint("ViewConstructor")
public class SelectLoginPromptWidget extends UIDialog implements LoginsAdapter.Delegate {

    public interface Delegate {
        void onLoginSelected(@NonNull Login login);
        void onSettingsClicked();
    }

    private Delegate mDelegate;
    private List<Login> mItems;

    public SelectLoginPromptWidget(@NonNull Context aContext, @NonNull Delegate delegate, @NonNull List<Login> items) {
        super(aContext);

        mDelegate = delegate;
        mItems = items;
        initialize(aContext);
    }

    protected void initialize(Context aContext) {
        updateUI();
    }

    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        LoginsAdapter mAdapter = new LoginsAdapter(getContext(), this, LoginsAdapter.SELECTION_LIST);
        mAdapter.setItems(mItems);

        // Inflate this data binding layout
        org.mozilla.vrbrowser.databinding.PromptSelectLoginBinding mBinding = DataBindingUtil.inflate(inflater, R.layout.prompt_select_login, this, true);
        mBinding.settings.setOnClickListener(view -> {
            mDelegate.onSettingsClicked();
            hide(REMOVE_WIDGET);
        });
        mBinding.loginsList.setAdapter(mAdapter);
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
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.settings_world_z) -
                WidgetPlacement.unitFromMeters(getContext(), R.dimen.window_world_z);
    }

    @Override
    public void show(@ShowFlags int aShowFlags) {
        mWidgetPlacement.parentHandle = mWidgetManager.getFocusedWindow().getHandle();

        super.show(aShowFlags);
    }

    // SelectLoginAdapter.Delegate

    @Override
    public void onLoginSelected(@NonNull View view, @NonNull Login login) {
        mDelegate.onLoginSelected(login);
        hide(REMOVE_WIDGET);
    }

}
