package com.igalia.wolvic.ui.widgets.prompts;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Button;

import com.igalia.wolvic.R;
import com.igalia.wolvic.audio.AudioEngine;

import org.jetbrains.annotations.NotNull;

public class DateTimePromptWidget extends PromptWidget {

            private AudioEngine mAudio;
            private Button mCancelButton, mOkButton;

            public DateTimePromptWidget(Context aContext) {
                super(aContext);
                initialize(aContext);
            }

            public DateTimePromptWidget(Context aContext, AttributeSet aAttrs) {
                super(aContext, aAttrs);
                initialize(aContext);
            }

            public DateTimePromptWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
                super(aContext, aAttrs, aDefStyle);
                initialize(aContext);
            }

            protected void initialize(Context aContext) {
                inflate(aContext, R.layout.prompt_date_time, this);

                mAudio = AudioEngine.fromContext(aContext);
                mLayout = findViewById(R.id.layout);
                mTitle = findViewById(R.id.title);
                mCancelButton = findViewById(R.id.cancelButton);
                mOkButton = findViewById(R.id.okButton);

                mCancelButton.setOnClickListener(v -> {
                    if (mAudio != null) {
                        mAudio.playSound(AudioEngine.Sound.CLICK);
                    }
                    if (mPromptDelegate != null && mPromptDelegate instanceof DateTimePromptDelegate) {
                        mPromptDelegate.dismiss();
                    }
                    hide(REMOVE_WIDGET);
                });

                mOkButton.setOnClickListener(v -> {
                    if (mAudio != null) {
                        mAudio.playSound(AudioEngine.Sound.CLICK);
                    }
                    if (mPromptDelegate != null && mPromptDelegate instanceof DateTimePromptDelegate) {

                    }
                    hide(REMOVE_WIDGET);
                });
            }

            public interface DateTimePromptDelegate extends PromptDelegate {
                void confirm(@NotNull final String color);
            }
        }
    }
}
