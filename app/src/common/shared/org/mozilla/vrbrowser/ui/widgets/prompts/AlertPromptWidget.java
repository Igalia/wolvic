package org.mozilla.vrbrowser.ui.widgets.prompts;

import android.content.Context;
import android.text.method.ScrollingMovementMethod;
import android.util.AttributeSet;
import android.widget.Button;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;

public class AlertPromptWidget extends PromptWidget {

    private AudioEngine mAudio;
    private Button mOkButton;
    private GeckoSession.PromptDelegate.AlertCallback mCallback;

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

    @Override
    protected void initialize(Context aContext) {
        super.initialize(aContext);

        inflate(aContext, R.layout.prompt_alert, this);

        mAudio = AudioEngine.fromContext(aContext);

        mLayout = findViewById(R.id.layout);

        mTitle = findViewById(R.id.alertTitle);
        mMessage = findViewById(R.id.alertMessage);
        mMessage.setMovementMethod(new ScrollingMovementMethod());

        mOkButton = findViewById(R.id.positiveButton);
        mOkButton.setSoundEffectsEnabled(false);
        mOkButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            onDismiss();
        });
    }

    @Override
    protected void onDismiss() {
        hide(REMOVE_WIDGET);

        if (mCallback != null) {
            mCallback.dismiss();
        }

        if (mDelegate != null) {
            mDelegate.onDismiss();
        }
    }

    public void setDelegate(GeckoSession.PromptDelegate.AlertCallback delegate) {
        mCallback = delegate;
    }

}
