/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.Log;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.R;

import java.net.URI;

public class PermissionWidget extends PromptDialogWidget {

    private GeckoSession.PermissionDelegate.Callback mPermissionCallback;

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
    protected void initialize(Context aContext) {
        super.initialize(aContext);

        setButtons(new int[] {
                R.string.permission_reject,
                R.string.permission_allow
        });
        setButtonsDelegate(index -> {
            if (index == PromptDialogWidget.NEGATIVE) {
                // Do not allow
                handlePermissionResult(false);

            } else if (index == PromptDialogWidget.POSITIVE) {
                // Allow
                handlePermissionResult(true);
            }
        });
        setCheckboxVisible(false);
        setDescriptionVisible(false);
    }

    public void showPrompt(String aUri, PermissionType aType, GeckoSession.PermissionDelegate.Callback aCallback) {
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

        String requesterName = getRequesterName(aUri);
        String message = String.format(getContext().getString(messageId), requesterName);
        int start = message.indexOf(requesterName);
        int end = start + requesterName.length();
        SpannableStringBuilder str = new SpannableStringBuilder(message);
        str.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        setIcon(iconId);
        setTitle(titleId);
        setBody(str);

        show(REQUEST_FOCUS);
    }

    String getRequesterName(String aUri) {
        try {
            URI uri = new URI(aUri);
            String domain = uri.getHost();
            return domain.startsWith("www.") ? domain.substring(4) : domain;
        }
        catch (Exception ex) {
         return aUri;
        }
    }

    private void handlePermissionResult(boolean aGranted) {
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
