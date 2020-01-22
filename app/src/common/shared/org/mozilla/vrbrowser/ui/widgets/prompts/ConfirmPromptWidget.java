package org.mozilla.vrbrowser.ui.widgets.prompts;

import android.content.Context;
import android.text.method.ScrollingMovementMethod;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;

public class ConfirmPromptWidget extends PromptWidget {

    public interface ConfirmPromptDelegate extends PromptDelegate {
        void confirm(int index);
    }

    private static final int POSITIVE = 0;
    private static final int NEGATIVE = 1;

    private AudioEngine mAudio;
    private Button[] mButtons;

    public ConfirmPromptWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public ConfirmPromptWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public ConfirmPromptWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    protected void initialize(Context aContext) {
        inflate(aContext, R.layout.prompt_confirm, this);

        mAudio = AudioEngine.fromContext(aContext);

        mLayout = findViewById(R.id.layout);

        mTitle = findViewById(R.id.confirmTitle);
        mMessage = findViewById(R.id.confirmMessage);
        mMessage.setMovementMethod(new ScrollingMovementMethod());

        mButtons = new Button[2];

        mButtons[POSITIVE] = findViewById(R.id.positiveButton);
        mButtons[POSITIVE].setOnClickListener(mButtonClickListener);
        mButtons[POSITIVE].setVisibility(GONE);

        mButtons[NEGATIVE] = findViewById(R.id.negativeButton);
        mButtons[NEGATIVE].setOnClickListener(mButtonClickListener);
        mButtons[NEGATIVE].setVisibility(GONE);
    }

    private OnClickListener mButtonClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            if (mPromptDelegate != null && mPromptDelegate instanceof ConfirmPromptDelegate) {
                ((ConfirmPromptDelegate)mPromptDelegate).confirm((int)view.getTag());
            }

            hide(REMOVE_WIDGET);
        }
    };

    public void setButtons(String[] btnMsg) {
        if (btnMsg[POSITIVE] != null) {
            mButtons[POSITIVE].setText(btnMsg[POSITIVE]);
            mButtons[POSITIVE].setTag(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE);
            mButtons[POSITIVE].setVisibility(VISIBLE);
        }
        if (btnMsg[NEGATIVE] != null) {
            mButtons[NEGATIVE].setText(btnMsg[NEGATIVE]);
            mButtons[NEGATIVE].setTag(GeckoSession.PromptDelegate.ButtonPrompt.Type.NEGATIVE);
            mButtons[NEGATIVE].setVisibility(VISIBLE);
        }
    }

}
