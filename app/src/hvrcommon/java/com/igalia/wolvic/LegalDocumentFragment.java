package com.igalia.wolvic;

import androidx.fragment.app.Fragment;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.widget.NestedScrollView;

import com.google.android.material.appbar.MaterialToolbar;

/**
 * A DialogFragment that displays a legal document like the Terms of Service or the Privacy Policy.
 * You must specify the type of document to show when you create a new instance.
 * Note that this class is intended to be used on the phone UI.
 */
public class LegalDocumentFragment extends Fragment {

    public static String LEGAL_DOCUMENT = "legal_document";

    public enum LegalDocument {
        TERMS_OF_SERVICE,
        PRIVACY_POLICY
    }

    public static LegalDocumentFragment newInstance(LegalDocument legalDocument) {
        LegalDocumentFragment fragment = new LegalDocumentFragment();
        Bundle args = new Bundle();
        args.putSerializable(LEGAL_DOCUMENT, legalDocument);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Bundle args = getArguments();
        if (args == null || !args.containsKey(LEGAL_DOCUMENT)) {
            throw new IllegalArgumentException("Missing argument: " + LEGAL_DOCUMENT);
        }

        View view = inflater.inflate(R.layout.fragment_legal_document, container, false);

        // Handle Back presses by going back to the previous page
        view.setFocusableInTouchMode(true);
        view.requestFocus();
        view.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                getParentFragmentManager().popBackStack();
                return true;
            } else {
                return false;
            }
        });

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_icon_back);
        toolbar.setNavigationOnClickListener(v -> getParentFragmentManager().popBackStack());

        NestedScrollView contentHolder = view.findViewById(R.id.scrollview);

        View content;
        if (LegalDocument.TERMS_OF_SERVICE == args.getSerializable(LEGAL_DOCUMENT)) {
            toolbar.setTitle(R.string.terms_service_title);
            content = inflater.inflate(R.layout.terms_service_content, contentHolder, false);
        } else {
            toolbar.setTitle(R.string.privacy_policy_title);
            content = inflater.inflate(R.layout.privacy_policy_content, contentHolder, false);
        }
        content.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        contentHolder.addView(content);

        return view;
    }
}
