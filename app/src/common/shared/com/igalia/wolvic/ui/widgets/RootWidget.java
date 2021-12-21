package com.igalia.wolvic.ui.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

public class RootWidget extends UIWidget {
    private Runnable mOnClickCallback;
    private boolean mTouched = true;

    public RootWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public RootWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public RootWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.width = 8;
        aPlacement.height = 8;
        aPlacement.cylinder = false;
    }

    private void initialize(Context aContext) {
        setFocusable(true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mTouched = true;
                break;
            case MotionEvent.ACTION_UP:
                if (mTouched) {
                    mTouched = false;
                    requestFocus();
                    requestFocusFromTouch();
                    if (mOnClickCallback != null) {
                        mOnClickCallback.run();
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                mTouched = false;
                break;
        }
        return super.onTouchEvent(event);
    }

    public void setClickCallback(Runnable aRunnable) {
        mOnClickCallback = aRunnable;
    }
}
