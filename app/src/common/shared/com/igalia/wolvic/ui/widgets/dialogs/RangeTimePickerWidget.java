package com.igalia.wolvic.ui.widgets.dialogs;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.TimePicker;

import com.igalia.wolvic.utils.SystemUtils;

import java.util.Calendar;

/**
 * A time dialog that allows setting a min and max time.
 */
public class RangeTimePickerWidget extends TimePicker implements TimePicker.OnTimeChangedListener {
    static final String LOGTAG = SystemUtils.createLogtag(RangeTimePickerWidget.class);

    private int mMinHour = 0;
    private int mMinMinute = 0;
    private int mMaxHour = 23;
    private int mMaxMinute = 59;
    private int mCurrentHour;
    private int mCurrentMinute;

    public RangeTimePickerWidget(Context context) {
        super(context);
        setOnTimeChangedListener(this);
    }

    public RangeTimePickerWidget(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnTimeChangedListener(this);
    }

    public RangeTimePickerWidget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setOnTimeChangedListener(this);
    }

    public void setMaxTime(int hourIn24, int minute) {
        mMaxHour = hourIn24;
        mMaxMinute = minute;

        Calendar currentTime = Calendar.getInstance();

        Calendar maxTime = Calendar.getInstance();
        maxTime.set(Calendar.HOUR_OF_DAY, mMaxHour);
        maxTime.set(Calendar.MINUTE, mMaxMinute);

        if (currentTime.after(maxTime)) {
            // If current time is after max time, set it to max.
            this.setCurrentHour(mMaxHour);
            this.setCurrentMinute(mMaxMinute);
        }
    }

    public void setMinTime(int hourIn24, int minute) {
        mMinHour = hourIn24;
        mMinMinute = minute;

        Calendar currentTime = Calendar.getInstance();

        Calendar minTime = Calendar.getInstance();
        minTime.set(Calendar.HOUR_OF_DAY, mMinHour);
        minTime.set(Calendar.MINUTE, mMinMinute);

        if (currentTime.before(minTime)) {
            // Reset current time is before min time, set it to min.
            this.setCurrentHour(mMinHour);
            this.setCurrentMinute(mMinMinute);
        }
    }

    @Override
    public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {
        Log.d(LOGTAG, "onTimeChanged: " + String.format("%02d:%02d", hourOfDay, minute));

        boolean validTime = true;
        if (hourOfDay < mMinHour || (hourOfDay == mMinHour && minute < mMinMinute)) {
            validTime = false;
        }

        if (hourOfDay > mMaxHour || (hourOfDay == mMaxHour && minute > mMaxMinute)) {
            validTime = false;
        }

        if (validTime) {
            mCurrentHour = hourOfDay;
            mCurrentMinute = minute;
        }

        setCurrentHour(mCurrentHour);
        setCurrentMinute(mCurrentMinute);
    }
}