/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;

import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.browser.Accounts;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.databinding.WhatsNewBinding;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.UIThreadExecutor;

public class WhatsNewWidget extends UIDialog implements WidgetManagerDelegate.WorldClickListener {

    private WhatsNewBinding mBinding;
    private Accounts mAccounts;
    private Runnable mSignInCallback;
    private Runnable mStartBrowsingCallback;

    public WhatsNewWidget(Context aContext) {
        super(aContext);
        initialize();
    }

    public WhatsNewWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize();
    }

    public WhatsNewWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize();
    }

    public void setSignInCallback(@NonNull Runnable callback) {
        mSignInCallback = callback;
    }

    public void setStartBrowsingCallback(@NonNull Runnable callback) {
        mStartBrowsingCallback = callback;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initialize() {
        LayoutInflater inflater = LayoutInflater.from(getContext());

        mAccounts = ((VRBrowserApplication)getContext().getApplicationContext()).getAccounts();

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.whats_new, this, true);

        mBinding.signInButton.setOnClickListener(v -> signIn());
        mBinding.startBrowsingButton.setOnClickListener((v) -> {
            if (mStartBrowsingCallback != null) {
                mStartBrowsingCallback.run();
            }
        });
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.whats_new_width);
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.whats_new_height);
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.0f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.translationY = WidgetPlacement.unitFromMeters(getContext(), R.dimen.settings_world_y) -
                                  WidgetPlacement.unitFromMeters(getContext(), R.dimen.window_world_y);
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.settings_world_z) -
                                  WidgetPlacement.unitFromMeters(getContext(), R.dimen.window_world_z);
    }

    @Override
    public void show(@ShowFlags int aShowFlags) {
        super.show(aShowFlags);

        mWidgetManager.addWorldClickListener(this);
        mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);
    }

    @Override
    public void hide(@HideFlags int aHideFlags) {
        super.hide(aHideFlags);

        SettingsStore.getInstance(getContext()).setWhatsNewDisplayed(true);

        mWidgetManager.popWorldBrightness(this);
        mWidgetManager.removeWorldClickListener(this);
    }

    private void signIn() {
        mAccounts.getAuthenticationUrlAsync().thenAcceptAsync((url) -> {
            if (url != null) {
                mAccounts.setLoginOrigin(Accounts.LoginOrigin.SEND_TABS);
                mWidgetManager.openNewTabForeground(url);
                mWidgetManager.getFocusedWindow().getSession().setUaMode(GeckoSessionSettings.USER_AGENT_MODE_VR);
                mWidgetManager.getFocusedWindow().getSession().loadUri(url);
            }

            if (mSignInCallback != null) {
                mSignInCallback.run();
            }

        }, new UIThreadExecutor());
    }

    // WidgetManagerDelegate.WorldClickListener

    @Override
    public void onWorldClick() {
        onDismiss();
    }

}
