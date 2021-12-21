package com.igalia.wolvic.ui.widgets.prompts;

import android.content.Context;
import android.text.method.ScrollingMovementMethod;
import android.util.AttributeSet;
import android.widget.Button;

import com.igalia.wolvic.R;
import com.igalia.wolvic.audio.AudioEngine;
import com.igalia.wolvic.ui.views.settings.SettingsEditText;

public class TextPromptWidget extends PromptWidget {

    public interface TextPromptDelegate extends PromptDelegate {
        void confirm(String message);
    }

    private AudioEngine mAudio;
    private SettingsEditText mPromptText;
    private Button mOkButton;
    private Button mCancelButton;

    public TextPromptWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public TextPromptWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public TextPromptWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    protected void initialize(Context aContext) {
        inflate(aContext, R.layout.prompt_text, this);

        mAudio = AudioEngine.fromContext(aContext);

        mLayout = findViewById(R.id.layout);

        mTitle = findViewById(R.id.textTitle);
        mMessage = findViewById(R.id.textMessage);
        mMessage.setMovementMethod(new ScrollingMovementMethod());
        mPromptText = findViewById(R.id.promptText);

        mOkButton = findViewById(R.id.positiveButton);
        mOkButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            if (mPromptDelegate != null && mPromptDelegate instanceof TextPromptDelegate) {
                ((TextPromptDelegate)mPromptDelegate).confirm(mPromptText.getText().toString());
            }

            hide(REMOVE_WIDGET);
        });

        mCancelButton = findViewById(R.id.negativeButton);
        mCancelButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            onDismiss();
        });
    }

    public void setDefaultText(String text) {
        mPromptText.setText(text);
    }

}
