package com.igalia.wolvic.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.LinearLayout;

/**
 * A LinearLayout that keeps every hover event for itself instead of handing them to its children.
 * Otherwise, moving the pointer from the layout onto one of its children makes the layout receive
 * a HOVER_EXIT (and a HOVER_ENTER when leaving the child), so an OnHoverListener that highlights
 * the whole layout flickers while the pointer moves over the children.
 */
public class HoverInterceptLinearLayout extends LinearLayout {
    public HoverInterceptLinearLayout(Context context) {
        super(context);
    }

    public HoverInterceptLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HoverInterceptLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onInterceptHoverEvent(MotionEvent event) {
        return true;
    }
}
