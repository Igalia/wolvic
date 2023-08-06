package com.igalia.wolvic.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.igalia.wolvic.R;
import com.igalia.wolvic.ui.adapters.WebApp;
import com.igalia.wolvic.utils.StringUtils;
import com.igalia.wolvic.utils.SystemUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class WebAppsStore implements SharedPreferences.OnSharedPreferenceChangeListener {

    protected final String LOGTAG = SystemUtils.createLogtag(this.getClass());

    private Context mContext;
    private LinkedHashMap<String, WebApp> mWebApps;
    private Set<WebAppsListener> mListeners;
    private SharedPreferences mPrefs;

    public WebAppsStore(Context context) {
        mContext = context.getApplicationContext();
        mWebApps = new LinkedHashMap<>();
        mListeners = new LinkedHashSet<>();

        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mPrefs.registerOnSharedPreferenceChangeListener(this);

        mWebApps = new LinkedHashMap<>();
        updateWebAppsListFromStorage();
    }

    private void updateWebAppsListFromStorage() {
        mWebApps.clear();

        String json = SettingsStore.getInstance(mContext).getWebAppsData();
        if (!StringUtils.isEmpty(json)) {
            try {
                Gson gson = new Gson();
                WebApp[] webAppsArray = gson.fromJson(json, WebApp[].class);
                for (WebApp webApp : webAppsArray) {
                    mWebApps.put(webApp.getId(), webApp);
                }
            } catch (RuntimeException e) {
                Log.w(LOGTAG, "retrieveListFromStorage: error parsing stored data: " + e.getMessage());

                // the stored data is invalid, so we need to clear it
                SettingsStore.getInstance(mContext).setWebAppsData("");
            }
        }
        notifyListeners();
    }

    private void saveWebAppsListToStorage() {
        Gson gson = new Gson();
        WebApp[] webAppsArray = mWebApps.values().toArray(new WebApp[0]);
        String json = gson.toJson(webAppsArray, WebApp[].class);
        SettingsStore.getInstance(mContext).setWebAppsData(json);
    }

    public void updateWebAppOpenTime(@NonNull String id) {
        WebApp existingWebApp = mWebApps.get(id);
        if (existingWebApp != null) {
            existingWebApp.setLastOpenTime();
            saveWebAppsListToStorage();
            notifyListeners();
        }
    }

    /**
     * @return {@code true} if the map did not contain the specified Web app (so it was added),
     * and {@code false} if the Web app was already in the list (so it was updated).
     */
    public boolean addWebApp(@NonNull WebApp webAppToAdd) {
        // if the Web app is already in the map, we update it
        WebApp existingWebApp = mWebApps.get(webAppToAdd.getId());
        if (existingWebApp != null) {
            existingWebApp.copyFrom(webAppToAdd);
            notifyListeners();
            return false;
        }
        // otherwise, we add a new entry
        mWebApps.put(webAppToAdd.getId(), webAppToAdd);
        saveWebAppsListToStorage();
        notifyListeners();
        return true;
    }

    /**
     * @return {@code true} if an element was removed
     */
    public boolean removeWebAppById(@NonNull String webAppId) {
        WebApp removedWebApp = mWebApps.remove(webAppId);
        saveWebAppsListToStorage();
        notifyListeners();
        return removedWebApp != null;
    }

    @NonNull
    public List<WebApp> getWebApps() {
        return new ArrayList<>(mWebApps.values());
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(mContext.getString(R.string.settings_key_web_apps_data))) {
            updateWebAppsListFromStorage();
        }
    }

    public interface WebAppsListener {
        void onWebAppsUpdated(@NonNull List<WebApp> webApps);
    }

    public void addListener(@NonNull WebAppsListener listener) {
        mListeners.add(listener);
    }

    public void removeListener(@NonNull WebAppsListener listener) {
        mListeners.remove(listener);
    }

    private void notifyListeners() {
        @SuppressWarnings("unchecked")
        List<WebAppsListener> listenersCopy = new ArrayList(mListeners);
        List<WebApp> webAppsCopy = new ArrayList<>(mWebApps.values());
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            for (WebAppsListener listener : listenersCopy) {
                listener.onWebAppsUpdated(webAppsCopy);
            }
        });
    }
}
