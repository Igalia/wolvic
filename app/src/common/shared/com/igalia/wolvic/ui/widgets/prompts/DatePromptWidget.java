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



public class DatePromptWidget extends PromptWidget implements DatePickerDialog.OnDateSetListener {
//TODO() Make a workable structure of it like the TimePRomptWidget.
    //TODO() Then add logs to both DatePrompt and TimePrompt.
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
        inflate(aContext, R.layout.prompt_date, this);

//        datePickerDialogInitialise();
        mAudio = AudioEngine.fromContext(aContext);
        mLayout = findViewById(R.id.layout);
        mTitle = findViewById(R.id.title);
        mCancelButton = findViewById(R.id.negativeButton);
        mOkButton = findViewById(R.id.positiveButton);

        Dialog dateDialog = onCreateDialog();
        final Calendar currentDate = Calendar.getInstance();
        DatePicker datePicker = (DatePicker) findViewById(R.id.date_picker);

        //For the date
        int year = currentDate.get(Calendar.YEAR);
        int month = currentDate.get(Calendar.MONTH);
        int day = currentDate.get(Calendar.DAY_OF_MONTH);
        datePickerDialogInitialise();

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

//                onDateSet(datePicker,year,month,day);
//                ((DatePromptDelegate) mPromptDelegate).confirm()

            }
            hide(REMOVE_WIDGET);
        });
    }

    protected Dialog onCreateDialog(){
        final Calendar currentDate = Calendar.getInstance();
        DatePicker datePicker = (DatePicker) findViewById(R.id.date_picker);

        //For the date
        int year = currentDate.get(Calendar.YEAR);
        int month = currentDate.get(Calendar.MONTH);
        int day = currentDate.get(Calendar.DAY_OF_MONTH);
        return new DatePickerDialog(getContext(),this,year,month,day);
    }

    private void datePickerDialogInitialise() {
        final Calendar currentDate = Calendar.getInstance();
        DatePicker datePicker = (DatePicker) findViewById(R.id.date_picker);

        //For the date
        int year = currentDate.get(Calendar.YEAR);
        int month = currentDate.get(Calendar.MONTH);
        int day = currentDate.get(Calendar.DAY_OF_MONTH);

    }

    @Override
    public void onDateSet(DatePicker datePicker, int year, int month, int day) {
    }

    public interface DatePromptDelegate extends PromptDelegate {
        void confirm(@NonNull final String date);
    }
}