package com.igalia.wolvic.ui.widgets.prompts;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.icu.util.Calendar;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Button;
import android.widget.DatePicker;

import com.igalia.wolvic.R;
import com.igalia.wolvic.audio.AudioEngine;

import androidx.annotation.NonNull;


public class DatePromptWidget extends PromptWidget implements DatePickerDialog.OnDateSetListener {
    //TODO() --> Now the question arises should we 1) Fix the backend code
    //TODO() --> Or just see the backend code once and
    private AudioEngine mAudio;
    private Button mCancelButton, mOkButton;

    public DatePromptWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public DatePromptWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public DatePromptWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    protected void initialize(Context aContext) {
        Log.d("HARI00", "Start of Logs in DatePromptWidget");
        inflate(aContext, R.layout.prompt_date, this);

//        datePickerDialogInitialise();
//        Dialog dateDialog = onCreateDialog();
        final Calendar currentDate = Calendar.getInstance();
        Log.d("HARI0", currentDate.toString());

        //For the date
        final int[] currentYear = {currentDate.get(Calendar.YEAR)};
        final int[] currentMonth = {currentDate.get(Calendar.MONTH)};
        final int[] currentDay = {currentDate.get(Calendar.DAY_OF_MONTH)};
        Log.d("HARI1", String.valueOf(currentDay[0]));
        Log.d("HARI2", String.valueOf(currentMonth[0]));
        Log.d("HARI3", String.valueOf(currentYear[0]));

        DatePicker datePicker = (DatePicker) findViewById(R.id.date_picker);
        Log.d("HARI3", datePicker.toString());
//        datePickerDialogInitialise();
        DatePickerDialog datePickerDialog = new DatePickerDialog(getContext(),
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker datePicker, int day, int month, int year) {
//TODO() --> Check up the date picker ka code.
                        Log.d("HARI4", "Before day" + String.valueOf(currentDay[0]));
                        currentDay[0] = day;
                        Log.d("HARI44", "After day" + String.valueOf(currentDay[0]));
                        Log.d("HARI5", "Before Month" + currentMonth[0]);
                        currentMonth[0] = month;
                        Log.d("HARI55", "After month" + String.valueOf(currentMonth[0]));
                        Log.d("HARI6", "Before Year" + String.valueOf(currentYear[0]));
                        currentYear[0] = year;
                        Log.d("HARI66", "After month" + String.valueOf(currentMonth[0]));
                    }
                }, currentYear[0], currentMonth[0], currentDay[0]);
        mAudio = AudioEngine.fromContext(aContext);
        mLayout = findViewById(R.id.layout);
        mTitle = findViewById(R.id.title);
        mCancelButton = findViewById(R.id.negativeButton);
        mOkButton = findViewById(R.id.positiveButton);

        Log.d("HARI8", "mCancelButton");

        mCancelButton.setOnClickListener(v -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
                Log.d("HARI99", "Inside Clicks");
            }
            if (mPromptDelegate != null && mPromptDelegate instanceof DatePromptDelegate) {
                Log.d("HARI999", "Inside Clicks2");
                mPromptDelegate.dismiss();
            }
            hide(REMOVE_WIDGET);
        });

        Log.d("HARI9", "mOkButton");

        mOkButton.setOnClickListener(v -> {
            if (mAudio != null) {
                Log.d("HARI99", "inside Audio if");
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            if (mPromptDelegate != null && mPromptDelegate instanceof DatePromptDelegate) {
                Log.d("HARI10", "mGetHours");
                int intYear = datePicker.getDayOfMonth();
                int intMonth = datePicker.getMonth();
                int intDay = datePicker.getYear();

                Log.d("HARI11", String.valueOf(intYear) + "/" + String.valueOf(intMonth) +
                        "/" + String.valueOf(intDay));
                String date = String.valueOf(intDay) + "/" + String.valueOf(intMonth) + "/" + String.valueOf(intYear);
            }
            hide(REMOVE_WIDGET);
        });
    }

//    protected Dialog onCreateDialog() {
//        final Calendar currentDate = Calendar.getInstance();
//        DatePicker datePicker = (DatePicker) findViewById(R.id.date_picker);
//
//        //For the date
//        int year = currentDate.get(Calendar.YEAR);
//        int month = currentDate.get(Calendar.MONTH);
//        int day = currentDate.get(Calendar.DAY_OF_MONTH);
//        return new DatePickerDialog(getContext(), this, year, month, day);
//    }

//    private void datePickerDialogInitialise() {
//        final Calendar currentDate = Calendar.getInstance();
//        DatePicker datePicker = (DatePicker) findViewById(R.id.date_picker);
//
//        //For the date
//        int year = currentDate.get(Calendar.YEAR);
//        int month = currentDate.get(Calendar.MONTH);
//        int day = currentDate.get(Calendar.DAY_OF_MONTH);
//
//    }

    @Override
    public void onDateSet(DatePicker datePicker, int year, int month, int day) {
    }

    public interface DatePromptDelegate extends PromptDelegate {
        void confirm(@NonNull final String date);
    }
}