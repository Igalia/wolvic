package com.igalia.wolvic;

import androidx.fragment.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import com.igalia.wolvic.browser.SettingsStore;

public class PrivacyPolicyFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_privacy_policy, container, false);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Toolbar toolbar = view.findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitle(R.string.privacy_policy_title);
            toolbar.hideOverflowMenu();
        }

        view.findViewById(R.id.button_decline).setOnClickListener(
                button -> getActivity().finish());

        view.findViewById(R.id.button_accept).setOnClickListener(
                button -> SettingsStore.getInstance(getContext()).setPrivacyPolicyAccepted(true));
    }
}
