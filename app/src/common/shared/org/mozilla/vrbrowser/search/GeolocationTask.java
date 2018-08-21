package org.mozilla.vrbrowser.search;

import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

public class GeolocationTask extends AsyncTask<Void, Void, GeolocationTask.GeolocationTaskResponse> {

    private static final String LOGTAG = "VRB";

    private static final int MAX_RETRIES = 2;
    private static final int CONNECTION_TIMEOUT = 10 * 1000;

    public static class GeolocationError {
        private int mCode;
        private String mMessage;

        private GeolocationError(int aCode, String aMessage) {
            mCode = aCode;
            mMessage = aMessage;
        }

        @NonNull
        public static GeolocationError create(int aCode, String aMessage) {
            return new GeolocationError(aCode, aMessage);
        }

        public static GeolocationError parse(String aGeolocationJson) {
            GeolocationError data = null;

            JSONObject json;
            try {
                json = new JSONObject(aGeolocationJson);
                JSONObject error = json.getJSONObject("error");

                return GeolocationError.create(
                        error.getInt("code"),
                        error.getString("message"));

            } catch (JSONException e) {
                Log.e(LOGTAG, "Error parsing geolocation data: " + e.getLocalizedMessage());
            }

            return data;
        }

        public int getCode() {
            return mCode;
        }

        public String getMessage() {
            return mMessage;
        }
    }

    public static class GeolocationData {

        private String mCountryCode;
        private String mCountryName;

        private GeolocationData(String aCountryCode, String aCountryName) {
            mCountryCode = aCountryCode;
            mCountryName = aCountryName;
        }

        @NonNull
        public static GeolocationData create(String aCountryCode, String aCountryName) {
            return new GeolocationData(aCountryCode, aCountryName);
        }

        public static GeolocationData parse(String aGeolocationJson) {
            GeolocationData data = new GeolocationData(null, null);

            JSONObject json;
            try {
                json = new JSONObject(aGeolocationJson);
                data.mCountryCode = json.getString("country_code");
                data.mCountryName = json.getString("country_name");

            } catch (JSONException e) {
                Log.e(LOGTAG, "Error parsing geolocation data: " + e.getLocalizedMessage());

                data.mCountryCode = "";
                data.mCountryName = "";
            }

            return data;
        }

        public String getCountryCode() {
            return mCountryCode;
        }

        public String getCountryName() {
            return mCountryName;
        }

        public String toString() {
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("country_code", mCountryCode);
                jsonObject.put("country_name", mCountryName);

            } catch (JSONException e) {
                Log.e(LOGTAG, "Error: " + e.getLocalizedMessage());
            }

            return jsonObject.toString();
        }
    }

    public enum ResponseType {
        SUCCESS,
        ERROR
    }

    public static class GeolocationTaskResponse {

        ResponseType responseType;
        Object data;

        public static GeolocationTaskResponse create(ResponseType type, Object data) {
            GeolocationTaskResponse response = new GeolocationTaskResponse();
            response.responseType = type;
            response.data = data;

            return response;
        }

    }

    public interface GeolocationTaskDelegate {
        void onGeolocationRequestStarted();
        void onGeolocationRequestSuccess(GeolocationData response);
        void onGeolocationRequestError(String error);
    }

    private GeolocationTaskDelegate mDelegate;
    private int mRetries;
    private int mRetryCount;
    private String mEndpoint;

    public GeolocationTask(@NonNull String endpoint, GeolocationTaskDelegate aDelegate) {
        this(endpoint, aDelegate, MAX_RETRIES);
    }

    public GeolocationTask(@NonNull String endpoint, GeolocationTaskDelegate aDelegate, int retries) {
        mEndpoint = endpoint;
        mDelegate = aDelegate;
        mRetries = retries;
        mRetryCount = 0;
    }

    @Override
    protected GeolocationTaskResponse doInBackground(Void... params) {
        if (mDelegate != null)
            mDelegate.onGeolocationRequestStarted();

        GeolocationTaskResponse result;
        do {
            result = executeGeoLocationRequest();

            if (result.responseType == ResponseType.ERROR) {
                if (mRetryCount <= mRetries - 1) {
                    Log.e(LOGTAG, "Geolocation request error, retrying... " + (mRetryCount + 1));

                } else {
                    Log.e(LOGTAG, "Max geolocation request retry count reached. Cancelling");
                    result = GeolocationTaskResponse.create(ResponseType.ERROR, "Max retry count reached");
                }
            }
        } while(mRetryCount++ < mRetries && result.responseType == ResponseType.ERROR);

        return result;
    }

    @NonNull
    private GeolocationTaskResponse executeGeoLocationRequest() {
        HttpsURLConnection urlConnection = null;
        BufferedReader reader = null;

        try {
            URL url = new URL(mEndpoint);

            urlConnection = (HttpsURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setConnectTimeout(CONNECTION_TIMEOUT);
            urlConnection.connect();

            InputStream inputStream = urlConnection.getInputStream();
            StringBuffer buffer = new StringBuffer();
            if (inputStream == null) {
                Log.e(LOGTAG, "Null input stream");
                return GeolocationTaskResponse.create(ResponseType.ERROR, "Null input stream");
            }

            reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line + "\n");
            }

            if (buffer.length() == 0) {
                Log.e(LOGTAG, "Empty response buffer");
                return GeolocationTaskResponse.create(ResponseType.ERROR, "Empty response buffer");
            }

            String result = buffer.toString();

            if (urlConnection.getResponseCode() != 200) {
                GeolocationError error = GeolocationError.parse(result);
                if (error != null)
                    return GeolocationTaskResponse.create(ResponseType.SUCCESS, error);
                else
                    return GeolocationTaskResponse.create(ResponseType.ERROR, "Server error: " + urlConnection.getResponseCode());
            }

            GeolocationData data = GeolocationData.parse(result);
            return GeolocationTaskResponse.create(ResponseType.SUCCESS, data);

        } catch (IOException e) {
            Log.e(LOGTAG, "Error: " + e.getLocalizedMessage());

            return GeolocationTaskResponse.create(ResponseType.ERROR, "Error: " + e.getLocalizedMessage());

        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (final IOException e) {
                    Log.e(LOGTAG, "Error closing stream: " + e.getLocalizedMessage());
                }
            }
        }
    }

    @Override
    protected void onPostExecute(GeolocationTaskResponse response) {
        super.onPostExecute(response);

        if (mDelegate != null) {
            if (response.responseType == ResponseType.SUCCESS) {
                if (response.data instanceof GeolocationData) {
                    mDelegate.onGeolocationRequestSuccess((GeolocationData) response.data);

                } else if (response.data instanceof GeolocationError) {
                    GeolocationError error = (GeolocationError)response.data;
                    mDelegate.onGeolocationRequestError(error.getMessage());
                }

            } else {
                mDelegate.onGeolocationRequestError((String)response.data);
            }
        }
    }
}
