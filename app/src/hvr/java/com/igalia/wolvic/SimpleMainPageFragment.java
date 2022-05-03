package com.igalia.wolvic;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

// A simple content for the main page, just displaying a message to put on the VR glasses.
public class SimpleMainPageFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.simple_main_page, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.button_open_web).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.base_url)));
            startActivity(intent);
        });
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        Toolbar toolbar = getActivity().findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitle(R.string.app_name);
            toolbar.setElevation(0f);

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
