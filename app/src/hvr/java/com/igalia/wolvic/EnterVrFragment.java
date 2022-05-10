package com.igalia.wolvic;

import android.app.Fragment;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.appbar.MaterialToolbar;

// A simple message to put on the VR glasses.
public class EnterVrFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_enter_vr, container, false);

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setTitle(null);

        // If we were launched from another screen, allow the user to navigate back
        if (getFragmentManager().getBackStackEntryCount() > 0) {
            toolbar.setNavigationIcon(R.drawable.ic_icon_back);
            toolbar.setNavigationOnClickListener(v -> getFragmentManager().popBackStack());

            // Handle Back presses
            view.setFocusableInTouchMode(true);
            view.requestFocus();
            view.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    getFragmentManager().popBackStack();
                    return true;
                } else {
                    return false;
                }
            });
        }

        return view;
    }
}
