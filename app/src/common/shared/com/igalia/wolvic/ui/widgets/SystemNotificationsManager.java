package com.igalia.wolvic.ui.widgets;

import android.util.Log;

import androidx.annotation.NonNull;

import com.igalia.wolvic.R;
import com.igalia.wolvic.ui.adapters.SystemNotification;
import com.igalia.wolvic.utils.SystemUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SystemNotificationsManager {
    private static final String LOGTAG = SystemUtils.createLogtag(SystemNotificationsManager.class);

    private static final int NOTIFICATION_DURATION = 5000;

    private static final SystemNotificationsManager mInstance = new SystemNotificationsManager();

    // TODO the system notifications should be stored until the used has dismissed them
    private final List<SystemNotification> mSystemNotifications = Collections.synchronizedList(new ArrayList<>());

    private final Set<ChangeListener> mChangeListeners = new LinkedHashSet<>();

    private SystemNotificationsManager() {
        mSystemNotifications.add(new SystemNotification("TEST Notification title", "Notification body",
                new SystemNotification.Action(SystemNotification.Action.OPEN_URL, null, "https://google.com", null),
                Calendar.getInstance()));
        mSystemNotifications.add(new SystemNotification("TEST Notification title 2", "Notification body 2",
                new SystemNotification.Action(SystemNotification.Action.OPEN_URL, null, "https://reddit.com", null),
                Calendar.getInstance()));
    }

    public static SystemNotificationsManager getInstance() {
        return mInstance;
    }

    public void show(SystemNotification notification, UIWidget parent) {

        Log.i(LOGTAG, "PushKit: show notification = " + notification + " , parent widget = " + parent);

        mSystemNotifications.add(0, notification);
        notifyChangeListeners(mSystemNotifications);

        NotificationManager.Notification tooltipNotification = new NotificationManager.Builder(parent)
                .withString(notification.getTitle())
                .withZTranslation(25.0f)
                .withPosition(NotificationManager.Notification.TOP)
                .withDuration(NOTIFICATION_DURATION)
                .withLayout(R.layout.system_notification)
                .withCurved(true).build();
        NotificationManager.show(10, tooltipNotification);
    }

    public List<SystemNotification> getSystemNotifications() {
        return mSystemNotifications;
    }

    // TODO notify of individual changes to the list
    public interface ChangeListener {
        void onDataChanged(List<SystemNotification> newData);
    }

    public void addChangeListener(@NonNull ChangeListener listener) {
        mChangeListeners.add(listener);
    }

    public void removeChangeListener(@NonNull ChangeListener listener) {
        mChangeListeners.remove(listener);
    }

    private void notifyChangeListeners(List<SystemNotification> newData) {
        for (ChangeListener listener : mChangeListeners) {
            listener.onDataChanged(newData);
        }
    }
}
