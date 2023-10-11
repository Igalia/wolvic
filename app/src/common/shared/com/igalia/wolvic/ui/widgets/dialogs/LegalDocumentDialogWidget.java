/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets.dialogs;

import android.content.Context;
import android.content.res.Configuration;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.R;
import com.igalia.wolvic.databinding.LegalDocumentDialogBinding;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.ui.widgets.WindowWidget;

/**
 * A dialog that displays a legal document like the Terms of Service or the Privacy Policy,
 * along with two buttons to accept or reject it.
 */
public class LegalDocumentDialogWidget extends UIDialog {

    public enum LegalDocument {
        TERMS_OF_SERVICE,
        PRIVACY_POLICY
    }

    public interface Delegate {
        void onUserResponse(boolean response);
    }

    private LegalDocument mLegalDocument;
    private LegalDocumentDialogBinding mBinding;
    private Delegate mDialogDelegate;

    public LegalDocumentDialogWidget(Context aContext, LegalDocument legalDocument) {
        super(aContext);
        this.mLegalDocument = legalDocument;
        initialize(aContext);
    }

    protected void initialize(Context aContext) {
        updateUI();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateUI();
    }

    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.legal_document_dialog, this, true);

        // Add the content of the specific legal document
        switch (mLegalDocument) {
            case TERMS_OF_SERVICE:
                inflater.inflate(R.layout.terms_service_content, mBinding.scrollbar, true);
                break;
            case PRIVACY_POLICY:
                inflater.inflate(R.layout.privacy_policy_content, mBinding.scrollbar, true);
                break;
        }

        mBinding.acceptButton.setOnClickListener(v -> {
            if (mDialogDelegate != null) {
                mDialogDelegate.onUserResponse(true);
            }
            onDismiss();
        });
        mBinding.declineButton.setOnClickListener(v -> {
            if (mDialogDelegate != null) {
                mDialogDelegate.onUserResponse(false);
            }
            onDismiss();
        });
    }

    public void setDelegate(Delegate delegate) {
        mDialogDelegate = delegate;
    }

    @Override
    public void onWorldClick() {
        // ignored: this is a modal dialog
    }

    @Override
    public void attachToWindow(@NonNull WindowWidget window) {
        mWidgetPlacement.parentHandle = window.getHandle();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width = WidgetPlacement.dpDimension(getContext(), R.dimen.settings_dialog_width);
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.privacy_options_height);
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.0f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.translationY = WidgetPlacement.unitFromMeters(getContext(), R.dimen.settings_world_y) -
                WidgetPlacement.unitFromMeters(getContext(), R.dimen.window_world_y);
        updatePlacementTranslationZ();
    }

    @Override
    public void updatePlacementTranslationZ() {
        getPlacement().translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.settings_world_z) -
                WidgetPlacement.getWindowWorldZMeters(getContext());
    }
}
