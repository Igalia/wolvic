package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.WidgetPlacement;
import org.mozilla.vrbrowser.audio.AudioEngine;

public class CloseButtonWidget extends UIWidget {

    private static final String LOGTAG = "VRB";

    public interface CloseButtonDelegate {
        void OnClick();
    }

    private UIButton mCancelButton;
    private CloseButtonDelegate mDelegate;
    private AudioEngine mAudio;

    public CloseButtonWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public CloseButtonWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public CloseButtonWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.close_button, this);

        mCancelButton = findViewById(R.id.closeButton);

        mCancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }

                if (mDelegate != null) {
                    mDelegate.OnClick();
                }
            }
        });

        mAudio = AudioEngine.fromContext(aContext);
    }

    @Override
    public void releaseWidget() {
        super.releaseWidget();
    }

    public void setDelegate(CloseButtonDelegate delegate) {
        mDelegate = delegate;
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.close_button_width);
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.close_button_height);
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 1.0f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.voice_search_world_z);
    }

    @Override
    public void show() {
        if (!mWidgetPlacement.visible) {
            mWidgetPlacement.visible = true;
            mWidgetManager.addWidget(this);
        }
    }

    @Override
    public void hide() {
        if (mWidgetPlacement.visible) {
            mWidgetPlacement.visible = false;
            mWidgetManager.removeWidget(this);
        }
    }

}
