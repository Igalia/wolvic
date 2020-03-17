package org.mozilla.vrbrowser.geolocation;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.vrbrowser.utils.SystemUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Class representing a Geolocation success response (HTTP 200)
 */
public class GeolocationData {

    private static final String LOGTAG = SystemUtils.createLogtag(GeolocationData.class);

    private static final String COUNTRY_CODE = "country_code";
    private static final String COUNTRY_NAME = "country_name";

    private JSONObject mData;

    private GeolocationData(JSONObject data) {
        mData = data;
    }

    @NonNull
    public static GeolocationData create(JSONObject data) {
        return new GeolocationData(data);
    }

    @NonNull
    public static GeolocationData create(String countryCode, String countryName) {
        JSONObject json = new JSONObject();
        try {
            json.put(COUNTRY_CODE, countryCode);
            json.put(COUNTRY_NAME, countryName);

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return GeolocationData.create(json);
    }

    @Nullable
    public static GeolocationData parse(String aGeolocationJson) {
        try {
            return GeolocationData.create(new JSONObject(aGeolocationJson));

        } catch (JSONException e) {
            Log.e(LOGTAG, "Error parsing geolocation data: " + e.getLocalizedMessage());
            return null;
        }
    }

    public String getCountryCode() {
        return mData.optString(COUNTRY_CODE, "");
    }

    public String getCountryName() {
        return mData.optString(COUNTRY_NAME, "");
    }

    @Override
    public String toString() {
        return mData.toString();
    }

}
