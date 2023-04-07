package com.igalia.wolvic.ui.widgets.prompts;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.icu.util.Calendar;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.TimePicker;

import com.igalia.wolvic.R;
import com.igalia.wolvic.audio.AudioEngine;
import androidx.annotation.NonNull;



public class DateTimePromptWidget extends PromptWidget implements DatePickerDialog.OnDateSetListener, TimePickerDialog.OnTimeSetListener {

    private AudioEngine mAudio;
    private Button mCancelButton, mOkButton;

    public DateTimePromptWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public DateTimePromptWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public DateTimePromptWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    protected void initialize(Context aContext) {
        inflate(aContext, R.layout.prompt_date_time, this);

        final Calendar c = Calendar.getInstance();
        //For the time
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int minute = c.get(Calendar.MINUTE);

        //For the date
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);
        //TODO() how people have used Calendar API of google in their own codes.
        //TODO() Read the documentation of Calendar API.
        //TimePicker();
        //TimePickerDialog();
        // Create a new instance of TimePickerDialog and return it
//        return new TimePickerDialog(getContext(), this, hour, minute,
//                DateFormat.is24HourFormat(getContext()));
//        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
//            // Do something with the time chosen by the user
//        }
//        See the TimePickerDialog-- in the android documentations
//        https://developer.android.com/reference/android/app/TimePickerDialog
//        class for information about the constructor arguments.

        //For the DatePickerDialog --> https://developer.android.com/reference/android/app/DatePickerDialog
        // Create a new instance of DatePickerDialog and return it
//        return new DatePickerDialog(requireContext(), this, year, month, day);
//    }
//
//    public void onDateSet(DatePicker view, int year, int month, int day) {
//        // Do something with the date chosen by the user
//    }
        //**For AutoFILL Picker**
//        https://developer.android.com/develop/ui/views/components/pickers#PickerAutofill




                //TODO(X) --> Write up the Calendar code first.
        //TODO() --> Check the input type on the link by Felipe.
        //TODO() --> .ideas and the cleaning up of the annotations caches issue using this link --https://engineering.backmarket.com/put-your-android-studio-on-a-diet-fa4d364acb05

        mAudio = AudioEngine.fromContext(aContext);
        mLayout = findViewById(R.id.layout);
        mTitle = findViewById(R.id.title);
        mCancelButton = findViewById(R.id.cancelButton);
        mOkButton = findViewById(R.id.okButton);


        mCancelButton.setOnClickListener(v -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            if (mPromptDelegate != null && mPromptDelegate instanceof DateTimePromptDelegate) {
                mPromptDelegate.dismiss();
            }
            hide(REMOVE_WIDGET);
        });

        mOkButton.setOnClickListener(v -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            if (mPromptDelegate != null && mPromptDelegate instanceof DateTimePromptDelegate) {

            }
            hide(REMOVE_WIDGET);
        });
    }

    @Override
    public void onDateSet(DatePicker datePicker, int i, int i1, int i2) {

    }

    @Override
    public void onTimeSet(TimePicker timePicker, int i, int i1) {

    }

    public interface DateTimePromptDelegate extends PromptDelegate {
        void confirm(@NonNull final String color);
    }
}