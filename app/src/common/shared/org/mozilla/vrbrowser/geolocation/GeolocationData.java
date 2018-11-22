package org.mozilla.vrbrowser.geolocation;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import androidx.annotation.NonNull;

/**
 * Class representing a Geolocation success response (HTTP 200)
 */
public class GeolocationData {

    private static final String LOGTAG = "VRB";

    private JSONObject mData;

    private GeolocationData(JSONObject data) {
        mData = data;
    }

    @NonNull
    public static GeolocationData create(JSONObject data) {
        return new GeolocationData(data);
    }

    @NonNull
    public static GeolocationData parse(String aGeolocationJson) {
        try {
            return GeolocationData.create(new JSONObject(aGeolocationJson));

        } catch (JSONException e) {
            Log.e(LOGTAG, "Error parsing geolocation data: " + e.getLocalizedMessage());
            return null;
        }
    }

    public String getCountryCode() {
        return mData.optString("country_code", "");
    }

    public String getCountryName() {
        return mData.optString("country_name", "");
    }

    @Override
    public String toString() {
        return mData.toString();
    }

}
