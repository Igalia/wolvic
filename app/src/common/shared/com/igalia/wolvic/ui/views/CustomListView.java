package com.igalia.wolvic.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;

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

        boolean fits = getCount() == 0;

        // Check if all the items fit on the ListView
        int last = getLastVisiblePosition();
        int first = getFirstVisiblePosition();
        if (!fits && first == 0 && last == getCount() - 1) {
            fits = getChildAt(first).getTop() >= 0 && getChildAt(last).getBottom() <= getHeight();
        }

        // Hide scrollbar is all item fit.
        setVerticalScrollBarEnabled(!fits);
        setFastScrollAlwaysVisible(!fits);
    }


}
