package com.igalia.wolvic;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import java.util.stream.Stream;

public class LandingPageFragment extends Fragment {

    private static String[] WOLVIC_HOSTS = {"wolvic.com", "beta.wolvic.com"};

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_landing_page, container, false);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        WebView webView = view.findViewById(R.id.web_view);

        // wolvic.com links will be opened in this WebView and outside links will be opened in the default browser
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String host = request.getUrl().getHost();
                if (Stream.of(WOLVIC_HOSTS).anyMatch(host::equalsIgnoreCase)) {
                    return false;
                } else {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, request.getUrl());
                    startActivity(browserIntent);
                    return true;
                }
            }
        });

        // Handle Back presses by navigating back in the Web view
        webView.setFocusableInTouchMode(true);
        webView.requestFocus();
        webView.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP && webView.canGoBack()) {
                webView.goBack();
                return true;
            } else {
                return false;
            }
        });

        webView.loadUrl(getString(R.string.landing_page_url));

        view.findViewById(R.id.button_learn_more).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.hvr_learn_more_url)));
            startActivity(intent);
        });

        view.findViewById(R.id.button_dismiss).setOnClickListener(v ->
                getView().findViewById(R.id.glass_banner).setVisibility(View.GONE));
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        Toolbar toolbar = getActivity().findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitle(R.string.app_name);
            toolbar.showOverflowMenu();
            toolbar.inflateMenu(R.menu.app_menu);
            toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.terms_service) {
                    showLegalDocument(LegalDocumentFragment.LegalDocument.TERMS_OF_SERVICE);
                    return true;
                } else if (item.getItemId() == R.id.privacy_policy) {
                    showLegalDocument(LegalDocumentFragment.LegalDocument.PRIVACY_POLICY);
                    return true;
                }
                return false;
            });
        }
    }

    private void showLegalDocument(LegalDocumentFragment.LegalDocument document) {
        LegalDocumentFragment documentFragment = LegalDocumentFragment.newInstance(document);
        getFragmentManager().beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .add(android.R.id.content, documentFragment)
                .addToBackStack(null)
                .commit();
    }
}
