package com.igalia.wolvic.browser.api.impl;

import com.igalia.wolvic.browser.api.WResult;
import com.igalia.wolvic.browser.api.WSession;

import org.chromium.wolvic.PermissionManagerBridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ChromiumPermissionDelegate implements PermissionManagerBridge.Delegate {
    private static final String SESSION_ID = "WOLVIC_SESSION_ID";
    private static final String OFF_THE_RECORD_SESSION_ID = "WOLVIC_OFF_THE_RECORD_SESSION_ID";

    private final WSession mSession;
    private final WSession.PermissionDelegate mPermissionDelegate;

    public ChromiumPermissionDelegate(WSession session,
                                      WSession.PermissionDelegate mPermissionDelegate) {
        this.mSession = session;
        this.mPermissionDelegate = mPermissionDelegate;
        PermissionManagerBridge.get().setDelegate(this);
    }

    private static int toContentPermissionType(PermissionManagerBridge.PermissionType permission) {
        switch (permission) {
            case GEOLOCATION:
                return WSession.PermissionDelegate.PERMISSION_GEOLOCATION;
            case DESKTOP_NOTIFICATION:
                return WSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION;
            case PERSISTENT_STORAGE:
                return WSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE;
            case XR:
                return WSession.PermissionDelegate.PERMISSION_XR;
            case AUTOPLAY_INAUDIBLE:
                return WSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE;
            case AUTOPLAY_AUDIBLE:
                return WSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE;
            case MEDIA_KEY_SYSTEM_ACCESS:
                return WSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS;
            case TRACKING:
                return WSession.PermissionDelegate.PERMISSION_TRACKING;
            case STORAGE_ACCESS:
                return WSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS;
        }
        throw new IllegalArgumentException("Invalid permission: " + permission);
    }

    private static WSession.PermissionDelegate.ContentPermission toContentPermission(
            String url, boolean isOffTheRecord, PermissionManagerBridge.PermissionType permission) {
        return new WSession.PermissionDelegate.ContentPermission(url,
                null,
                isOffTheRecord,
                toContentPermissionType(permission),
                WSession.PermissionDelegate.ContentPermission.VALUE_PROMPT,
                isOffTheRecord ? OFF_THE_RECORD_SESSION_ID : SESSION_ID);
    }

    private static PermissionManagerBridge.PermissionStatus fromPermissionValue(int value) {
        switch (value) {
            case WSession.PermissionDelegate.ContentPermission.VALUE_ALLOW:
                return PermissionManagerBridge.PermissionStatus.ALLOW;
            case WSession.PermissionDelegate.ContentPermission.VALUE_DENY:
                return PermissionManagerBridge.PermissionStatus.DENY;
            case WSession.PermissionDelegate.ContentPermission.VALUE_PROMPT:
                return PermissionManagerBridge.PermissionStatus.PROMPT;
        }
        throw new IllegalArgumentException("Invalid value: " + value);
    }

    private WResult<PermissionManagerBridge.PermissionStatus> requestPermission(
            PermissionManagerBridge.PermissionType permissionType, String url,
            boolean isOffTheRecord) {
        if (mPermissionDelegate == null) {
            return WResult.fromValue(PermissionManagerBridge.PermissionStatus.PROMPT);
        }
        // Automatically deny any Chromium permissions that are not supported by Wolvic.
        if (permissionType == PermissionManagerBridge.PermissionType.NOT_SUPPORTED) {
            return WResult.fromValue(PermissionManagerBridge.PermissionStatus.DENY);
        }

        return Objects.requireNonNull(mPermissionDelegate.onContentPermissionRequest(mSession,
                toContentPermission(url, isOffTheRecord, permissionType))).then(value ->
                WResult.fromValue(fromPermissionValue(value)));
    }

    /**
     * Iterates over permissionTypes and starts a permission dialog for each type.
     * @param currentPermission index of the permission processed on this step
     * @param currentResults accumulated permission results for already processed permissions
     */
    private WResult<List<PermissionManagerBridge.PermissionStatus>> requestPermissions(
            PermissionManagerBridge.PermissionType[] permissionTypes,
            String url,
            boolean isOffTheRecord,
            int currentPermission,
            List<PermissionManagerBridge.PermissionStatus> currentResults) {
        if (currentPermission == permissionTypes.length) {
            return WResult.fromValue(currentResults);
        }

        return requestPermission(permissionTypes[currentPermission], url, isOffTheRecord).then(status -> {
            currentResults.add(status);
            return requestPermissions(
                    permissionTypes, url, isOffTheRecord, currentPermission + 1, currentResults);
        });
    }

    @Override
    public void onPermissionRequest(PermissionManagerBridge.PermissionType[] permissionTypes,
                                    String url,
                                    boolean isOffTheRecord,
                                    PermissionManagerBridge.PermissionCallback permissionCallback) {
        requestPermissions(permissionTypes, url, isOffTheRecord, 0, new ArrayList<>()).then(statuses -> {
            PermissionManagerBridge.PermissionStatus[] result = Objects.requireNonNull(statuses).toArray(
                    new PermissionManagerBridge.PermissionStatus[permissionTypes.length]);
            permissionCallback.onPermissionResult(result);
            return null;
        });
    }
}
