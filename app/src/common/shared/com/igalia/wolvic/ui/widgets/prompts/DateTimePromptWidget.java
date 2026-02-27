package com.igalia.wolvic.ui.widgets.prompts;

import android.content.Context;
import android.icu.util.Calendar;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.Spinner;
import android.widget.TimePicker;

import com.igalia.wolvic.R;
import com.igalia.wolvic.audio.AudioEngine;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class DateTimePromptWidget extends PromptWidget {

    private AudioEngine mAudio;
    private Button mCancelButton, mClearButton, mOkButton;
    private WSession.PromptDelegate.DateTimePrompt mPrompt;
    private String format;

    enum DefaultToNow {
        YES,
        NO
    }

    private static Date parseDate(final SimpleDateFormat formatter, final String value,
                                  final DefaultToNow defaultToNow) {
        try {
            if (value != null && !value.isEmpty()) {
                return formatter.parse(value);
            }
        } catch (final ParseException e) {
            // fall through
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
        final TimePicker timePicker;

        mAudio = AudioEngine.fromContext(aContext);
        mLayout = findViewById(R.id.layout);
        mTitle = findViewById(R.id.title);
        mCancelButton = findViewById(R.id.negativeButton);
        mClearButton = findViewById(R.id.neutralButton);
        mOkButton = findViewById(R.id.positiveButton);

        final SimpleDateFormat formatter = new SimpleDateFormat(format, Locale.ROOT);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        final Date minDate = parseDate(formatter, mPrompt.minValue(), DefaultToNow.NO);
        final Date maxDate = parseDate(formatter, mPrompt.maxValue(), DefaultToNow.NO);
        final Date date = parseDate(formatter, mPrompt.defaultValue(), DefaultToNow.YES);
        final java.util.Calendar cal = formatter.getCalendar();
        cal.setTime(date);

        // Parse step attribute (days for date types, seconds for time)
        final double stepValue = parseStep(mPrompt.stepValue());

        final String[] listValues = mPrompt.listValues();  // datalist suggestions
        final List<Date> suggestions = buildSuggestions(formatter, listValues);

        if (mPrompt.type() == WSession.PromptDelegate.DateTimePrompt.Type.DATE
                || mPrompt.type() == WSession.PromptDelegate.DateTimePrompt.Type.MONTH
                || mPrompt.type() == WSession.PromptDelegate.DateTimePrompt.Type.WEEK
                || mPrompt.type() == WSession.PromptDelegate.DateTimePrompt.Type.DATETIME_LOCAL) {
            datePicker = findViewById(R.id.date_picker);
            datePicker.init(
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH),
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
        } else {
            findViewById(R.id.time_picker).setVisibility(View.GONE);
            timePicker = null;
        }

        // Populate suggestions spinner if we have list values
        final Spinner suggestionsSpinner = findViewById(R.id.suggestions_spinner);
        if (!suggestions.isEmpty()) {
            suggestionsSpinner.setVisibility(View.VISIBLE);
            final List<String> labels = new ArrayList<>();
            for (Date s : suggestions) {
                labels.add(formatter.format(s));
            }
            final ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    aContext, android.R.layout.simple_spinner_item, labels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            suggestionsSpinner.setAdapter(adapter);

            // Pre-select the entry that matches the current default value, if any.
            final String defaultFormatted = formatter.format(date);
            int preselect = labels.indexOf(defaultFormatted);
            if (preselect >= 0) {
                suggestionsSpinner.setSelection(preselect);
            }

            // When the user picks a suggestion, update the pickers to reflect it.
            suggestionsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    final Date picked = suggestions.get(position);
                    final java.util.Calendar sc = formatter.getCalendar();
                    sc.setTime(picked);
                    if (datePicker != null) {
                        datePicker.updateDate(
                                sc.get(java.util.Calendar.YEAR),
                                sc.get(java.util.Calendar.MONTH),
                                sc.get(java.util.Calendar.DAY_OF_MONTH));
                    }
                    if (timePicker != null) {
                        timePicker.setHour(sc.get(java.util.Calendar.HOUR_OF_DAY));
                        timePicker.setMinute(sc.get(java.util.Calendar.MINUTE));
                    }
                    cal.setTime(picked);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        } else {
            suggestionsSpinner.setVisibility(View.GONE);
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

                // Round to nearest step boundary if step is set
                final String confirmed;
                if (!Double.isNaN(stepValue) && stepValue > 0) {
                    confirmed = formatter.format(snapToStep(cal.getTime(), stepValue,
                            minDate, mPrompt.type()));
                } else {
                    confirmed = formatter.format(cal.getTime());
                }
                ((DateTimePromptDelegate) mPromptDelegate).confirm(confirmed);
            }
            hide(REMOVE_WIDGET);
        });

        if (datePicker != null && timePicker != null) {
            mWidgetPlacement.width = WidgetPlacement.dpDimension(getContext(), R.dimen.prompt_datetime_width);
            mWidgetManager.updateWidget(this);
        }
    }

    // Returns NaN if step is null, "any", or invalid.
    private static double parseStep(@Nullable String stepStr) {
        if (stepStr == null || stepStr.isEmpty() || "any".equalsIgnoreCase(stepStr)) {
            return Double.NaN;
        }
        try { return Double.parseDouble(stepStr); } catch (NumberFormatException e) { return Double.NaN; }
    }

    // Parse suggestion strings to Dates, skipping entries that fail.
    private static List<Date> buildSuggestions(SimpleDateFormat formatter,
                                               @Nullable String[] listValues) {
        final List<Date> result = new ArrayList<>();
        if (listValues == null) return result;
        for (String v : listValues) {
            final Date d = parseDate(formatter, v, DefaultToNow.NO);
            if (d != null) {
                result.add(d);
            }
        }
        return result;
    }

    // Snap to nearest valid step value (base + n*step). Step is in the
    // HTML attribute unit: days for date, weeks for week, seconds for time.
    // Month is skipped - non-uniform month lengths make ms arithmetic unreliable.
    private static Date snapToStep(Date selected, double stepValue,
                                   @Nullable Date minDate, int type) {
        final long stepMs;
        switch (type) {
            case WSession.PromptDelegate.DateTimePrompt.Type.MONTH:
                return selected;
            case WSession.PromptDelegate.DateTimePrompt.Type.WEEK:
                stepMs = Math.round(stepValue * 7L * 24L * 60L * 60L * 1000L);
                break;
            case WSession.PromptDelegate.DateTimePrompt.Type.TIME:
            case WSession.PromptDelegate.DateTimePrompt.Type.DATETIME_LOCAL:
                stepMs = Math.round(stepValue * 1000L);
                break;
            default:
                stepMs = Math.round(stepValue * 24L * 60L * 60L * 1000L);
                break;
        }
        if (stepMs <= 0) return selected;

        final long base = minDate != null ? minDate.getTime() : 0L;
        final long offset = selected.getTime() - base;
        final long snapped = base + Math.round((double) offset / stepMs) * stepMs;
        return new Date(snapped);
    }

    public interface DateTimePromptDelegate extends PromptDelegate {
        void confirm(@NonNull final String date);
    }
}
