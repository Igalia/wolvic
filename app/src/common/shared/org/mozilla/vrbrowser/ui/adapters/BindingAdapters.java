package org.mozilla.vrbrowser.ui.adapters;

import android.graphics.Typeface;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.databinding.BindingAdapter;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;


public class BindingAdapters {

    @BindingAdapter("visibleGone")
    public static void showHide(@NonNull View view, boolean show) {
        view.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @BindingAdapter("visibleInvisible")
    public static void showInvisible(@NonNull View view, boolean show) {
        view.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
    }

    @BindingAdapter("typeface")
    public static void setTypeface(@NonNull TextView v, String style) {
        switch (style) {
            case "bold":
                v.setTypeface(null, Typeface.BOLD);
                break;
            default:
                v.setTypeface(null, Typeface.NORMAL);
                break;
        }
    }

    @BindingAdapter("bindDate")
    public static void bindDate(@NonNull TextView textView, long timestamp) {
        String androidDateTime = android.text.format.DateFormat.getDateFormat(textView.getContext()).format(new Date(timestamp)) + " " +
                android.text.format.DateFormat.getTimeFormat(textView.getContext()).format(new Date(timestamp));
        String AmPm = "";
        if(!Character.isDigit(androidDateTime.charAt(androidDateTime.length()-1))) {
            if(androidDateTime.contains(new SimpleDateFormat().getDateFormatSymbols().getAmPmStrings()[Calendar.AM])){
                AmPm = " " + new SimpleDateFormat().getDateFormatSymbols().getAmPmStrings()[Calendar.AM];
            }else{
                AmPm = " " + new SimpleDateFormat().getDateFormatSymbols().getAmPmStrings()[Calendar.PM];
            }
            androidDateTime=androidDateTime.replace(AmPm, "");
        }
        textView.setText(androidDateTime.concat(AmPm));
    }
}