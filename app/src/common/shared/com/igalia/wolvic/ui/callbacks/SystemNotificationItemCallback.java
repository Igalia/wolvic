package com.igalia.wolvic.ui.callbacks;

import android.view.View;

import com.igalia.wolvic.ui.adapters.SystemNotification;

public interface SystemNotificationItemCallback {
    void onClick(View view, SystemNotification item);
    void onDelete(View view, SystemNotification item);
}
