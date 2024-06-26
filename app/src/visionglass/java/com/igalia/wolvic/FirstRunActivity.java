package com.igalia.wolvic;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import com.igalia.wolvic.browser.SettingsStore;

import java.util.Objects;

public class FirstRunActivity extends FragmentActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private SharedPreferences mPrefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.visionglass_first_run);
        setTheme(R.style.Theme_MaterialComponents_Light);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        updateUI();
    }

    private void updateUI() {
        if (!SettingsStore.getInstance(this).isTermsServiceAccepted()) {
            Fragment fragment = BuildConfig.CN_FIRST_RUN_IN_PHONE_UI ? new FirstRunFragment() : new TermsServiceFragment();
            getSupportFragmentManager().beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .replace(R.id.fragment_placeholder, fragment)
                    .commit();
        } else if (!SettingsStore.getInstance(this).isPrivacyPolicyAccepted()) {
            getSupportFragmentManager().beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .replace(R.id.fragment_placeholder, new PrivacyPolicyFragment())
                    .commit();
        } else {
            // finish and go to the main activity
            Intent intent = new Intent(this, VRBrowserActivity.class);
            startActivity(intent);
            finish();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (Objects.equals(key, this.getString(R.string.settings_key_terms_service_accepted))
                || Objects.equals(key, this.getString(R.string.settings_key_privacy_policy_accepted))) {
            updateUI();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
    }
}
