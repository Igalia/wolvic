package com.igalia.wolvic.browser.api.impl;

import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WResult;
import com.igalia.wolvic.browser.api.WSession;

import org.chromium.wolvic.PermissionManagerBridge;

import java.util.ArrayList;
import java.util.Arrays;
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

    private static @WSession.Permission int toContentPermissionType(
            @PermissionManagerBridge.ContentPermissionType int permission) {
        switch (permission) {
            case PermissionManagerBridge.ContentPermissionType.GEOLOCATION:
                return WSession.PermissionDelegate.PERMISSION_GEOLOCATION;
            case PermissionManagerBridge.ContentPermissionType.DESKTOP_NOTIFICATION:
                return WSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION;
            case PermissionManagerBridge.ContentPermissionType.PERSISTENT_STORAGE:
                return WSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE;
            case PermissionManagerBridge.ContentPermissionType.XR:
                return WSession.PermissionDelegate.PERMISSION_XR;
            case PermissionManagerBridge.ContentPermissionType.AUTOPLAY_INAUDIBLE:
                return WSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE;
            case PermissionManagerBridge.ContentPermissionType.AUTOPLAY_AUDIBLE:
                return WSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE;
            case PermissionManagerBridge.ContentPermissionType.MEDIA_KEY_SYSTEM_ACCESS:
                return WSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS;
            case PermissionManagerBridge.ContentPermissionType.TRACKING:
                return WSession.PermissionDelegate.PERMISSION_TRACKING;
            case PermissionManagerBridge.ContentPermissionType.STORAGE_ACCESS:
                return WSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS;
        }
        throw new IllegalArgumentException("Invalid permission: " + permission);
    }

    private static WSession.PermissionDelegate.ContentPermission toContentPermission(
            String url, boolean isOffTheRecord,
            @PermissionManagerBridge.ContentPermissionType int permission) {
        return new WSession.PermissionDelegate.ContentPermission(url,
                null,
                isOffTheRecord,
                toContentPermissionType(permission),
                WSession.PermissionDelegate.ContentPermission.VALUE_PROMPT,
                isOffTheRecord ? OFF_THE_RECORD_SESSION_ID : SESSION_ID);
    }

    private static @PermissionManagerBridge.PermissionStatus int fromPermissionValue(
            @WSession.PermissionDelegate.ContentPermission.Value int value) {
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

    private static int toWolvicMediaSourceType(
            @PermissionManagerBridge.MediaSourceType int sourceType) {
        switch (sourceType) {
            case PermissionManagerBridge.MediaSourceType.CAMERA:
                return WSession.PermissionDelegate.MediaSource.SOURCE_CAMERA;
            case PermissionManagerBridge.MediaSourceType.SCREEN:
                return WSession.PermissionDelegate.MediaSource.SOURCE_SCREEN;
            case PermissionManagerBridge.MediaSourceType.MICROPHONE:
                return WSession.PermissionDelegate.MediaSource.SOURCE_MICROPHONE;
            case PermissionManagerBridge.MediaSourceType.AUDIOCAPTURE:
                return WSession.PermissionDelegate.MediaSource.SOURCE_AUDIOCAPTURE;
            default:
                return WSession.PermissionDelegate.MediaSource.SOURCE_OTHER;
        }
    }

    private static int toWolvicMediaType(
            @PermissionManagerBridge.MediaType int mediaType) {
        switch (mediaType) {
            case PermissionManagerBridge.MediaType.VIDEO:
                return WSession.PermissionDelegate.MediaSource.TYPE_VIDEO;
            case PermissionManagerBridge.MediaType.AUDIO:
                return WSession.PermissionDelegate.MediaSource.TYPE_AUDIO;
        }
        throw new IllegalArgumentException("Invalid media type: " + mediaType);
    }

    private static WSession.PermissionDelegate.MediaSource toWolvicMediaSource(
            PermissionManagerBridge.MediaSource source) {
        return new WSession.PermissionDelegate.MediaSource(source.id, source.name,
                toWolvicMediaSourceType(source.source),
                toWolvicMediaType(source.type));
    }

    private static WSession.PermissionDelegate.MediaSource[] toWolvicMediaSources(
            PermissionManagerBridge.MediaSource[] sources) {
        return Arrays.stream(sources).map(ChromiumPermissionDelegate::toWolvicMediaSource).toArray(
                WSession.PermissionDelegate.MediaSource[]::new);
    }

    private @PermissionManagerBridge.PermissionStatus WResult<Integer> requestContentPermission(
            @PermissionManagerBridge.ContentPermissionType int permissionType, String url,
            boolean isOffTheRecord) {
        if (mPermissionDelegate == null) {
            return WResult.fromValue(PermissionManagerBridge.PermissionStatus.PROMPT);
        }
        // Automatically deny any Chromium permissions that are not supported by Wolvic.
        if (permissionType == PermissionManagerBridge.ContentPermissionType.NOT_SUPPORTED) {
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
    private @PermissionManagerBridge.PermissionStatus WResult<List<Integer>> requestContentPermissions(
            @PermissionManagerBridge.ContentPermissionType int[] permissionTypes,
            String url,
            boolean isOffTheRecord,
            int currentPermission,
            @PermissionManagerBridge.PermissionStatus List<Integer> currentResults) {
        if (currentPermission == permissionTypes.length) {
            return WResult.fromValue(currentResults);
        }

        return requestContentPermission(permissionTypes[currentPermission], url,
                isOffTheRecord).then(status -> {
            currentResults.add(status);
            return requestContentPermissions(
                    permissionTypes, url, isOffTheRecord, currentPermission + 1, currentResults);
        });
    }

    private @PermissionManagerBridge.PermissionStatus WResult<Integer> requestAndroidPermission(
            String androidPermission) {
        if (androidPermission.equals(PermissionManagerBridge.NO_ANDROID_PERMISSION)) {
            return WResult.fromValue(PermissionManagerBridge.PermissionStatus.ALLOW);
        }

        @PermissionManagerBridge.PermissionStatus WResult<Integer> result = WResult.create();
        mPermissionDelegate.onAndroidPermissionsRequest(mSession, new String[]{androidPermission}, new WSession.PermissionDelegate.Callback() {
            @Override
            public void grant() {
                result.complete(PermissionManagerBridge.PermissionStatus.ALLOW);
            }

            @Override
            public void reject() {
                result.complete(PermissionManagerBridge.PermissionStatus.DENY);
            }
        });
        return result;
    }

    /**
     * Iterates over androidPermissions and starts an android permission dialog for each permission.
     * @param currentPermission index of the permission processed on this step
     * @param currentResults accumulated permission results for already processed permissions
     */
    private @PermissionManagerBridge.PermissionStatus WResult<List<Integer>> requestAndroidPermissions(
            String[] androidPermissions,
            int currentPermission,
            @PermissionManagerBridge.PermissionStatus List<Integer> currentResults) {
        if (currentPermission == androidPermissions.length) {
            return WResult.fromValue(currentResults);
        }

        return requestAndroidPermission(androidPermissions[currentPermission]).then(status -> {
            currentResults.add(status);
            return requestAndroidPermissions(androidPermissions, currentPermission + 1,
                    currentResults);
        });
    }

    private void invokeCallback(@PermissionManagerBridge.PermissionStatus List<Integer> statuses,
                           PermissionManagerBridge.PermissionCallback callback) {
        int[] result = statuses.stream().mapToInt(Integer::intValue).toArray();
        callback.onPermissionResult(result);
    }

    @Override
    public void onContentPermissionRequest(
            @PermissionManagerBridge.ContentPermissionType int[] permissionTypes, String url,
            boolean isOffTheRecord, PermissionManagerBridge.PermissionCallback permissionCallback) {
        requestContentPermissions(permissionTypes, url, isOffTheRecord, 0, new ArrayList<>()).then(
                statuses -> {
                    invokeCallback(statuses, permissionCallback);
                    return null;
                });
    }

    @Override
    public void onAndroidPermissionRequest(String[] permissionTypes,
                                           PermissionManagerBridge.PermissionCallback permissionCallback) {
        requestAndroidPermissions(permissionTypes, 0, new ArrayList<>()).then(statuses -> {
            invokeCallback(statuses, permissionCallback);
            return null;
        });
    }

    @Override
    public void onMediaPermissionRequest(String url,
                                         PermissionManagerBridge.MediaSource[] video,
                                         PermissionManagerBridge.MediaSource[] audio,
                                         PermissionManagerBridge.MediaCallback callback) {
        mPermissionDelegate.onMediaPermissionRequest(mSession, url, toWolvicMediaSources(video),
                toWolvicMediaSources(audio), new WSession.PermissionDelegate.MediaCallback() {
                    @Override
                    public void grant(@Nullable String video, @Nullable String audio) {
                        callback.onMediaPermissionResult(true, video, audio);
                    }

                    @Override
                    public void grant(@Nullable WSession.PermissionDelegate.MediaSource video,
                                      @Nullable WSession.PermissionDelegate.MediaSource audio) {
                        grant(video == null ? null : video.id, audio == null ? null : audio.id);
                    }

                    @Override
                    public void reject() {
                        callback.onMediaPermissionResult(false, null, null);
                    }
                });
    }
}
