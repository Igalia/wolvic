package org.mozilla.vrbrowser.browser;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.PlatformActivity;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.dialogs.PermissionWidget;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.util.ArrayList;
import java.util.Arrays;

public class PermissionDelegate implements GeckoSession.PermissionDelegate, WidgetManagerDelegate.PermissionListener {

    static final int PERMISSION_REQUEST_CODE = 1143;

    static final String LOGTAG = SystemUtils.createLogtag(PermissionDelegate.class);
    private Context mContext;
    private int mParentWidgetHandle;
    private WidgetManagerDelegate mWidgetManager;
    private GeckoSession.PermissionDelegate.Callback mCallback;
    private PermissionWidget mPermissionWidget;

    public PermissionDelegate(Context aContext, WidgetManagerDelegate aWidgetManager) {
        mContext = aContext;
        mWidgetManager = aWidgetManager;
        mWidgetManager.addPermissionListener(this);
        SessionStore.get().setPermissionDelegate(this);
    }

    public void setParentWidgetHandle(int aHandle) {
        mParentWidgetHandle = aHandle;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != PERMISSION_REQUEST_CODE || mCallback == null) {
            return;
        }

        boolean granted = true;
        for (int result: grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                granted = false;
                break;
            }
        }

        if (granted) {
            mCallback.grant();
        } else {
            mCallback.reject();
        }
    }

    public void handlePermission(final String aUri, final PermissionWidget.PermissionType aType, final Callback aCallback) {
        if (mPermissionWidget == null) {
            mPermissionWidget = new PermissionWidget(mContext);
            mWidgetManager.addWidget(mPermissionWidget);
        }

        mPermissionWidget.showPrompt(aUri, aType, aCallback);
    }

    public void release() {
        mWidgetManager.removePermissionListener(this);
        SessionStore.get().setPermissionDelegate(null);
        mCallback = null;
        mContext = null;
        mWidgetManager = null;
    }

    @Override
    public void onAndroidPermissionsRequest(GeckoSession aSession, String[] permissions, Callback aCallback) {
        Log.d(LOGTAG, "onAndroidPermissionsRequest: " + Arrays.toString(permissions));
        ArrayList<String> missingPermissions = new ArrayList<>();
        ArrayList<String> filteredPemissions = new ArrayList<>();
        for (String permission: permissions) {
            if (PlatformActivity.filterPermission(permission)) {
                Log.d(LOGTAG, "Skipping permission: " + permission);
                filteredPemissions.add(permission);
                continue;
            }
            Log.d(LOGTAG, "permission = " + permission);
            if (mContext.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        if (missingPermissions.size() == 0) {
            if (filteredPemissions.size() == 0) {
                Log.d(LOGTAG, "Android permissions granted");
                aCallback.grant();
            } else {
                Log.d(LOGTAG, "Android permissions rejected");
                aCallback.reject();
            }
        } else {
            Log.d(LOGTAG, "Request Android permissions: " + missingPermissions);
            mCallback = aCallback;
            ((Activity)mContext).requestPermissions(missingPermissions.toArray(new String[missingPermissions.size()]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onContentPermissionRequest(GeckoSession aSession, String aUri, int aType, Callback callback) {
        Log.d(LOGTAG, "onContentPermissionRequest: " + aUri + " " + aType);
        if (aType == PERMISSION_XR) {
            callback.grant();
            return;
        }

        if (aType == PERMISSION_AUTOPLAY_AUDIBLE || aType == PERMISSION_AUTOPLAY_INAUDIBLE) {
            if (SettingsStore.getInstance(mContext).isAutoplayEnabled()) {
                callback.grant();
            } else {
                callback.reject();
            }
            return;
        }

        PermissionWidget.PermissionType type;
        if (aType == PERMISSION_DESKTOP_NOTIFICATION) {
            type = PermissionWidget.PermissionType.Notification;
        } else if (aType == PERMISSION_GEOLOCATION) {
            type = PermissionWidget.PermissionType.Location;
        } else {
            Log.e(LOGTAG, "onContentPermissionRequest unknown permission: " + aType);
            callback.reject();
            return;
        }

        handlePermission(aUri, type, callback);
    }

    @Override
    public void onMediaPermissionRequest(GeckoSession aSession, String aUri, MediaSource[] aVideo, MediaSource[] aAudio, final MediaCallback aMediaCallback) {
        Log.d(LOGTAG, "onMediaPermissionRequest: " + aUri);

        final MediaSource video = aVideo != null ? aVideo[0] : null;
        final MediaSource audio = aAudio != null ? aAudio[0] : null;
        PermissionWidget.PermissionType type;
        if (video != null && audio != null) {
            type = PermissionWidget.PermissionType.CameraAndMicrophone;
        } else if (video != null) {
            type = PermissionWidget.PermissionType.Camera;
        } else if (audio != null) {
            type = PermissionWidget.PermissionType.Microphone;
        } else {
            aMediaCallback.reject();
            return;
        }

        GeckoSession.PermissionDelegate.Callback callback = new GeckoSession.PermissionDelegate.Callback() {
            @Override
            public void grant() {
                aMediaCallback.grant(video, audio);
            }

            @Override
            public void reject() {
                aMediaCallback.reject();
            }
        };

        handlePermission(aUri, type, callback);
    }

    public boolean isPermissionGranted(@NonNull String permission) {
        return mContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    // Handle app permissions that Gecko doesn't handle itself yet
    public void onAppPermissionRequest(final GeckoSession aSession, String aUri, final String permission, final Callback callback) {
        Log.d(LOGTAG, "onAppPermissionRequest: " + aUri);

        // If the permission is already granted we just grant
        if (mContext.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {

            // Check if we support a rationale for that permission
            PermissionWidget.PermissionType type = null;
            if (permission.equals(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                type = PermissionWidget.PermissionType.ReadExternalStorage;
            }

            if (type != null) {
                // Show rationale
                handlePermission(mContext.getString(R.string.app_name), type, new Callback() {
                    @Override
                    public void grant() {
                        onAndroidPermissionsRequest(aSession, new String[]{permission}, callback);
                    }

                    @Override
                    public void reject() {
                        if (callback != null) {
                            callback.reject();
                        }
                    }
                });

            } else {
                // Let Android handle the permission request
                onAndroidPermissionsRequest(aSession, new String[]{permission}, callback);
            }

        } else {
            if (callback != null) {
                callback.grant();
            }
        }
    }

    public boolean isPermissionDialogVisible() {
        return mPermissionWidget != null && mPermissionWidget.isVisible();
    }
}
