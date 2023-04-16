package com.igalia.wolvic.ui.widgets.prompts;

import android.app.TimePickerDialog;
import android.content.Context;
import android.icu.util.Calendar;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Button;
import android.widget.TimePicker;

import androidx.annotation.NonNull;

import com.igalia.wolvic.R;
import com.igalia.wolvic.audio.AudioEngine;


public class TimePromptWidget extends PromptWidget implements TimePickerDialog.OnTimeSetListener {
    // TODO() FIX THE BACKEND CODE TO RECOGNISE WHEN TO CALL WHICH PROMPT.
    //TODO() Run this and setup logs to check where the file crashes, if it crashes.
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
        Log.d("HARI00","Start of Logs in TimePromptWidget");
        inflate(aContext, R.layout.prompt_time, this);

        final Calendar c = Calendar.getInstance();
        Log.d("HARI0", c.toString());
        //For the time
        final int[] currentHour = {c.get(Calendar.HOUR_OF_DAY)};
        final int[] currentMinute = {c.get(Calendar.MINUTE)};

        Log.d("HARI1", currentMinute.toString());
        Log.d("HARI2", currentHour.toString());

        TimePicker timePicker = (TimePicker) findViewById(R.id.time_picker);
        Log.d("HARI3", timePicker.toString());
        //Sets the time.
        TimePickerDialog timePickerDialog = new TimePickerDialog(getContext(),
                new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker timePicker, int hour, int minute) {
                        Log.d("HARI4", "Before hour");
                        Log.d("HARI44", String.valueOf(currentHour[0]));
                        currentHour[0] = hour;
                        Log.d("HARI5", String.valueOf(hour));
                        currentMinute[0] = minute;
                        Log.d("HARI55", String.valueOf(minute));
                    }
                }, currentHour[0], currentMinute[0], false);
        Log.d("HARI6", String.valueOf(timePickerDialog));
        timePickerDialog.show();
        Log.d("HARI66", "After timePickerDialog is shown.");

//        //The lambda code that was removed
//        (timePicker1, hour, minute) -> {
//            currentHour[0] = hour;
//            currentMinute[0] = minute;

        //The on set listener to the TimePicker Dialog that was created.
//        new TimePickerDialog.OnTimeSetListener() {
//            @Override
//            public void onTimeSet(TimePicker timePicker, int hour, int minute){
//                currentHour[0] = hour;
//                currentMinute[0] = minute;
//
//            }

        Log.d("HARI7","Initialisation of variables.");
        mAudio = AudioEngine.fromContext(aContext);
        mLayout = findViewById(R.id.layout);
        mTitle = findViewById(R.id.title);
        mCancelButton = findViewById(R.id.negativeButton);
        mOkButton = findViewById(R.id.positiveButton);

        Log.d("HARI8","mCancelButton");

        mCancelButton.setOnClickListener(v -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
                Log.d("HARI99","Inside Clicks");
            }
            if (mPromptDelegate != null && mPromptDelegate instanceof TimePromptDelegate) {
                Log.d("HARI999","Inside Clicks2");
                mPromptDelegate.dismiss();
            }
            hide(REMOVE_WIDGET);
        });

        Log.d("HARI9","mOkButton");

        mOkButton.setOnClickListener(v -> {
            if (mAudio != null) {
                Log.d("HARI99","inside Audio if");
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            if (mPromptDelegate != null && mPromptDelegate instanceof TimePromptDelegate) {
                Log.d("HARI10","mGetHours");
                int intHour = timePicker.getHour();
                int intMinute = timePicker.getMinute();

                Log.d("HARI11",String.valueOf(intHour)+" "+String.valueOf(intMinute));
                String hour = String.valueOf(intHour);
                String minute = String.valueOf(intMinute);
                String amPm = setAmPm(intHour, intMinute);

                Log.d("HARI12",amPm);

                String time = hour + ":" + minute + " " + amPm;
                Log.d("HARI13",time);
                ((TimePromptDelegate) mPromptDelegate).confirm(time);
                Log.d("HARI14","Was still able to reach here at least");
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
    public void onTimeSet(TimePicker timePicker, int i, int i1) {
        //Needed()??
        Log.d("HARI15","inside on time set");
    }

    public interface TimePromptDelegate extends PromptDelegate {
        void confirm(@NonNull final String time);
    }
}