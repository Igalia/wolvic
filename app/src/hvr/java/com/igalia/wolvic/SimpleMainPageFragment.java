package com.igalia.wolvic;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;

// A simple content for the main page, just displaying a message to put on the VR glasses.
public class SimpleMainPageFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.simple_main_page, container, false);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        Toolbar toolbar = getActivity().findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitle(R.string.app_name);
            toolbar.setElevation(0f);

            /* TODO Show the Terms of Service and Privacy Policy
            toolbar.inflateMenu(R.menu.app_menu);
            toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.terms_service) {
                    return true;
                } else if (item.getItemId() == R.id.privacy_policy) {
                    return true;
                }
                return false;
            });
            toolbar.showOverflowMenu();
            */
        }
    }
}
