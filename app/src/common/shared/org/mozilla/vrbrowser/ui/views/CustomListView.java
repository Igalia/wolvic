package org.mozilla.vrbrowser.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;

import org.mozilla.vrbrowser.R;

public class CustomListView extends ListView {
    public CustomListView(Context context) {
        super(context);
        initialize();
    }

    public CustomListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public CustomListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    public CustomListView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initialize();
    }

    void initialize() {
        setVerticalScrollBarEnabled(false);
        setFastScrollAlwaysVisible(false);
    }

    @Override
    public boolean isInTouchMode() {
        // Fixes unable to select items after scrolling. See https://github.com/MozillaReality/FirefoxReality/issues/953.
        return true;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        boolean scrollVisible = false;

        if (getChildCount() > 0) {
            View first = getChildAt(0);
            View last = getChildAt(getChildCount() - 1);
            if (first.getTop() < 0 || last.getBottom() > getHeight()) {
                scrollVisible = true;
            }
        }

        setVerticalScrollBarEnabled(scrollVisible);
        setFastScrollAlwaysVisible(scrollVisible);
    }


}
