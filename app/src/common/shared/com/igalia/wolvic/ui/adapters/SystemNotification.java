package com.igalia.wolvic.ui.adapters;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.util.Calendar;
import java.util.Objects;

/**
 * Represents a system notification that will be displayed inside of Wolvic instead of
 * Android's Notification Center. For example, it can be used to represent push messages.
 */
public class SystemNotification {

    private String mTitle;
    private String mBody;
    private Action mAction;
    private Calendar mReceivedDate;

    public SystemNotification(@NonNull String title, @NonNull String body, Action action, @NonNull Calendar receivedDate) {
        mTitle = title;
        mBody = body;
        mAction = action;
        this.mReceivedDate = receivedDate;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getBody() {
        return mBody;
    }

    public Action getAction() {
        return mAction;
    }

    public Calendar getReceivedDate() {
        return mReceivedDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SystemNotification that = (SystemNotification) o;
        return mTitle.equals(that.mTitle) && mBody.equals(that.mBody) &&
                Objects.equals(mAction, that.mAction) && mReceivedDate.equals(that.mReceivedDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTitle, mBody, mAction, mReceivedDate);
    }

    @Override
    public String toString() {
        return "SystemNotification{ " + " mTitle='" + mTitle + " , mBody='" + mBody +
                " , mReceivedDate=" + mReceivedDate + " , mAction=" + mAction + " }";
    }

    public static class Action {

        private final int mType;
        private final String mIntent;
        private final String mUrl;
        private final String mAction;

        public int getType() {
            return mType;
        }

        public String getIntent() {
            return mIntent;
        }

        public String getUrl() {
            return mUrl;
        }

        public String getAction() {
            return mAction;
        }

        @IntDef(value = {OPEN_APP_PAGE, OPEN_URL, OPEN_APP})
        public @interface ActionType {
        }

        public static final int OPEN_APP_PAGE = 1;
        public static final int OPEN_URL = 2;
        public static final int OPEN_APP = 3;

        public Action(@ActionType int type, String intent, String url, String action) {

            mType = type;
            mIntent = intent;
            mUrl = url;
            mAction = action;
        }

        @NonNull
        @Override
        public String toString() {
            return "Action{ Type=" + mType + " , Intent='" + mIntent + " , Url='" + mUrl + " , Action='" + mAction + " }";
        }
    }
}
