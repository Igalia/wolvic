package org.mozilla.vrbrowser.ui.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

public class RootWidget extends UIWidget {
    private Runnable mOnClickCallback;

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

        setOnClickListener(v -> {
            requestFocus();
            requestFocusFromTouch();
            if (mOnClickCallback != null) {
                mOnClickCallback.run();
            }
        });
    }

    public void setClickCallback(Runnable aRunnable) {
        mOnClickCallback = aRunnable;
    }
}
