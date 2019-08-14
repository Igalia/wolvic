package org.mozilla.vrbrowser.utils;

import android.os.Build;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;

import org.jetbrains.annotations.NotNull;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;

public class ViewUtils {

    public interface LinkClickableSpan {
        void onClick(@NonNull View widget, @NonNull String url);
    }

    public static void makeLinkClickable(@NonNull SpannableStringBuilder strBuilder, @NonNull final URLSpan span, @NonNull LinkClickableSpan listener)
    {
        int start = strBuilder.getSpanStart(span);
        int end = strBuilder.getSpanEnd(span);
        int flags = strBuilder.getSpanFlags(span);
        ClickableSpan clickable = new ClickableSpan() {
            public void onClick(View view) {
                listener.onClick(view, span.getURL());
            }
        };
        strBuilder.setSpan(clickable, start, end, flags);
        strBuilder.removeSpan(span);
    }

    public static void setTextViewHTML(@NonNull TextView text, @NonNull String html, LinkClickableSpan listener)
    {
        CharSequence sequence = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY);
        SpannableStringBuilder strBuilder = new SpannableStringBuilder(sequence);
        URLSpan[] urls = strBuilder.getSpans(0, sequence.length(), URLSpan.class);
        for(URLSpan span : urls) {
            makeLinkClickable(strBuilder, span, listener);
        }
        text.setText(strBuilder);
        text.setMovementMethod(LinkMovementMethod.getInstance());
    }

    public enum TooltipPosition {
        TOP(0), BOTTOM(1);
        int id;

        TooltipPosition(int id) {
            this.id = id;
        }

        public static TooltipPosition fromId(int id) {
            for (TooltipPosition f : values()) {
                if (f.id == id) return f;
            }
            throw new IllegalArgumentException();
        }
    }

    public static UIWidget getParentWidget(@NonNull View view) {
        if (view == null) {
            return null;
        }

        ViewParent v = view.getParent();
        if (v instanceof UIWidget) {
            return (UIWidget)v;

        } else if (v instanceof View){
            return getParentWidget((View)v);

        } else {
            return null;
        }
    }

    public static Spanned getSpannedText(String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT);
        } else {
            return Html.fromHtml(text);
        }
    }

    public static boolean isChildrenOf(@NonNull View parent, @NonNull View view) {
        if (parent == null || view == null) {
            return false;
        }

        if (!(parent instanceof ViewGroup)) {
            return false;
        }

        return parent.findViewById(view.getId()) != null;
    }

    public static boolean isInsideView(@NotNull View view, int rx, int ry) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int x = location[0];
        int y = location[1];
        int w = view.getWidth();
        int h = view.getHeight();

        if (rx < x || rx > x + w || ry < y || ry > y + h) {
            return false;
        }
        return true;
    }

}
