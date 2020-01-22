package org.mozilla.vrbrowser.ui.widgets.prompts;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.ui.widgets.dialogs.UIDialog;

/**
 * Base widget used for the browser triggered prompts: alert, confirm, prompt, auth and select
 */
public class PromptWidget extends UIDialog {

    public interface PromptDelegate {
        void dismiss();
    }

    protected TextView mTitle;
    protected TextView mMessage;
    protected ViewGroup mLayout;
    private int mMaxHeight;
    protected PromptDelegate mPromptDelegate;

    public PromptWidget(Context aContext) {
        super(aContext);
    }

    public PromptWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
    }

    public PromptWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
    }

    public void setPromptDelegate(@Nullable PromptDelegate delegate) {
        mPromptDelegate = delegate;
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
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.prompt_width);
        mMaxHeight = WidgetPlacement.dpDimension(getContext(), R.dimen.prompt_height);
        aPlacement.height = mMaxHeight;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.5f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.browser_children_z_distance);
    }

    @Override
    public void show(@ShowFlags int aShowFlags) {
        mLayout.measure(View.MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        mWidgetPlacement.height = (getMinHeight() == 0) ?
                (int)(mLayout.getMeasuredHeight()/mWidgetPlacement.density) :
                getMinHeight();
        super.show(aShowFlags);

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

    @Override
    protected void onDismiss() {
        hide(REMOVE_WIDGET);

        if (mPromptDelegate != null) {
            mPromptDelegate.dismiss();
        }

        if (mDelegate != null) {
            mDelegate.onDismiss();
        }
    }

    public int getMinHeight() {
        return 0;
    }

}
