package com.igalia.wolvic.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.igalia.wolvic.R;
import com.igalia.wolvic.ui.adapters.WebApp;
import com.igalia.wolvic.utils.StringUtils;
import com.igalia.wolvic.utils.SystemUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class WebAppsStore implements SharedPreferences.OnSharedPreferenceChangeListener {

    protected final String LOGTAG = SystemUtils.createLogtag(this.getClass());

    private Context mContext;
    private List<WebApp> mWebApps;
    private Set<WebAppsListener> mListeners;
    private SharedPreferences mPrefs;

    public WebAppsStore(Context context) {
        mContext = context.getApplicationContext();
        mWebApps = new ArrayList<>();
        mListeners = new LinkedHashSet<>();

        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mPrefs.registerOnSharedPreferenceChangeListener(this);

        mWebApps = new ArrayList<>();
        updateWebAppsListFromStorage();
    }

    private void updateWebAppsListFromStorage() {
        String json = SettingsStore.getInstance(mContext).getWebAppsData();

        if (StringUtils.isEmpty(json)) {
            mWebApps.clear();
            notifyListeners();
        } else {
            try {
                Gson gson = new Gson();
                WebApp[] webAppsArray = gson.fromJson(json, WebApp[].class);
                mWebApps.clear();
                mWebApps.addAll(Arrays.asList(webAppsArray));
                notifyListeners();
            } catch (RuntimeException e) {
                Log.w(LOGTAG, "retrieveListFromStorage: error parsing stored data: " + e.getMessage());

                // the stored data is invalid, so we need to clear it
                SettingsStore.getInstance(mContext).setWebAppsData("");
                mWebApps.clear();
                notifyListeners();
            }
        }
    }

    private void saveWebAppsListToStorage() {
        Gson gson = new Gson();
        WebApp[] webAppsArray = mWebApps.toArray(new WebApp[]{});
        String json = gson.toJson(webAppsArray, WebApp[].class);
        SettingsStore.getInstance(mContext).setWebAppsData(json);
    }

    /**
     * @return {@code true} if the list did not already contain the specified Web app,
     * {@code false} if the Web app was already in the list (in which case, it was updated).
     */
    public boolean addWebApp(@NonNull WebApp webAppToAdd) {
        for (WebApp webApp : mWebApps) {
            if (webApp.getId().equals(webAppToAdd.getId())) {
                // the Web app is already in the list, we update it
                webApp.copyFrom(webAppToAdd);
                notifyListeners();
                return false;
            }
        }
        mWebApps.add(webAppToAdd);
        notifyListeners();
        return true;
    }

    /**
     * @return {@code true} if any elements were removed
     */
    public void removeWebApp(@NonNull WebApp webAppToDelete) {
        mWebApps.removeIf(webApp -> webApp.getId().equals(webAppToDelete.getId()));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(mContext.getString(R.string.settings_key_web_apps_data))) {
            updateWebAppsListFromStorage();
        }
    }

    public interface WebAppsListener {
        void onWebAppsUpdated(List<WebApp> webApps);
    }

    public void addListener(@NonNull WebAppsListener listener) {
        mListeners.add(listener);
    }

    public void removeListener(@NonNull WebAppsListener listener) {
        mListeners.remove(listener);
    }

    private void notifyListeners() {
        List<WebAppsListener> listenersCopy = new ArrayList(mListeners);
        List<WebApp> webAppsCopy = Collections.unmodifiableList(mWebApps);
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            for (WebAppsListener listener : listenersCopy) {
                listener.onWebAppsUpdated(webAppsCopy);
            }
        });
    }
}
