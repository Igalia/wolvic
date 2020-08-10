package org.mozilla.vrbrowser.ui.widgets;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.DimenRes;
import androidx.annotation.IntDef;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.ui.views.UIButton;
import org.mozilla.vrbrowser.ui.widgets.NotificationManager.Notification.NotificationPosition;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class NotificationManager {

    private static final int DEFAULT_DURATION = 3000;

    private static HashMap<Integer, NotificationData> mData = new HashMap<>();

    private static class NotificationData {

        private TooltipWidget mNotificationView;
        private Notification mNotification;
        private Runnable mHideTask;

        public NotificationData(@NonNull TooltipWidget view, @NonNull Notification notification, @NonNull Runnable hideTask) {
            mNotificationView = view;
            mNotification = notification;
            mHideTask = hideTask;
        }

    }

    public static class Notification {

        @IntDef(value = { MIDDLE, TOP, BOTTOM, LEFT, RIGHT})
        public @interface NotificationPosition {}
        public static final int MIDDLE = 0;
        public static final int TOP = 1;
        public static final int BOTTOM = 2;
        public static final int LEFT = 4;
        public static final int RIGHT = 8;

        private UIWidget mParent;
        private View mView;
        private String mString;
        private float mMargin;
        private float mZTranslation;
        private @NotificationPosition int mPositionFlags;
        private @DimenRes int mDensity;
        private @LayoutRes int mLayoutRes;
        private int mDuration;
        private boolean mCurved;
        private boolean mAutoHide;

        public Notification(@NonNull Builder builder) {
            mParent = builder.parent;
            mView = builder.view;
            mString = builder.string;
            mMargin = builder.margin;
            mZTranslation = builder.zTranslation;
            mPositionFlags = builder.positionFlags;
            mDensity = builder.density;
            mLayoutRes = builder.layoutRes;
            mDuration = builder.duration;
            mCurved = builder.curved;
            mAutoHide = builder.autoHide;
        }
    }

    public static class Builder {

        private UIWidget parent;
        private View view = null;
        private String string;
        private float margin = 0.0f;
        private float zTranslation = 0.0f;
        private @NotificationPosition int positionFlags = Notification.MIDDLE;
        private @DimenRes int density;
        private @LayoutRes int layoutRes = R.layout.library_notification;
        private int duration = DEFAULT_DURATION;
        private boolean curved = false;
        private boolean autoHide = true;

        public Builder(@NonNull UIWidget parent) {
            this.parent = parent;
            this.view = parent;
            this.density = R.dimen.tooltip_default_density;
        }

        public Builder withString(@StringRes int res) {
            this.string = parent.getContext().getString(res);
            return this;
        }

        public Builder withString(String string) {
            this.string = string;
            return this;
        }

        public Builder withView(@NonNull View view) {
            this.view = view;
            return this;
        }

        public Builder withMargin(float margin){
            this.margin = margin;
            return this;
        }

        public Builder withPosition(@NotificationPosition int positionFlags) {
            this.positionFlags = positionFlags;
            return this;
        }

        public Builder withZTranslation(float translation) {
            this.zTranslation = translation;
            return this;
        }

        public Builder withDensity(@DimenRes int density) {
            this.density = density;
            return this;
        }

        public Builder withLayout(@LayoutRes int res) {
            this.layoutRes = res;
            return this;
        }

        public Builder withDuration(int duration) {
            this.duration = duration;
            return this;
        }

        public Builder withAutoHide(boolean hide) {
            this.autoHide = hide;
            return this;
        }

        public Builder withCurved(boolean curved) {
            this.curved = curved;
            return this;
        }

        public Notification build(){
            return new Notification(this);
        }
    }


    public static void show(int notificationId, @NonNull Notification notification) {
        if (mData.containsKey(notificationId)) {
            return;
        }

        TooltipWidget notificationView = new TooltipWidget(notification.mParent.getContext(), notification.mLayoutRes);
        notificationView.setDelegate(() -> hide(notificationId));

        setPlacement(notificationView, notification);

        notificationView.setText(notification.mString);
        notificationView.setCurvedMode(false);
        notificationView.show(UIWidget.KEEP_FOCUS);

        if (notification.mView instanceof UIButton) {
            ((UIButton)notification.mView).setNotificationMode(true);
        }

        Runnable hideTask = () -> hide(notificationId);
        if (notification.mAutoHide) {
            if (notification.mView != null) {
                notification.mView.postDelayed(hideTask, notification.mDuration);
            }
        }

        mData.put(notificationId, new NotificationData(notificationView, notification, hideTask));
    }

    public static void hide(int notificationId) {
        if (!mData.containsKey(notificationId)) {
            return;
        }

        NotificationData data = mData.get(notificationId);
        if (data != null && data.mNotificationView.isVisible()) {
            hideNotification(data);
            mData.remove(notificationId);
        }
    }

    public static void hideAll() {
        Iterator<Map.Entry<Integer, NotificationData>> it = mData.entrySet().iterator();
        while (it.hasNext()) {
            hideNotification(it.next().getValue());
            it.remove();
        }
    }

    private static void hideNotification(@NonNull NotificationData data) {
        data.mNotificationView.removeCallbacks(data.mHideTask);

        data.mNotificationView.hide(UIWidget.REMOVE_WIDGET);

        if (data.mNotification.mView instanceof UIButton) {
            ((UIButton)data.mNotification.mView).setNotificationMode(false);
        }
    }

    private static void setPlacement(@NonNull TooltipWidget notificationView, @NonNull Notification notification) {
        notificationView.getPlacement().parentHandle = notification.mParent.getHandle();
        notificationView.getPlacement().density = WidgetPlacement.floatDimension(notification.mParent.getContext(), notification.mDensity);
        notificationView.getPlacement().translationZ = notification.mZTranslation;
        notificationView.getPlacement().cylinder = notification.mCurved;

        Rect offsetViewBounds = new Rect();
        if (notification.mView != null) {
            notification.mParent.getDrawingRect(offsetViewBounds);
            notification.mParent.offsetDescendantRectToMyCoords(notification.mView, offsetViewBounds);
        }

        int width = 0;
        int height = 0;
        float ratio = 1.0f;
        if (notification.mView != null) {
            width = notification.mView.getWidth();
            height = notification.mView.getHeight();
            ratio = WidgetPlacement.viewToWidgetRatio(notification.mParent.getContext(), notification.mParent);
        }

        if (notification.mView == null) {
            notificationView.getPlacement().anchorX = 0.5f;
            notificationView.getPlacement().parentAnchorX = 0.5f;
            notificationView.getPlacement().anchorY = 0.5f;
            notificationView.getPlacement().parentAnchorY = 0.5f;

            if ((notification.mPositionFlags & Notification.TOP) == Notification.TOP) {
                notificationView.getPlacement().anchorY = 0.0f;
                notificationView.getPlacement().parentAnchorY = 1.0f;
                notificationView.getPlacement().translationY = notification.mMargin;
            }

            if ((notification.mPositionFlags & Notification.BOTTOM) == Notification.BOTTOM) {
                notificationView.getPlacement().anchorY = 1.0f;
                notificationView.getPlacement().parentAnchorY = 0.0f;
                notificationView.getPlacement().translationY = -notification.mMargin;
            }

            if ((notification.mPositionFlags & Notification.LEFT) == Notification.LEFT) {
                notificationView.getPlacement().anchorX = 1.0f;
                notificationView.getPlacement().parentAnchorX = 0.0f;
                notificationView.getPlacement().translationX = -notification.mMargin;
            }

            if ((notification.mPositionFlags & Notification.RIGHT) == Notification.RIGHT) {
                notificationView.getPlacement().anchorX = 0.0f;
                notificationView.getPlacement().parentAnchorX = 1.0f;
                notificationView.getPlacement().translationX = notification.mMargin;
            }

        } else {
            notificationView.getPlacement().parentAnchorX = 0.0f;
            notificationView.getPlacement().parentAnchorY = 1.0f;
            notificationView.getPlacement().anchorX = 0.5f;
            notificationView.getPlacement().anchorY = 0.5f;

            notificationView.getPlacement().translationX = (offsetViewBounds.left + (width / 2.0f)) * ratio;
            notificationView.getPlacement().translationY = -(offsetViewBounds.bottom - (height / 2.0f)) * ratio;

            if ((notification.mPositionFlags & Notification.TOP) == Notification.TOP) {
                notificationView.getPlacement().anchorY = 0.0f;
                notificationView.getPlacement().translationY = (offsetViewBounds.top + notification.mMargin) * ratio;
            }

            if ((notification.mPositionFlags & Notification.BOTTOM) == Notification.BOTTOM) {
                notificationView.getPlacement().anchorY = 1.0f;
                notificationView.getPlacement().translationY = -(offsetViewBounds.bottom + notification.mMargin) * ratio;
            }

            if ((notification.mPositionFlags & Notification.LEFT) == Notification.LEFT) {
                notificationView.getPlacement().anchorX = 1.0f;
                notificationView.getPlacement().translationX = (offsetViewBounds.left - notification.mMargin) * ratio;
            }

            if ((notification.mPositionFlags & Notification.RIGHT) == Notification.RIGHT) {
                notificationView.getPlacement().anchorX = 0.0f;
                notificationView.getPlacement().translationX = (offsetViewBounds.left + width + notification.mMargin) * ratio;
            }
        }
    }

}
