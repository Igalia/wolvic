package org.mozilla.vrbrowser.ui.widgets.prompts;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.ui.widgets.dialogs.UIDialog;

public class PromptWidget extends UIDialog {

    protected TextView mTitle;
    protected TextView mMessage;
    protected ViewGroup mLayout;
    private int mMaxHeight;

    public PromptWidget(Context aContext) {
        super(aContext);
    }

    public PromptWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
    }

    public PromptWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
    }

    public void setTitle(String title) {
        if (title == null || title.isEmpty()) {
            mTitle.setVisibility(View.GONE);

        } else {
            mTitle.setText(title);
        }
    }

    public void setMessage(String message) {
        if (message == null || message.isEmpty()) {
            mMessage.setVisibility(View.GONE);

        } else {
            mMessage.setText(message);
        }
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.pixelDimension(getContext(), R.dimen.browser_width_pixels)/2;
        mMaxHeight = WidgetPlacement.dpDimension(getContext(), R.dimen.prompt_height);
        aPlacement.height = mMaxHeight;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.5f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.browser_children_z_distance);
    }


    @Override
    public void show() {
        mLayout.measure(View.MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        mWidgetPlacement.height = (int)(mLayout.getMeasuredHeight()/mWidgetPlacement.density);
        super.show();

        ViewTreeObserver viewTreeObserver = mLayout.getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
            viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    mWidgetPlacement.height = (int)(mLayout.getHeight()/mWidgetPlacement.density);
                    mWidgetManager.updateWidget(PromptWidget.this);
                }
            });
        }
    }

    public void show(boolean focus) {
        super.show(focus);
        mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);
    }

    public void hide(@HideFlags int aHideFlags) {
        super.hide(aHideFlags);
        mWidgetManager.popWorldBrightness(this);
    }

    // WidgetManagerDelegate.FocusChangeListener
    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (oldFocus == this && isVisible() && findViewById(newFocus.getId()) == null) {
            onDismiss();
        }
    }

}
