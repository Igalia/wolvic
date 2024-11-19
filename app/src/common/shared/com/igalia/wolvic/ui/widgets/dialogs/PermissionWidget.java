/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets.dialogs;

import android.Manifest;
import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.Log;

import androidx.annotation.NonNull;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.utils.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class PermissionWidget extends PromptDialogWidget {

    private enum DialogType {PERMISSIONS_RATIONALE, WEBSITE_PERMISSIONS}

    private WSession.PermissionDelegate.Callback mPermissionCallback;
    private String mUri;
    private final List<String> mPermissions = new ArrayList<>();
    private PermissionType mPermissionType;
    private DialogType mDialogType = DialogType.WEBSITE_PERMISSIONS;

    public enum PermissionType {
        Camera,
        Microphone,
        CameraAndMicrophone,
        Location,
        Notification,
        ReadExternalStorage
    }

    public PermissionWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    @Override
    public void updateUI() {
        super.updateUI();

        setButtons(new int[] {
                R.string.permission_reject,
                R.string.permission_allow
        });
        setButtonsDelegate((index, isChecked) -> {
            if (index == PromptDialogWidget.NEGATIVE) {
                // Do not allow
                handlePermissionResult(false);

            } else if (index == PromptDialogWidget.POSITIVE) {
                // Allow
                handlePermissionResult(true);
            }
        });
        setCheckboxVisible(mDialogType == DialogType.WEBSITE_PERMISSIONS);
        setChecked(false);
        setCheckboxText(R.string.permissions_dialog_remember_choice);
        setDescriptionVisible(false);

        if (isVisible()) {
            if (mDialogType == DialogType.WEBSITE_PERMISSIONS)
                showWebsitePermissionsPrompt(mUri, mPermissionType, mPermissionCallback);
            else
                showPermissionsRationalePrompt(mPermissions, mPermissionCallback);
        }
    }

    public void showPermissionsRationalePrompt(@NonNull List<String> permissions, @NonNull WSession.PermissionDelegate.Callback aCallback) {
        Log.d(LOGTAG, "showPermissionsRationalePrompt "+aCallback);
        if (permissions.isEmpty()) {
            aCallback.reject();
            return;
        }

        int iconId;
        if (permissions.contains(Manifest.permission.CAMERA)) {
            iconId = R.drawable.ic_icon_dialog_camera;
        } else if (permissions.contains(Manifest.permission.RECORD_AUDIO)) {
            iconId = R.drawable.ic_icon_microphone;
        } else if (permissions.contains((Manifest.permission.ACCESS_COARSE_LOCATION))
                || permissions.contains((Manifest.permission.ACCESS_FINE_LOCATION))) {
            iconId = R.drawable.ic_icon_dialog_geolocation;
        } else if (permissions.contains((Manifest.permission.READ_EXTERNAL_STORAGE))
                || permissions.contains((Manifest.permission.WRITE_EXTERNAL_STORAGE))) {
            iconId = R.drawable.ic_icon_storage;
        } else {
            iconId = R.drawable.ic_settings_privacypolicy;
        }

        StringBuilder messageBuilder = new StringBuilder();
        for (String permission : permissions) {
            if (messageBuilder.length() > 0)
                messageBuilder.append("\n\n");

            switch (permission) {
                case Manifest.permission.CAMERA:
                    messageBuilder.append(getContext().getString(R.string.permission_camera_rationale));
                    break;
                case Manifest.permission.RECORD_AUDIO:
                    messageBuilder.append(getContext().getString(R.string.permission_microphone_rationale));
                    break;
                case Manifest.permission.ACCESS_COARSE_LOCATION:
                case Manifest.permission.ACCESS_FINE_LOCATION:
                    messageBuilder.append(getContext().getString(R.string.permission_location_rationale));
                    break;
                case Manifest.permission.READ_EXTERNAL_STORAGE:
                case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                    messageBuilder.append(getContext().getString(R.string.permission_read_external_storage_rationale));
                    break;
            }
        }

        mPermissionCallback = aCallback;
        mUri = getContext().getString(R.string.app_name);
        mPermissions.clear();
        mPermissions.addAll(permissions);

        setIcon(iconId);
        setTitle(getContext().getString(R.string.permission_rationale_title));
        setBody(messageBuilder.toString());
        setCheckboxVisible(false);

        show(REQUEST_FOCUS);
    }

    public void showWebsitePermissionsPrompt(String aUri, PermissionType aType, WSession.PermissionDelegate.Callback aCallback) {
        int titleId;
        int messageId;
        int iconId;
        switch (aType) {
            case Camera:
                titleId = R.string.security_options_permission_camera;
                messageId = R.string.permission_camera;
                iconId = R.drawable.ic_icon_dialog_camera;
                break;
            case Microphone:
                titleId = R.string.security_options_permission_microphone;
                messageId = R.string.permission_microphone;
                iconId = R.drawable.ic_icon_microphone;
                break;
            case CameraAndMicrophone:
                titleId = R.string.security_options_permission_camera_and_microphone;
                messageId = R.string.permission_camera_and_microphone;
                iconId = R.drawable.ic_icon_dialog_camera;
                break;
            case Location:
                titleId = R.string.security_options_permission_location;
                messageId = R.string.permission_location;
                iconId = R.drawable.ic_icon_dialog_geolocation;
                break;
            case Notification:
                titleId = R.string.security_options_permission_notifications;
                messageId = R.string.permission_notification;
                iconId = R.drawable.ic_icon_dialog_notification;
                break;
            case ReadExternalStorage:
                titleId = R.string.security_options_permission_storage;
                messageId = R.string.permission_read_external_storage;
                iconId = R.drawable.ic_icon_storage;
                break;
            default:
                Log.e(LOGTAG, "Unimplemented permission type: " + aType);
                aCallback.reject();
                return;
        }

        mPermissionCallback = aCallback;
        mUri = aUri;
        mPermissionType = aType;

        String requesterName = getRequesterName(aUri);
        String message = String.format(getContext().getString(messageId), requesterName);
        int start = message.indexOf(requesterName);
        int end = start + requesterName.length();
        SpannableStringBuilder str = new SpannableStringBuilder(message);
        str.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        setIcon(iconId);
        setTitle(titleId);
        setBody(str);
        setCheckboxVisible(true);
        setChecked(false);

        show(REQUEST_FOCUS);
    }

    String getRequesterName(String aUri) {
        if (StringUtils.isEmpty(aUri)) {
            return getContext().getString(R.string.app_name);
        }

        try {
            URI uri = new URI(aUri);
            String domain = uri.getHost();
            return domain.startsWith("www.") ? domain.substring(4) : domain;
        } catch (Exception ex) {
            return aUri;
        }
    }

    private synchronized void handlePermissionResult(boolean aGranted) {
        if (mPermissionCallback == null) {
            return;
        }
        if (aGranted) {
            mPermissionCallback.grant();
        } else {
            mPermissionCallback.reject();
        }
        mPermissionCallback = null;

        onDismiss();
    }
}
