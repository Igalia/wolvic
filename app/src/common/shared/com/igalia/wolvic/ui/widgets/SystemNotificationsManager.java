package com.igalia.wolvic.ui.widgets;

import android.util.Log;

import androidx.annotation.NonNull;

import com.igalia.wolvic.R;
import com.igalia.wolvic.ui.adapters.SystemNotification;
import com.igalia.wolvic.utils.SystemUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SystemNotificationsManager {
    private static final String LOGTAG = SystemUtils.createLogtag(SystemNotificationsManager.class);

    private static final int NOTIFICATION_DURATION = 5000;

    private static final SystemNotificationsManager mInstance = new SystemNotificationsManager();

    // TODO notifications should be in permanent storage until the user dismisses them
    private final List<SystemNotification> mSystemNotifications = Collections.synchronizedList(new ArrayList<>());

    private final Set<ChangeListener> mChangeListeners = new LinkedHashSet<>();

    public interface ChangeListener {
        void onDataChanged(List<SystemNotification> newData);

        void onItemAdded(int index, SystemNotification newItem);
    }

    private SystemNotificationsManager() {
    }

    public static SystemNotificationsManager getInstance() {
        return mInstance;
    }

    public void addNewSystemNotification(SystemNotification notification, UIWidget parent) {
        Log.i(LOGTAG, "PushKit: add system notification = " + notification + " , parent widget = " + parent);

        mSystemNotifications.add(0, notification);
        notifyListenersItemAdded(0, notification);

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

    public void addChangeListener(@NonNull ChangeListener listener) {
        mChangeListeners.add(listener);
    }

    public void removeChangeListener(@NonNull ChangeListener listener) {
        mChangeListeners.remove(listener);
    }

    private void notifyListenersDataChanged(List<SystemNotification> newData) {
        for (ChangeListener listener : mChangeListeners) {
            listener.onDataChanged(newData);
        }
    }

    private void notifyListenersItemAdded(int index, SystemNotification newItem) {
        for (ChangeListener listener : mChangeListeners) {
            listener.onItemAdded(index, newItem);
        }
    }
}
