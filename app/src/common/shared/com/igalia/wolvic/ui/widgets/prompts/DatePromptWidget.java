package com.igalia.wolvic.ui.widgets.prompts;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.icu.util.Calendar;
import android.util.AttributeSet;
import android.widget.Button;

import android.widget.DatePicker;

import com.igalia.wolvic.R;
import com.igalia.wolvic.audio.AudioEngine;

import androidx.annotation.NonNull;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;


public class DatePromptWidget extends PromptWidget implements DatePickerDialog.OnDateSetListener {

    private AudioEngine mAudio;
    private Button mCancelButton, mOkButton;
    private int[] currentYear,currentMonth, currentDate;
    private int mDateType;

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
        inflate(aContext, R.layout.prompt_date, this);
        onCreateDialog();
        DatePicker datePicker = (DatePicker) findViewById(R.id.date_picker);

        mAudio = AudioEngine.fromContext(aContext);
        mLayout = findViewById(R.id.layout);
        mTitle = findViewById(R.id.title);
        mCancelButton = findViewById(R.id.negativeButton);
        mOkButton = findViewById(R.id.positiveButton);

        mCancelButton.setOnClickListener(v -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            if (mPromptDelegate != null && mPromptDelegate instanceof DatePromptDelegate) {
                mPromptDelegate.dismiss();
            }
            hide(REMOVE_WIDGET);
        });

        mOkButton.setOnClickListener(v -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            if (mPromptDelegate != null && mPromptDelegate instanceof DatePromptDelegate) {
                int intYear = datePicker.getDayOfMonth();
                int intMonth = datePicker.getMonth();
                int intDay = datePicker.getYear();

                onDateSet(datePicker,intYear,intMonth,intDay);
            }
            hide(REMOVE_WIDGET);
        });
    }


    protected Dialog onCreateDialog() {
        final Calendar currentDate = Calendar.getInstance();
        DatePicker datePicker = (DatePicker) findViewById(R.id.date_picker);

        final int[] currentYear = {currentDate.get(Calendar.YEAR)};
        final int[] currentMonth = {currentDate.get(Calendar.MONTH)};
        final int[] currentDay = {currentDate.get(Calendar.DAY_OF_MONTH)};

        return new DatePickerDialog(getContext(),
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker datePicker, int day, int month, int year) {
                        currentDay[0] = day;
                        currentMonth[0] = month;
                        currentYear[0] = year;
                    }
                }, currentYear[0], currentMonth[0], currentDay[0]);
    }

    @Override
    public void onDateSet(DatePicker datePicker, int intYear, int intMonth, int intDay) {
        String date = String.valueOf(intMonth) + "-" + String.valueOf(intDay) + "-" + String.valueOf(intYear);
        String mmDDYYYYpattern = "MM-dd-yyyy";
        String datePatternWithTimeZone = "dd MMMM yyyy ZZZZ";
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(mmDDYYYYpattern);
        String simpleDate = simpleDateFormat.format(new Date());
        ((DatePromptDelegate) mPromptDelegate).confirm(String.valueOf(simpleDate));
    }

    public interface DatePromptDelegate extends PromptDelegate {
        void confirm(@NonNull final String date);
    }
}