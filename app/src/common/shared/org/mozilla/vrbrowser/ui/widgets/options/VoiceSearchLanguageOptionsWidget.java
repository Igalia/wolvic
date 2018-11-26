/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.options;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ScrollView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.ui.views.UIButton;
import org.mozilla.vrbrowser.ui.views.settings.ButtonSetting;
import org.mozilla.vrbrowser.ui.views.settings.RadioGroupSetting;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.LocaleUtils;

public class VoiceSearchLanguageOptionsWidget extends UIWidget implements
        WidgetManagerDelegate.WorldClickListener,
        WidgetManagerDelegate.FocusChangeListener {

    private AudioEngine mAudio;
    private UIButton mBackButton;

    private RadioGroupSetting mLanguage;

    private ButtonSetting mResetButton;

    private ScrollView mScrollbar;

    public VoiceSearchLanguageOptionsWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public VoiceSearchLanguageOptionsWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public VoiceSearchLanguageOptionsWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.options_language, this);

        mAudio = AudioEngine.fromContext(aContext);

        mWidgetManager.addFocusChangeListener(this);
        mWidgetManager.addWorldClickListener(this);

        mBackButton = findViewById(R.id.backButton);
        mBackButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            onDismiss();
        });

        String language = SettingsStore.getInstance(getContext()).getVoiceSearchLanguage();
        mLanguage = findViewById(R.id.languageRadio);
        mLanguage.setOnCheckedChangeListener(mLanguageListener);
        setLanguage(mLanguage.getIdForValue(language), false);

        mResetButton = findViewById(R.id.resetButton);
        mResetButton.setOnClickListener(mResetListener);

        mScrollbar = findViewById(R.id.scrollbar);
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.developer_options_width);
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.developer_options_height);
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.5f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.translationY = WidgetPlacement.unitFromMeters(getContext(), R.dimen.restart_dialog_world_y);
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.restart_dialog_world_z);
    }

    @Override
    public void releaseWidget() {
        mWidgetManager.removeFocusChangeListener(this);
        mWidgetManager.removeWorldClickListener(this);

        super.releaseWidget();
    }

    @Override
    public void show() {
        super.show();

        mScrollbar.scrollTo(0, 0);
    }

    private RadioGroupSetting.OnCheckedChangeListener mLanguageListener = (radioGroup, checkedId, doApply) -> {
        setLanguage(checkedId, true);
    };

    private OnClickListener mResetListener = (view) -> {
        String value = mLanguage.getValueForId(mLanguage.getCheckedRadioButtonId()).toString();
        if (!value.equals(LocaleUtils.getCurrentLocale())) {
            setLanguage(mLanguage.getIdForValue(LocaleUtils.getCurrentLocale()), true);
        }
    };

    private void setLanguage(int checkedId, boolean doApply) {
        mLanguage.setOnCheckedChangeListener(null);
        mLanguage.setChecked(checkedId, doApply);
        mLanguage.setOnCheckedChangeListener(mLanguageListener);

        SettingsStore.getInstance(getContext()).setVoiceSearchLanguage(mLanguage.getValueForId(checkedId).toString());
    }

    // WindowManagerDelegate.FocusChangeListener

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (oldFocus == this && isVisible() && findViewById(newFocus.getId()) == null) {
            onDismiss();
        }
    }

    // WorldClickListener

    @Override
    public void onWorldClick() {
        if (isVisible()) {
            onDismiss();
        }
    }

}
