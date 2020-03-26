package org.mozilla.vrbrowser.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.vrbrowser.utils.ViewUtils;

public class LinkTextView extends TextView {
    public LinkTextView(Context context) {
        super(context);
    }

    public LinkTextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public LinkTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LinkTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setLinkClickListener(@NonNull ViewUtils.LinkClickableSpan listener) {
        ViewUtils.setTextViewHTML(this, getText().toString(), listener::onClick);
    }
}
