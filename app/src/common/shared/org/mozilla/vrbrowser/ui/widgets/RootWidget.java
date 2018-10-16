package org.mozilla.vrbrowser.ui.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

public class RootWidget extends UIWidget {

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

    }

    private void initialize(Context aContext) {
        setFocusable(true);
        setSoundEffectsEnabled(false);

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                requestFocus();
                requestFocusFromTouch();
            }
        });
    }
}
