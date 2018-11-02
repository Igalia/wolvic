/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.R;

import java.net.URI;

public class PermissionWidget extends UIWidget implements WidgetManagerDelegate.FocusChangeListener {

    private static final String LOGTAG = "VRB";

    private TextView mPermissionMessage;
    private ImageView mPermissionIcon;
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

    public PermissionWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public PermissionWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.permission, this);

        mWidgetManager.addFocusChangeListener(this);

        mPermissionIcon = findViewById(R.id.permissionIcon);
        mPermissionMessage = findViewById(R.id.permissionText);

        Button cancelButton = findViewById(R.id.permissionCancelButton);
        cancelButton.setOnClickListener(v -> handlePermissionResult(false));

        Button allowButton = findViewById(R.id.permissionAllowButton);
        allowButton.setOnClickListener(v -> handlePermissionResult(true));
    }

    @Override
    public void releaseWidget() {
        mWidgetManager.removeFocusChangeListener(this);

        super.releaseWidget();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        Context context = getContext();
        aPlacement.width = WidgetPlacement.dpDimension(context, R.dimen.permission_width);
        aPlacement.height = WidgetPlacement.dpDimension(context, R.dimen.permission_height);
        aPlacement.worldWidth = WidgetPlacement.floatDimension(context, R.dimen.permission_world_width);
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(context, R.dimen.browser_children_z_distance);
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.5f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
    }

    @Override
    public void show() {
        super.show();

        mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);
    }

    @Override
    public void hide() {
        super.hide();

        mWidgetManager.popWorldBrightness(this);
    }

    public void showPrompt(String aUri, PermissionType aType, GeckoSession.PermissionDelegate.Callback aCallback) {
        int messageId;
        int iconId;
        switch (aType) {
            case Camera:
                messageId = R.string.permission_camera;
                iconId = R.drawable.ic_icon_dialog_camera;
                break;
            case Microphone:
                messageId = R.string.permission_microphone;
                iconId = R.drawable.ic_icon_microphone;
                break;
            case CameraAndMicrophone:
                messageId = R.string.permission_camera_and_microphone;
                iconId = R.drawable.ic_icon_dialog_camera;
                break;
            case Location:
                messageId = R.string.permission_location;
                iconId = R.drawable.ic_icon_dialog_geolocation;
                break;
            case Notification:
                messageId = R.string.permission_notification;
                iconId = R.drawable.ic_icon_dialog_notification;
                break;
            case ReadExternalStorage:
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

        mPermissionMessage.setText(str);
        mPermissionIcon.setImageResource(iconId);

        show();
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

    // WidgetManagerDelegate.FocusChangeListener

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (oldFocus == this && isVisible()) {
            onDismiss();
        }
    }
}
