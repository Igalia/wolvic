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

    private static final int POSITIVE = 0;
    private static final int NEUTRAL = 1;
    private static final int NEGATIVE = 2;

    private AudioEngine mAudio;
    private Button[] mButtons;
    private GeckoSession.PromptDelegate.ButtonCallback mCallback;

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

    @Override
    protected void initialize(Context aContext) {
        super.initialize(aContext);

        inflate(aContext, R.layout.prompt_confirm, this);

        mWidgetManager.addFocusChangeListener(this);

        mAudio = AudioEngine.fromContext(aContext);

        mLayout = findViewById(R.id.layout);

        mTitle = findViewById(R.id.confirmTitle);
        mMessage = findViewById(R.id.confirmMessage);
        mMessage.setMovementMethod(new ScrollingMovementMethod());

        mButtons = new Button[3];

        mButtons[POSITIVE] = findViewById(R.id.positiveButton);
        mButtons[POSITIVE].setSoundEffectsEnabled(false);
        mButtons[POSITIVE].setOnClickListener(mButtonClickListener);
        mButtons[POSITIVE].setVisibility(GONE);

        mButtons[NEUTRAL] = findViewById(R.id.neutralButton);
        mButtons[NEUTRAL].setSoundEffectsEnabled(false);
        mButtons[NEUTRAL].setOnClickListener(mButtonClickListener);
        mButtons[NEUTRAL].setVisibility(GONE);

        mButtons[NEGATIVE] = findViewById(R.id.negativeButton);
        mButtons[NEGATIVE].setSoundEffectsEnabled(false);
        mButtons[NEGATIVE].setOnClickListener(mButtonClickListener);
        mButtons[NEGATIVE].setVisibility(GONE);
    }

    private OnClickListener mButtonClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            if (mCallback != null) {
                mCallback.confirm((int)view.getTag());
            }

            hide(REMOVE_WIDGET);
        }
    };

    @Override
    protected void onDismiss() {
        hide(REMOVE_WIDGET);

        if (mCallback != null) {
            mCallback.dismiss();
        }
    }

    public void setDelegate(GeckoSession.PromptDelegate.ButtonCallback delegate) {
        mCallback = delegate;
    }

    public void setButtons(String[] btnMsg) {
        // NOTE: For some reason Gecko handles positive and negative internally reversed.
        // Returning 0 should be Ok but is in fact Cancel.
        if (btnMsg[POSITIVE] != null) {
            mButtons[POSITIVE].setText(btnMsg[POSITIVE]);
            mButtons[POSITIVE].setTag(NEGATIVE);
            mButtons[POSITIVE].setVisibility(VISIBLE);
        }
        if (btnMsg[NEUTRAL] != null) {
            mButtons[NEUTRAL].setText(btnMsg[NEUTRAL]);
            mButtons[NEUTRAL].setTag(NEUTRAL);
            mButtons[NEUTRAL].setVisibility(VISIBLE);
        }
        if (btnMsg[NEGATIVE] != null) {
            mButtons[NEGATIVE].setText(btnMsg[NEGATIVE]);
            mButtons[NEGATIVE].setTag(POSITIVE);
            mButtons[NEGATIVE].setVisibility(VISIBLE);
        }
    }

}
