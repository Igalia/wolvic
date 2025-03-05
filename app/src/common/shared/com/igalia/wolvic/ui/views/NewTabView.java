package com.igalia.wolvic.ui.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.NewTabBinding;
import com.igalia.wolvic.ui.viewmodel.SettingsViewModel;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.utils.SystemUtils;

public class NewTabView extends FrameLayout {

    static final String LOGTAG = SystemUtils.createLogtag(NewTabView.class);

    private NewTabBinding mBinding;
    private SettingsViewModel mSettingsViewModel;
    private WidgetManagerDelegate mWidgetManager;

    public NewTabView(Context context) {
        super(context);
        initialize();
    }

    protected void initialize() {
        mWidgetManager = ((VRBrowserActivity) getContext());
        updateUI();
    }

    @SuppressLint("ClickableViewAccessibility")
    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        mSettingsViewModel = new ViewModelProvider((VRBrowserActivity) getContext(), ViewModelProvider.AndroidViewModelFactory.getInstance(((VRBrowserActivity) getContext()).getApplication())).get(SettingsViewModel.class);

        mBinding = DataBindingUtil.inflate(inflater, R.layout.new_tab, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity) getContext());
        mBinding.setSettingsmodel(mSettingsViewModel);

        mBinding.logo.setOnClickListener(v -> openUrl(getContext().getString(R.string.home_page_url)));

        mBinding.searchBar.setOnClickListener(v -> {
            mWidgetManager.getNavigationBar().focusSearchBar();
        });
    }

    private void openUrl(@NonNull String url) {
        Session session = SessionStore.get().getActiveSession();
        session.loadUri(url);
    }
}