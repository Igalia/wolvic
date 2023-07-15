package com.igalia.wolvic;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.igalia.wolvic.browser.SettingsStore;

public class ContentHolderFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    private SharedPreferences mPrefs;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_content_holder, container, false);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        mPrefs.registerOnSharedPreferenceChangeListener(this);

        updateUI();
    }

    private void updateUI() {
        if (getContext() == null || getView() == null)
            return;

        if (!SettingsStore.getInstance(getContext()).isTermsServiceAccepted()) {
            Fragment fragment = BuildConfig.CN_FIRST_RUN_IN_PHONE_UI ? new FirstRunFragment() : new TermsServiceFragment();
            getParentFragmentManager().beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .replace(R.id.fragment_placeholder, fragment)
                    .commit();
        } else if (!SettingsStore.getInstance(getContext()).isPrivacyPolicyAccepted()) {
            getParentFragmentManager().beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .replace(R.id.fragment_placeholder, new PrivacyPolicyFragment())
                    .commit();
        } else if (BuildConfig.WEBVIEW_IN_PHONE_UI) {
            getParentFragmentManager().beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .replace(R.id.fragment_placeholder, new LandingPageFragment())
                    .commit();
        } else {
            getParentFragmentManager().beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .replace(R.id.fragment_placeholder, new EnterVrFragment())
                    .commit();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    // Listen to changes in the preferences and update the UI accordingly
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getContext().getString(R.string.settings_key_privacy_policy_accepted))) {
            updateUI();
        } else if (key.equals(getContext().getString(R.string.settings_key_terms_service_accepted))) {
            updateUI();
        }
    }
}
