package com.igalia.wolvic.ui.widgets.prompts;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Button;

import androidx.annotation.NonNull;

import com.igalia.wolvic.R;
import com.igalia.wolvic.audio.AudioEngine;

public class ColorPromptWidget extends PromptWidget {

    public interface ColorPromptDelegate extends PromptDelegate {
        void confirm(@NonNull final String color);
    }

    private AudioEngine mAudio;
    private Button mCancelButton, mOkButton;

    public ColorPromptWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public ColorPromptWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public ColorPromptWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    protected void initialize(Context aContext) {
        inflate(aContext, R.layout.prompt_color, this);

        mAudio = AudioEngine.fromContext(aContext);
        mLayout = findViewById(R.id.layout);
        mTitle = findViewById(R.id.title);
        mCancelButton = findViewById(R.id.cancelButton);
        mOkButton = findViewById(R.id.okButton);

        mCancelButton.setOnClickListener(v -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            if (mPromptDelegate != null && mPromptDelegate instanceof ColorPromptDelegate) {
                mPromptDelegate.dismiss();
            }
            hide(REMOVE_WIDGET);
        });

        mOkButton.setOnClickListener(v -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            if (mPromptDelegate != null && mPromptDelegate instanceof ColorPromptDelegate) {
                ((ColorPromptDelegate) mPromptDelegate).confirm("#FFBF00");
            }
            hide(REMOVE_WIDGET);
        });
    }
}
