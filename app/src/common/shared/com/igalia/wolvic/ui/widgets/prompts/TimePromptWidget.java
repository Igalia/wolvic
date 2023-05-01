package com.igalia.wolvic.ui.widgets.prompts;

import android.app.TimePickerDialog;
import android.content.Context;
import android.icu.util.Calendar;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.TimePicker;

import androidx.annotation.NonNull;

import com.igalia.wolvic.R;
import com.igalia.wolvic.audio.AudioEngine;

import java.text.SimpleDateFormat;
import java.util.Date;


public class TimePromptWidget extends PromptWidget implements TimePickerDialog.OnTimeSetListener {
    private AudioEngine mAudio;
    private Button mCancelButton, mOkButton;

    public TimePromptWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public TimePromptWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public TimePromptWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    protected void initialize(Context aContext) {
        inflate(aContext, R.layout.prompt_time, this);

        final Calendar c = Calendar.getInstance();

        final int[] currentHour = {c.get(Calendar.HOUR_OF_DAY)};
        final int[] currentMinute = {c.get(Calendar.MINUTE)};

        TimePicker timePicker = (TimePicker) findViewById(R.id.time_picker);
        TimePickerDialog timePickerDialog = new TimePickerDialog(getContext(),
                new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker timePicker, int hour, int minute) {
                        currentHour[0] = hour;
                        currentMinute[0] = minute;
                    }
                }, currentHour[0], currentMinute[0], false);
        timePickerDialog.show();

        mAudio = AudioEngine.fromContext(aContext);
        mLayout = findViewById(R.id.layout);
        mTitle = findViewById(R.id.title);
        mCancelButton = findViewById(R.id.negativeButton);
        mOkButton = findViewById(R.id.positiveButton);

        mCancelButton.setOnClickListener(v -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            if (mPromptDelegate != null && mPromptDelegate instanceof TimePromptDelegate) {
                mPromptDelegate.dismiss();
            }
            hide(REMOVE_WIDGET);
        });

        mOkButton.setOnClickListener(v -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            if (mPromptDelegate != null && mPromptDelegate instanceof TimePromptDelegate) {
                int intHour = timePicker.getHour();
                int intMinute = timePicker.getMinute();
                onTimeSet(timePicker, intHour,intMinute);
            }
            hide(REMOVE_WIDGET);
        });
    }

    private String setAmPm(int intHour, int intMinute) {
        String amPm = "";
        if (intHour == 0) {
            intHour += 12;
            amPm = "AM";
        } else if (intHour == 12) {
            intHour -= 12;
            amPm = "PM";
        } else if (intHour > 12) {
            intHour -= 12;
            amPm = "PM";
        } else {
            amPm = "AM";
        }
        return amPm;
    }

    @Override
    public void onTimeSet(TimePicker timePicker, int intHour, int intMinute) {
        String hour = String.valueOf(intHour);
        String minute = String.valueOf(intMinute);
        String amPm = setAmPm(intHour, intMinute);
        String pattern12Hour = "hh:mm aa";
        String time = hour + ":" + minute;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern12Hour);
        String simpleTime = simpleDateFormat.format(new Date());
        ((TimePromptDelegate) mPromptDelegate).confirm(String.valueOf(simpleTime));
    }

    public interface TimePromptDelegate extends PromptDelegate {
        void confirm(@NonNull final String time);
    }
}