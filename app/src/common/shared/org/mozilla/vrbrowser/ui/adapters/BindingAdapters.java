package org.mozilla.vrbrowser.ui.adapters;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.BindingAdapter;
import androidx.databinding.ObservableList;
import androidx.recyclerview.widget.RecyclerView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.downloads.Download;
import org.mozilla.vrbrowser.ui.views.HoneycombButton;
import org.mozilla.vrbrowser.ui.views.UIButton;
import org.mozilla.vrbrowser.ui.views.UITextButton;
import org.mozilla.vrbrowser.ui.views.settings.ButtonSetting;
import org.mozilla.vrbrowser.ui.views.settings.SwitchSetting;

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
    public static void setTypeface(@NonNull TextView v, @NonNull String style) {
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
    public static void setSpannableString(@NonNull TextView textView, @NonNull Drawable drawable, String text) {
        SpannableString spannableString = new SpannableString(text);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        ImageSpan span = new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM);
        spannableString.setSpan(span, spannableString.toString().indexOf("@"),  spannableString.toString().indexOf("@")+1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        textView.setText(spannableString);
    }

    @BindingAdapter("honeycombButtonText")
    public static void setHoneycombButtonText(@NonNull HoneycombButton button, int resource){
        button.setText(resource);
    }

    @BindingAdapter("layout_height")
    public static void setLayoutHeight(@NonNull View view, @NonNull @Dimension float dimen) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = (int)dimen;
        view.setLayoutParams(params);
    }

    @BindingAdapter("leftMargin")
    public static void setLeftMargin(@NonNull View view, @NonNull @Dimension float margin) {
        if (view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            if (params.getMarginStart() != Math.round(margin)) {
                params.setMarginStart(Math.round(margin));
                view.setLayoutParams(params);
            }
        }
    }

    @BindingAdapter("lastSync")
    public static void setFxALastSync(@NonNull TextView view, long lastSync) {
        if (lastSync == 0) {
            view.setText(view.getContext().getString(R.string.fxa_account_last_no_synced));

        } else {
            long timeDiff = System.currentTimeMillis() - lastSync;
            if (timeDiff < 60000) {
                view.setText(view.getContext().getString(R.string.fxa_account_last_synced_now));

            } else {
                view.setText(view.getContext().getString(R.string.fxa_account_last_synced, timeDiff / 60000));
            }
        }
    }

    @BindingAdapter("android:layout_width")
    public static void setLayoutWidth(@NonNull UIButton button, @NonNull @Dimension float dimen) {
        button.setLayoutWidth(dimen);
    }

    @BindingAdapter("android:enabled")
    public static void setEnabled(@NonNull SwitchSetting button, boolean enabled) {
        button.setEnabled(enabled);
    }

    @BindingAdapter("lastSync")
    public static void setFxALastSync(@NonNull ButtonSetting view, long lastSync) {
        if (lastSync == 0) {
            view.setDescription(view.getContext().getString(R.string.fxa_account_last_no_synced));

        } else {
            long timeDiff = System.currentTimeMillis() - lastSync;
            if (timeDiff < 60000) {
                view.setDescription(view.getContext().getString(R.string.fxa_account_last_synced_now));

            } else {
                view.setDescription(view.getContext().getString(R.string.fxa_account_last_synced, timeDiff / 60000));
            }
        }
    }

    @BindingAdapter("android:layout_height")
    public static void setLayoutHeight(@NonNull ImageView view, @NonNull @Dimension float dimen) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = (int)dimen;
        view.setLayoutParams(params);
    }

    @BindingAdapter("android:layout_width")
    public static void setLayoutWidth(@NonNull ImageView view, @NonNull @Dimension float dimen) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = (int)dimen;
        view.setLayoutParams(params);
    }

    @BindingAdapter("privateMode")
    public static void setPrivateMode(@NonNull UIButton button, boolean isPrivateMode) {
        button.setPrivateMode(isPrivateMode);
    }

    @BindingAdapter("privateMode")
    public static void setPrivateMode(@NonNull UITextButton button, boolean isPrivateMode) {
        button.setPrivateMode(isPrivateMode);
    }

    @BindingAdapter("activeMode")
    public static void setActiveMode(@NonNull UIButton button, boolean isActiveMode) {
        button.setActiveMode(isActiveMode);
    }

    @BindingAdapter("notificationMode")
    public static void setNotificationMode(@NonNull UIButton button, boolean isNotificationMode) {
        button.setNotificationMode(isNotificationMode);
    }

    @BindingAdapter("regularModeBackground")
    public static void setRegularModeBackground(@NonNull UIButton button, @NonNull Drawable drawable) {
        button.setRegularModeBackground(drawable);
    }

    @BindingAdapter("privateModeBackground")
    public static void setPrivateModeBackground(@NonNull UIButton button, @NonNull Drawable drawable) {
        button.setPrivateModeBackground(drawable);
    }

    @BindingAdapter("activeModeBackground")
    public static void setActiveModeBackground(@NonNull UIButton button, @NonNull Drawable drawable) {
        button.setActiveModeBackground(drawable);
    }

    @BindingAdapter("android:tooltipText")
    public static void setTooltipText(@NonNull UIButton button, @Nullable String text){
        button.setTooltipText(text);
    }

    @BindingAdapter("items")
    public static void bindAdapterWithDefaultBinder(@NonNull RecyclerView recyclerView, @Nullable ObservableList<Download> items) {
        DownloadsAdapter adapter = (DownloadsAdapter)recyclerView.getAdapter();
        if (adapter != null) {
            adapter.setDownloadsList(items);
        }
    }

 }