package com.igalia.wolvic;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.igalia.wolvic.browser.SettingsStore;

public class FirstRunFragment extends Fragment {

    private AlertDialog mCurrentDialog;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_first_run, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mCurrentDialog == null) {
            // initial dialog
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
            mCurrentDialog = builder.setTitle(R.string.rCN_first_launch_title)
                    .setMessage(createTextWithDocumentLinks(getString(R.string.rCN_first_launch_body)))
                    .setNegativeButton(R.string.rCN_first_launch_disagree, this::onDisagreeButtonClicked)
                    .setPositiveButton(R.string.rCN_first_launch_agree, this::onAgreeButtonClicked)
                    .setCancelable(false)
                    .create();
        }
        mCurrentDialog.show();

        // This is needed so the links can be clicked.
        if (mCurrentDialog.findViewById(android.R.id.message) != null) {
            ((TextView) mCurrentDialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mCurrentDialog != null)
            mCurrentDialog.dismiss();
    }

    private void onDisagreeButtonClicked(DialogInterface dialogInterface, int i) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
        // Show a confirmation dialog before closing the app.
        mCurrentDialog = builder.setTitle(R.string.rCN_first_launch_title)
                .setMessage(createTextWithDocumentLinks(getString(R.string.rCN_first_launch_confirmation_body)))
                .setNegativeButton(R.string.rCN_first_launch_confirmation_disagree, (dlg, which) -> getActivity().finish())
                .setPositiveButton(R.string.rCN_first_launch_confirmation_agree, this::onAgreeButtonClicked)
                .setCancelable(false)
                .show();
        if (mCurrentDialog.findViewById(android.R.id.message) != null) {
            ((TextView) mCurrentDialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    private void onAgreeButtonClicked(DialogInterface dialogInterface, int i) {
        SettingsStore.getInstance(getContext()).setTermsServiceAccepted(true);
        SettingsStore.getInstance(getContext()).setPrivacyPolicyAccepted(true);
    }

    private SpannableString createTextWithDocumentLinks(String baseText) {
        String clickableTermsText = getString(R.string.rCN_first_launch_clickable_terms_text);
        String clickablePrivacyText = getString(R.string.rCN_first_launch_clickable_privacy_text);

        // Ensure that the text contains the substrings that will be clickable.
        if (!baseText.contains(clickableTermsText)) {
            baseText += "\n\n" + clickableTermsText;
        }
        if (!baseText.contains(clickablePrivacyText)) {
            baseText += "\n\n" + clickablePrivacyText;
        }

        int startTerms = baseText.lastIndexOf(clickableTermsText);
        int endTerms = startTerms + clickableTermsText.length();

        int startPrivacy = baseText.lastIndexOf(clickablePrivacyText);
        int endPrivacy = startPrivacy + clickablePrivacyText.length();

        ClickableSpan clickableTermsSpan = new ClickableSpan() {
            public void onClick(View view) {
                showLegalDocument(LegalDocumentFragment.LegalDocument.TERMS_OF_SERVICE);
            }
        };

        ClickableSpan clickablePrivacySpan = new ClickableSpan() {
            public void onClick(View view) {
                showLegalDocument(LegalDocumentFragment.LegalDocument.PRIVACY_POLICY);
            }
        };

        SpannableString spannable = SpannableString.valueOf(baseText);
        spannable.setSpan(clickableTermsSpan, startTerms, endTerms, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(clickablePrivacySpan, startPrivacy, endPrivacy, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        return spannable;
    }

    private void showLegalDocument(LegalDocumentFragment.LegalDocument document) {
        LegalDocumentFragment documentFragment = LegalDocumentFragment.newInstance(document);
        getParentFragmentManager().beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .replace(R.id.fragment_placeholder, documentFragment)
                .addToBackStack(null)
                .commit();
    }
}
