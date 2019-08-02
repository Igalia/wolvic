package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Button;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.ui.widgets.prompts.PromptWidget;

public class MaxWindowsWidget extends PromptWidget {

    private Button mButton;

    public MaxWindowsWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public MaxWindowsWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public MaxWindowsWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    protected void initialize(Context aContext) {
        inflate(aContext, R.layout.prompt_max_windows, this);

        mLayout = findViewById(R.id.layout);

        mMessage = findViewById(R.id.alertMessage);

        mButton = findViewById(R.id.exitButton);
        mButton.setOnClickListener(view -> onDismiss());
    }

}
