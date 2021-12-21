package com.igalia.wolvic.ui.widgets.prompts;

import android.content.Context;
import android.text.method.ScrollingMovementMethod;
import android.util.AttributeSet;
import android.widget.Button;

import com.igalia.wolvic.R;
import com.igalia.wolvic.audio.AudioEngine;

public class AlertPromptWidget extends PromptWidget {

    private AudioEngine mAudio;
    private Button mOkButton;

    public AlertPromptWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public AlertPromptWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public AlertPromptWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    protected void initialize(Context aContext) {
        inflate(aContext, R.layout.prompt_alert, this);

        mAudio = AudioEngine.fromContext(aContext);

        mLayout = findViewById(R.id.layout);

        mTitle = findViewById(R.id.alertTitle);
        mMessage = findViewById(R.id.alertMessage);
        mMessage.setMovementMethod(new ScrollingMovementMethod());

        mOkButton = findViewById(R.id.positiveButton);
        mOkButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            onDismiss();
        });
    }

}
