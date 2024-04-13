package com.igalia.wolvic.ui.widgets.prompts;

import android.content.Context;
import android.icu.util.Calendar;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;

import androidx.annotation.NonNull;

import com.igalia.wolvic.R;
import com.igalia.wolvic.audio.AudioEngine;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.ui.widgets.dialogs.RangeTimePickerWidget;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class DateTimePromptWidget extends PromptWidget {
    private AudioEngine mAudio;
    private Button mCancelButton, mClearButton, mOkButton;
    private WSession.PromptDelegate.DateTimePrompt mPrompt;
    private String format;

    enum DefaultToNow {
        YES,
        NO
    }
    private static Date parseDate(final SimpleDateFormat formatter, final String value, final DefaultToNow defaultToNow) {
        try {
            if (value != null && !value.isEmpty()) {
                return formatter.parse(value);
            }
        } catch (final ParseException e) {
        }
        return defaultToNow == DefaultToNow.YES ? new Date() : null;
    }

    public DateTimePromptWidget(Context aContext, WSession.PromptDelegate.DateTimePrompt prompt) {
        super(aContext);
        mPrompt = prompt;
        initialize(aContext);
    }

    protected void initialize(Context aContext) {
        switch (mPrompt.type()) {
            case WSession.PromptDelegate.DateTimePrompt.Type.DATE:
                format = "yyyy-MM-dd";
                break;
            case WSession.PromptDelegate.DateTimePrompt.Type.MONTH:
                format = "yyyy-MM";
                break;
            case WSession.PromptDelegate.DateTimePrompt.Type.WEEK:
                format = "yyyy-'W'ww";
                break;
            case WSession.PromptDelegate.DateTimePrompt.Type.TIME:
                format = "HH:mm";
                break;
            case WSession.PromptDelegate.DateTimePrompt.Type.DATETIME_LOCAL:
                format = "yyyy-MM-dd'T'HH:mm";
                break;
            default:
                throw new UnsupportedOperationException();
        }

        inflate(aContext, R.layout.prompt_datetime, this);

        final DatePicker datePicker;
        final RangeTimePickerWidget timePicker;

        mAudio = AudioEngine.fromContext(aContext);
        mLayout = findViewById(R.id.layout);
        mTitle = findViewById(R.id.title);
        mCancelButton = findViewById(R.id.negativeButton);
        mClearButton = findViewById(R.id.neutralButton);
        mOkButton = findViewById(R.id.positiveButton);

        final SimpleDateFormat formatter = new SimpleDateFormat(format, Locale.ROOT);
        final Date minDate = parseDate(formatter, mPrompt.minValue(), DefaultToNow.NO);
        final Date maxDate = parseDate(formatter, mPrompt.maxValue(), DefaultToNow.NO);
        final Date date = parseDate(formatter, mPrompt.defaultValue(), DefaultToNow.YES);
        final java.util.Calendar cal = formatter.getCalendar();
        cal.setTime(date);

        if (mPrompt.type() == WSession.PromptDelegate.DateTimePrompt.Type.DATE
                || mPrompt.type() == WSession.PromptDelegate.DateTimePrompt.Type.MONTH
                || mPrompt.type() == WSession.PromptDelegate.DateTimePrompt.Type.WEEK
                || mPrompt.type() == WSession.PromptDelegate.DateTimePrompt.Type.DATETIME_LOCAL) {
            datePicker = findViewById(R.id.date_picker);
            datePicker.init(
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH), /* listener */
                    null);
            if (minDate != null) {
                datePicker.setMinDate(minDate.getTime());
            }
            if (maxDate != null) {
                datePicker.setMaxDate(maxDate.getTime());
            }
        } else {
            findViewById(R.id.date_picker).setVisibility(View.GONE);
            datePicker = null;
        }

        if (mPrompt.type() == WSession.PromptDelegate.DateTimePrompt.Type.TIME
                || mPrompt.type() == WSession.PromptDelegate.DateTimePrompt.Type.DATETIME_LOCAL) {
            timePicker = findViewById(R.id.time_picker);
            timePicker.setHour(cal.get(java.util.Calendar.HOUR_OF_DAY));
            timePicker.setMinute(cal.get(java.util.Calendar.MINUTE));
            timePicker.setIs24HourView(DateFormat.is24HourFormat(aContext));
            if (minDate != null) {
                timePicker.setMinTime(minDate.getHours(), minDate.getMinutes());
            }
            if (maxDate != null) {
                timePicker.setMaxTime(maxDate.getHours(), maxDate.getMinutes());
            }
        } else {
            findViewById(R.id.time_picker).setVisibility(View.GONE);
            timePicker = null;
        }

        mCancelButton.setOnClickListener(v -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            if (mPromptDelegate != null && mPromptDelegate instanceof DateTimePromptDelegate) {
                mPromptDelegate.dismiss();
            }
            hide(REMOVE_WIDGET);
        });

        mClearButton.setOnClickListener(v -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            if (mPromptDelegate != null && mPromptDelegate instanceof DateTimePromptDelegate) {
                ((DateTimePromptDelegate) mPromptDelegate).confirm("");
            }
            hide(REMOVE_WIDGET);
        });

        mOkButton.setOnClickListener(v -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            if (mPromptDelegate != null && mPromptDelegate instanceof DateTimePromptDelegate) {
                if (datePicker != null) {
                    cal.set(datePicker.getYear(), datePicker.getMonth(), datePicker.getDayOfMonth());
                }
                if (timePicker != null) {
                    cal.set(Calendar.HOUR_OF_DAY, timePicker.getHour());
                    cal.set(Calendar.MINUTE, timePicker.getMinute());
                }
                ((DateTimePromptDelegate) mPromptDelegate).confirm(formatter.format(cal.getTime()));
            }
            hide(REMOVE_WIDGET);
        });

        if (datePicker != null && timePicker != null) {
            mWidgetPlacement.width = WidgetPlacement.dpDimension(getContext(), R.dimen.prompt_datetime_width);
            mWidgetManager.updateWidget(this);
        }
    }

    public interface DateTimePromptDelegate extends PromptDelegate {
        void confirm(@NonNull final String date);
    }
}
