package org.mozilla.vrbrowser.search;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.SessionStore;
import org.mozilla.vrbrowser.browser.SettingsStore;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class SearchEngine implements GeolocationTask.GeolocationTaskDelegate {

    private static final String LOGTAG = "VRB";

    private static SearchEngine mSearchEngineInstance;

    private static class Engine {

        private int mUrlResource;
        private int mQueryResource;

        public String getURLResource(Context aContext) {
            return aContext.getString(mUrlResource);
        }

        public String getSearchQuery(Context aContext, String aQuery) {
            return aContext.getString(mUrlResource) + "?" + aContext.getString(mQueryResource, aQuery);
        }

        public static Engine create(int urlResource, int queryResource) {
            Engine engine = new Engine();
            engine.mUrlResource = urlResource;
            engine.mQueryResource = queryResource;

            return engine;
        }

        @NonNull
        public static Engine getEngine(@NonNull GeolocationTask.GeolocationData data) {
            String countryCode = data.getCountryCode().toUpperCase();
            if (countryCode.equals("US"))
                return Engine.create(R.string.search_google_us, R.string.search_google_us_params);

            else if (countryCode.equals("CN"))
                return Engine.create(R.string.search_baidu_cn, R.string.search_baidu_params);

            else if (countryCode.equals("RU"))
                return Engine.create(R.string.search_yandex_ru, R.string.search_yandex_params);

            else if (countryCode.equals("BY"))
                return Engine.create(R.string.search_yandex_by, R.string.search_yandex_params);

            else if (countryCode.equals("TR"))
                return Engine.create(R.string.search_yandex_tr, R.string.search_yandex_params);

            else if (countryCode.equals("KZ"))
                return Engine.create(R.string.search_yandex_kz, R.string.search_yandex_params);

            else
                return Engine.create(R.string.search_google, R.string.search_google_params);
        }
    }

    public static synchronized @NonNull
    SearchEngine get(final @NonNull Context aContext) {
        if (mSearchEngineInstance == null) {
            mSearchEngineInstance = new SearchEngine(aContext);
        }

        return mSearchEngineInstance;
    }

    private Context mContext;
    private Engine mEngine;
    private boolean isUpdating;
    private GeolocationTask mTask;
    private String mEndpoint;

    private SearchEngine(@NonNull Context aContext) {
        mContext = aContext;
        isUpdating = false;
        mEndpoint = mContext.getString(R.string.geolocation_api_url);

        String geolocationJson = SettingsStore.getInstance(mContext).getGeolocationData();
        GeolocationTask.GeolocationData data = GeolocationTask.GeolocationData.parse(geolocationJson);
        mEngine = Engine.getEngine(data);
        SessionStore.get().setRegion(data.getCountryCode());
    }

    public String getSearchURL(String aQuery) {
        try {
            aQuery = URLEncoder.encode(aQuery, "UTF-8");

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return mEngine.getSearchQuery(mContext, aQuery);
    }
    public String getURLResource() {
        return mEngine.getURLResource(mContext);
    }

    public void update() {
        if (!isUpdating) {
            mTask = new GeolocationTask(mEndpoint, this);
            mTask.execute();

        } else {
            Log.i(LOGTAG, "Geolocation update cancelled, previous update already running");
        }
    }

    @Override
    public void onGeolocationRequestStarted() {
        isUpdating = true;
    }

    @Override
    public void onGeolocationRequestSuccess(GeolocationTask.GeolocationData data) {
        isUpdating = false;

        SettingsStore.getInstance(mContext).setGeolocationData(data.toString());

        mEngine = Engine.getEngine(data);
        SessionStore.get().setRegion(data.getCountryCode());

        Log.d(LOGTAG, "Geolocation request success: " + data.toString());
    }

    @Override
    public void onGeolocationRequestError(String error) {
        isUpdating = false;

        Log.e(LOGTAG, "Geolocation request error: " + error);
    }
}
