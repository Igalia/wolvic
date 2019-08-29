package org.mozilla.vrbrowser.ui.widgets;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.utils.AnimationHelper;

public class TooltipWidget extends UIWidget {

    protected TextView mText;
    protected ViewGroup mLayout;

    public TooltipWidget(@NonNull Context aContext, @NonNull  @LayoutRes int layoutRes) {
        super(aContext);

        initialize(layoutRes);
    }

    public TooltipWidget(Context aContext) {
        super(aContext);

        initialize(R.layout.tooltip);
    }

    private void initialize(@NonNull @LayoutRes int layoutRes) {
        inflate(getContext(), layoutRes, this);

        mLayout = findViewById(R.id.layout);
        mText = findViewById(R.id.tooltipText);
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  0;
        aPlacement.height = 0;
        aPlacement.parentAnchorX = 0.0f;
        aPlacement.parentAnchorY = 1.0f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.tooltip_z_distance);
    }

    @Override
    public void show(@ShowFlags int aShowFlags) {
        measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int paddingH = getPaddingStart() + getPaddingEnd();
        int paddingV = getPaddingTop() + getPaddingBottom();
        mWidgetPlacement.width = (int)((getMeasuredWidth() + paddingH)/mWidgetPlacement.density);
        mWidgetPlacement.height = (int)((getMeasuredHeight() + paddingV)/mWidgetPlacement.density);

        super.show(aShowFlags);
        AnimationHelper.scaleIn(mLayout, 100, 0, null);
    }

    @Override
    public void hide(int aHideFlags) {
        AnimationHelper.scaleOut(mLayout, 100, 0, () -> TooltipWidget.super.hide(aHideFlags));
    }

    public void setCurvedMode(boolean enabled) {
        mWidgetPlacement.cylinder = enabled;
    }

    public void setText(@StringRes int stringRes) {
        mText.setText(stringRes);
    }

    public void setText(String text) {
        mText.setText(text);
    }

}
