package org.mozilla.vrbrowser.ui.adapters;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Dimension;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.databinding.BindingAdapter;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;


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

    @BindingAdapter(value={"textDrawable", "textString"})
    public static void setSpannableString(@NonNull TextView textView, Drawable drawable, String text) {
        SpannableString spannableString = new SpannableString(text);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        ImageSpan span = new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM);
        spannableString.setSpan(span, spannableString.toString().indexOf("@"),  spannableString.toString().indexOf("@")+1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        textView.setText(spannableString);
    }

    @BindingAdapter("layout_height")
    public static void setLayoutHeight(@NonNull View view, @NonNull @Dimension float dimen) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = (int)dimen;
        view.setLayoutParams(params);
    }
}