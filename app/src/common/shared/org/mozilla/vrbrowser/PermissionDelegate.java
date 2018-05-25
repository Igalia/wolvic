package org.mozilla.vrbrowser;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.ui.KeyboardWidget;
import org.mozilla.vrbrowser.ui.PermissionWidget;

import java.util.ArrayList;

public class PermissionDelegate implements GeckoSession.PermissionDelegate {
    static final int PERMISSION_REQUEST_CODE = 1143;
    static final String LOGTAG = "VRB";
    private Context mContext;
    private int mParentWidgetHandle;
    private WidgetManagerDelegate mWidgetManager;
    private GeckoSession.PermissionDelegate.Callback mCallback;
    private PermissionWidget mPermissionWidget;

    public PermissionDelegate(Context aContext, WidgetManagerDelegate aWidgetManager) {
        mContext = aContext;
        mWidgetManager = aWidgetManager;
        SessionStore.get().setPermissionDelegate(this);
    }

    public void setParentWidgetHandle(int aHandle) {
        mParentWidgetHandle = aHandle;
    }

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

    private void handleContentPermission(final String aUri, final PermissionWidget.PermissionType aType, final Callback aCallback) {
        if (mPermissionWidget == null) {
            WidgetPlacement placement = new WidgetPlacement();
            placement.widgetType = Widget.PermissionWidget;
            placement.parentHandle = mParentWidgetHandle;
            placement.width = 300;
            placement.height = 230;
            placement.parentAnchorX = 0.5f;
            placement.parentAnchorY = 0.5f;
            placement.anchorX = 0.5f;
            placement.anchorY = 0.5f;
            placement.translationZ = 400.0f;
            placement.worldScale = 0.8f;

            mWidgetManager.addWidget(placement, true, new WidgetManagerDelegate.WidgetAddCallback() {
                @Override
                public void onWidgetAdd(Widget aWidget) {
                    mPermissionWidget = (PermissionWidget) aWidget;
                    handleContentPermission(aUri, aType, aCallback);
                }
            });

            return;
        }

        mPermissionWidget.showPrompt(aUri, aType, aCallback);
    }

    public void release() {
        SessionStore.get().setPermissionDelegate(null);
        mCallback = null;
        mContext = null;
        mWidgetManager = null;
    }



    @Override
    public void onAndroidPermissionsRequest(GeckoSession aSession, String[] permissions, Callback aCallback) {
        Log.d(LOGTAG, "onAndroidPermissionsRequest: " + permissions);
        ArrayList<String> missingPermissions = new ArrayList<>();
        for (String permission: permissions) {
            if (mContext.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        if (missingPermissions.size() == 0) {
            Log.d(LOGTAG, "Android permissions granted");
            aCallback.grant();
        } else {
            Log.d(LOGTAG, "Request Android permissions: " + missingPermissions);
            mCallback = aCallback;
            ((Activity)mContext).requestPermissions(missingPermissions.toArray(new String[missingPermissions.size()]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onContentPermissionRequest(GeckoSession aSession, String aUri, int aType, String access, Callback callback) {
        Log.d(LOGTAG, "onContentPermissionRequest: " + aUri + " " + aType + " " + access);
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

        handleContentPermission(aUri, type, callback);
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

        handleContentPermission(aUri, type, callback);
    }
}
