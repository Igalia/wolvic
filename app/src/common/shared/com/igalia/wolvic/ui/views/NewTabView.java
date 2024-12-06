package com.igalia.wolvic.ui.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.databinding.NewTabBinding;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;

public class NewTabView extends FrameLayout {

    private WidgetManagerDelegate mWidgetManager;

    private NewTabBinding mBinding;

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

        mBinding = DataBindingUtil.inflate(inflater, R.layout.new_tab, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity)getContext());

        mBinding.executePendingBindings();
    }
}