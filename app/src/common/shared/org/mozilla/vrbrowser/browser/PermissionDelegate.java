package org.mozilla.vrbrowser.browser;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.PlatformActivity;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.browser.engine.SessionState;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.db.SitePermission;
import org.mozilla.vrbrowser.ui.viewmodel.SitePermissionViewModel;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WindowWidget;
import org.mozilla.vrbrowser.ui.widgets.dialogs.PermissionWidget;
import org.mozilla.vrbrowser.utils.SystemUtils;
import org.mozilla.vrbrowser.utils.UrlUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PermissionDelegate implements GeckoSession.PermissionDelegate, WidgetManagerDelegate.PermissionListener {

    static final int PERMISSION_REQUEST_CODE = 1143;

    static final String LOGTAG = SystemUtils.createLogtag(PermissionDelegate.class);
    private Context mContext;
    private int mParentWidgetHandle;
    private WidgetManagerDelegate mWidgetManager;
    private GeckoSession.PermissionDelegate.Callback mCallback;
    private PermissionWidget mPermissionWidget;
    private SitePermissionViewModel mSitePermissionModel;
    private List<SitePermission> mSitePermissions;

    public PermissionDelegate(Context aContext, WidgetManagerDelegate aWidgetManager) {
        mContext = aContext;
        mWidgetManager = aWidgetManager;
        mWidgetManager.addPermissionListener(this);
        SessionStore.get().setPermissionDelegate(this);
        mSitePermissionModel = new SitePermissionViewModel((Application)aContext.getApplicationContext());
        mSitePermissionModel.getAll().observeForever(mSitePermissionObserver);
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

    private Observer<List<SitePermission>> mSitePermissionObserver = sites -> {
        mSitePermissions = sites;
    };

    void handleWebXRPermission(GeckoSession aGeckoSession, final String aUri, final Callback aCallback) {
        Session session = SessionStore.get().getSession(aGeckoSession);
        if (session == null || !SettingsStore.getInstance(mContext).isWebXREnabled()) {
            aCallback.reject();
            return;
        }
        final String domain = UrlUtils.getHost(aUri);

        @Nullable SitePermission site = null;
        if (mSitePermissions != null) {
            site = mSitePermissions.stream()
                    .filter(sitePermission -> sitePermission.category == SitePermission.SITE_PERMISSION_WEBXR &&
                            sitePermission.url.equalsIgnoreCase(domain))
                    .findFirst().orElse(null);
        }

        if (site == null) {
            aCallback.grant();
            session.setWebXRState(SessionState.WEBXR_ALLOWED);
        } else {
            aCallback.reject();
            session.setWebXRState(SessionState.WEBXR_BLOCKED);
        }
    }

    public void release() {
        mSitePermissionModel.getAll().removeObserver(mSitePermissionObserver);
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
        ArrayList<String> filteredPermissions = new ArrayList<>();
        for (String permission: permissions) {
            if (PlatformActivity.filterPermission(permission)) {
                Log.d(LOGTAG, "Skipping permission: " + permission);
                filteredPermissions.add(permission);
                continue;
            }
            Log.d(LOGTAG, "permission = " + permission);
            if (mContext.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        if (missingPermissions.size() == 0) {
            if (filteredPermissions.size() == 0) {
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
            handleWebXRPermission(aSession, aUri, callback);
            return;
        }

        if (aType == PERMISSION_AUTOPLAY_INAUDIBLE) {
            // https://hacks.mozilla.org/2019/02/firefox-66-to-block-automatically-playing-audible-video-and-audio/
            callback.grant();
            return;

        } else if(aType == PERMISSION_AUTOPLAY_AUDIBLE) {
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
        } else if (aType == PERMISSION_MEDIA_KEY_SYSTEM_ACCESS) {
            WindowWidget windowWidget = mWidgetManager.getFocusedWindow();
            Runnable enableDrm = () -> {
                Session session = SessionStore.get().getSession(aSession);
                if (SettingsStore.getInstance(mContext).isDrmContentPlaybackEnabled()) {
                    if (session != null) {
                        session.setDrmState(SessionState.DRM_ALLOWED);
                    }
                    callback.grant();
                } else {
                    if (session != null) {
                        session.setDrmState(SessionState.DRM_BLOCKED);
                    }
                    callback.reject();
                }
            };
            if (SettingsStore.getInstance(mContext).isDrmContentPlaybackSet()) {
                enableDrm.run();

            } else {
                windowWidget.showFirstTimeDrmDialog(enableDrm);
            }
            windowWidget.setDrmUsed(true);
            return;
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

        // Temporary fix for https://bugzilla.mozilla.org/show_bug.cgi?id=1621380
        if ((type == PermissionWidget.PermissionType.Camera ||
                type == PermissionWidget.PermissionType.CameraAndMicrophone)) {
            callback.reject();
            return;
        }

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
    public void addPermissionException(@NonNull String uri, @SitePermission.Category int category) {
        @Nullable SitePermission site = mSitePermissions.stream()
                .filter((item) -> item.category == category && item.url.equals(uri))
                .findFirst().orElse(null);

        if (site == null) {
            site = new SitePermission(uri, "", category);
            mSitePermissions.add(site);
        }
        mSitePermissionModel.insertSite(site);

        // Reload URIs with the same domain
        for (WindowWidget window: mWidgetManager.getWindows().getCurrentWindows()) {
            Session session = window.getSession();
            if (uri.equalsIgnoreCase(UrlUtils.getHost(session.getCurrentUri()))) {
                session.reload(GeckoSession.LOAD_FLAGS_BYPASS_CACHE);
            }
        }

    }

    public void removePermissionException(String uri, @SitePermission.Category int category) {
        @Nullable SitePermission site = mSitePermissions.stream()
                .filter((item) -> item.category == category && item.url.equals(uri))
                .findFirst().orElse(null);

        mSitePermissions.removeIf(sitePermission -> sitePermission.url.equals(uri));
        if (site != null) {
            mSitePermissionModel.deleteSite(site);
        }

        // Reload URIs with the same domain
        for (WindowWidget window: mWidgetManager.getWindows().getCurrentWindows()) {
            Session session = window.getSession();
            if (uri.equalsIgnoreCase(UrlUtils.getHost(session.getCurrentUri()))) {
                session.reload(GeckoSession.LOAD_FLAGS_BYPASS_CACHE);
            }
        }

    }
}
