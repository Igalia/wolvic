package com.igalia.wolvic.ui.widgets.prompts;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Button;

import androidx.annotation.NonNull;

import com.igalia.wolvic.R;
import com.igalia.wolvic.audio.AudioEngine;
import com.skydoves.colorpickerview.ColorPickerView;
import com.skydoves.colorpickerview.flag.BubbleFlag;
import com.skydoves.colorpickerview.flag.FlagMode;
import com.skydoves.colorpickerview.preference.ColorPickerPreferenceManager;

public class ColorPromptWidget extends PromptWidget {

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
        new ColorPickerView.Builder(getContext()).build();

        ColorPickerView colorPickerView = findViewById(R.id.colorPickerView);
        BubbleFlag bubbleFlag = new BubbleFlag(getContext());
        bubbleFlag.setFlagMode(FlagMode.ALWAYS);
        colorPickerView.setFlagView(bubbleFlag);
        ColorPickerPreferenceManager manager = ColorPickerPreferenceManager.getInstance(getContext());

        mAudio = AudioEngine.fromContext(aContext);
        mLayout = findViewById(R.id.layout);
        mTitle = findViewById(R.id.title);
        mCancelButton = findViewById(R.id.negativeButton);
        mOkButton = findViewById(R.id.positiveButton);

        mCancelButton.setOnClickListener(v -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            if (mPromptDelegate != null && mPromptDelegate instanceof ColorPromptDelegate) {
                mPromptDelegate.dismiss();
                manager.clearSavedAllData();
            }
            hide(REMOVE_WIDGET);
        });

        mOkButton.setOnClickListener(v -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            // Color from Android is in ARGB format, so we need to convert it the hex format used in HTML.
            if (mPromptDelegate != null && mPromptDelegate instanceof ColorPromptDelegate) {
                String colorCodeHex = "#" + colorPickerView.getColorEnvelope().getHexCode().substring(2, 8);
                manager.saveColorPickerData(colorPickerView);
                ((ColorPromptDelegate) mPromptDelegate).confirm(colorCodeHex);
            }
            hide(REMOVE_WIDGET);
        });
    }

    public interface ColorPromptDelegate extends PromptDelegate {
        void confirm(@NonNull final String color);
    }
}