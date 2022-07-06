package com.igalia.wolvic.ui.widgets.settings;

import android.content.Context;
import android.graphics.Point;
import android.view.LayoutInflater;

import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.R;
import com.igalia.wolvic.databinding.OptionsLegalDocumentBinding;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;

/**
 * A SettingsView that displays a legal document like the Terms of Service or the Privacy Policy,.
 * The content itself is shared with LegalDocumentDialogWidget.
 */
public class LegalDocumentView extends SettingsView {

    public enum LegalDocument {
        TERMS_OF_SERVICE,
        PRIVACY_POLICY
    }

    private LegalDocument mLegalDocument;
    private OptionsLegalDocumentBinding mBinding;

    public LegalDocumentView(Context aContext, WidgetManagerDelegate aWidgetManager, LegalDocument legalDocument) {
        super(aContext, aWidgetManager);
        this.mLegalDocument = legalDocument;
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
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_legal_document, this, true);

        // Inflate the specific content
        if (mLegalDocument == LegalDocument.TERMS_OF_SERVICE) {
            inflater.inflate(R.layout.terms_service_content, mBinding.scrollbar, true);
        } else {
            inflater.inflate(R.layout.privacy_policy_content, mBinding.scrollbar, true);
        }


        mScrollbar = mBinding.scrollbar;

        // Header
        if (mLegalDocument == LegalDocument.TERMS_OF_SERVICE)
            mBinding.headerLayout.setTitle(R.string.settings_terms_service);
        else
            mBinding.headerLayout.setTitle(R.string.settings_privacy_policy);

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
        if (mLegalDocument == LegalDocument.TERMS_OF_SERVICE)
            return SettingViewType.TERMS_OF_SERVICE;
        else
            return SettingViewType.PRIVACY_POLICY;
    }
}
