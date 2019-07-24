package org.mozilla.vrbrowser.utils;

import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.view.ViewParent;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;

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

    public static void setTextViewHTML(@NonNull TextView text, @NonNull String html, @NonNull LinkClickableSpan listener)
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
        if (view == null)
            return null;

        ViewParent v = view.getParent();
        if (v instanceof UIWidget) {
            return (UIWidget)v;

        } else if (v instanceof View){
            return getParentWidget((View)v);

        } else {
            return null;
        }
    }

}
