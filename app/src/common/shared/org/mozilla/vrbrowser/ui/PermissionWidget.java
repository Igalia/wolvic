/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.Widget;
import org.mozilla.vrbrowser.WidgetManagerDelegate;
import org.mozilla.vrbrowser.WidgetPlacement;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;

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

        ImageButton cancelButton = findViewById(R.id.permissionCancelButton);

        cancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                handlePermissionResult(false);
            }
        });

        ImageButton allowButton = findViewById(R.id.permissionAllowButton);

        allowButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                handlePermissionResult(true);
            }
        });
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
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(context, R.dimen.permission_distance_from_browser);
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.5f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
    }

    @Override
    public void show() {
        super.show();

        mWidgetManager.fadeOutWorld();
    }

    @Override
    public void hide() {
        super.hide();

        mWidgetManager.fadeInWorld();
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
                iconId = R.drawable.ic_icon_dialog_notification;
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
