package com.igalia.wolvic.geolocation;

import android.content.Context;
import android.location.Location;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.huawei.hmf.tasks.OnFailureListener;
import com.huawei.hmf.tasks.OnSuccessListener;
import com.huawei.hms.location.FusedLocationProviderClient;
import com.huawei.hms.location.LocationCallback;
import com.huawei.hms.location.LocationRequest;
import com.huawei.hms.location.LocationResult;
import com.huawei.hms.location.LocationServices;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.engine.Session;

public class HVRLocationManager implements WSession.NavigationDelegate {

    public HVRLocationManager(Context ctx) {
        mContext = ctx;
    }

    public void start(Session session) {
        if (mSession != null) {
            mSession.removeNavigationListener(this);
        }
        mSession = session;
        mSession.addNavigationListener(this);
        if (mClient != null) {
            return;
        }
        mClient = LocationServices.getFusedLocationProviderClient(mContext);
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult location) {
                if (location != null && mSession.getWSession() != null) {
                    Location loc = location.getLastLocation();
                    mSession.getWSession().dispatchLocation(loc.getLatitude(), loc.getLongitude(), loc.getAltitude(),
                            loc.getAccuracy(), loc.getAccuracy(), loc.getBearing(), loc.getSpeed(), loc.getTime());
                }
            }
        };

        mLocationRequest = new LocationRequest();
        // Set the location update interval (unit: ms).
        mLocationRequest.setInterval(10000);
        // Set the location type.
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        mClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.getMainLooper())
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                    }
                });
    }

    public void stop() {
        if (mClient == null) {
            return;
        }
        // Note: When requesting location updates is stopped, mLocationCallback must be the same object as LocationCallback in the requestLocationUpdates method.
        mClient.removeLocationUpdates(mLocationCallback)
                // Define callback for success in stopping requesting location updates.
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        // TODO
                    }
                })
                // Define callback for failure in stopping requesting location updates.
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        // TODO
                    }
                });
        mClient = null;
        mLocationCallback = null;
        mLocationRequest = null;
        if (mSession != null) {
            mSession.removeNavigationListener(this);
            mSession = null;
        }
    }

    private FusedLocationProviderClient mClient;
    private Session mSession;
    LocationCallback mLocationCallback;
    LocationRequest mLocationRequest;
    private Context mContext;

    @Override
    public void onLocationChange(@NonNull WSession session, @Nullable String url) {
        stop();
    }
}
