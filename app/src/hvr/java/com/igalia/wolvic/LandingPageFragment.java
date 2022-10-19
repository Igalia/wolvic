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
import android.widget.Button;

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

        // Links to wolvic.com will be opened in this Web view, "mailto" links will launch the email app
        // and all other links will be opened in the default browser.
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) {
                    return true;
                }

                Uri url = request.getUrl();
                if (url.getScheme() != null && url.getScheme().equalsIgnoreCase("mailto")) {
                    Intent emailIntent = new Intent(Intent.ACTION_SENDTO, url);
                    startActivity(Intent.createChooser(emailIntent, null));
                    return true;
                }

                String host = url.getHost();
                if (Stream.of(WOLVIC_HOSTS).anyMatch(host::equalsIgnoreCase)) {
                    return false;
                } else {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, url);
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

        // Open Wolvic's landing page.
        webView.loadUrl(getString(R.string.landing_page_url));

        // "Enter VR" button displays a message to put on the VR glasses
        Button enterVrButton = view.findViewById(R.id.button_enter_vr);
        enterVrButton.setOnClickListener(v -> {
            showEnterVr();
        });
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
                if (item.getItemId() == R.id.enter_vr) {
                    showEnterVr();
                    return true;
                } else if (item.getItemId() == R.id.terms_service) {
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

    private void showEnterVr() {
        EnterVrFragment fragment = new EnterVrFragment();
        getFragmentManager().beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .add(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit();
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
