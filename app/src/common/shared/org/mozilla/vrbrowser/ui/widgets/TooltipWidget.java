package org.mozilla.vrbrowser.ui.widgets;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.view.View;
import android.widget.TextView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.utils.ViewUtils;

public class TooltipWidget extends UIWidget {

    private View mTargetView;
    private UIWidget mParentWidget;
    protected TextView mText;
    private PointF mTranslation;
    private float mRatio;
    private float mDensityRatio;

    public TooltipWidget(Context aContext) {
        super(aContext);

        initialize();
    }

    private void initialize() {
        inflate(getContext(), R.layout.tooltip, this);

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
        mWidgetPlacement.translationX = mTranslation.x * (mRatio / mWidgetPlacement.density);
        mWidgetPlacement.translationY = mTranslation.y * (mRatio / mWidgetPlacement.density);
        int paddingH = getPaddingStart() + getPaddingEnd();
        int paddingV = getPaddingTop() + getPaddingBottom();
        mWidgetPlacement.width = (int)(WidgetPlacement.convertPixelsToDp(getContext(), getMeasuredWidth() + paddingH)/mDensityRatio);
        mWidgetPlacement.height = (int)(WidgetPlacement.convertPixelsToDp(getContext(), getMeasuredHeight() + paddingV)/mDensityRatio);

        super.show(aShowFlags);
    }

    public void setLayoutParams(View targetView) {
        this.setLayoutParams(targetView, ViewUtils.TooltipPosition.BOTTOM);
    }

    public void setLayoutParams(View targetView, ViewUtils.TooltipPosition position) {
        this.setLayoutParams(targetView, position, mWidgetPlacement.density);
    }

    public void setLayoutParams(View targetView, ViewUtils.TooltipPosition position, float density) {
        mTargetView = targetView;
        mParentWidget = ViewUtils.getParentWidget(mTargetView);
        if (mParentWidget != null) {
            mRatio = WidgetPlacement.worldToWidgetRatio(mParentWidget);
            mWidgetPlacement.density = density;
            mDensityRatio = mWidgetPlacement.density / getContext().getResources().getDisplayMetrics().density;

            Rect offsetViewBounds = new Rect();
            getDrawingRect(offsetViewBounds);
            mParentWidget.offsetDescendantRectToMyCoords(mTargetView, offsetViewBounds);

            mWidgetPlacement.parentHandle = mParentWidget.getHandle();
            // At the moment we only support showing tooltips on top or bottom of the target view
            if (position == ViewUtils.TooltipPosition.BOTTOM) {
                mWidgetPlacement.anchorY = 1.0f;
                mWidgetPlacement.parentAnchorY = 0.0f;
                mTranslation = new PointF(
                        (offsetViewBounds.left + mTargetView.getWidth() / 2) * mDensityRatio,
                        -offsetViewBounds.top * mDensityRatio);
            } else {
                mWidgetPlacement.anchorY = 0.0f;
                mWidgetPlacement.parentAnchorY = 1.0f;
                mTranslation = new PointF(
                        (offsetViewBounds.left + mTargetView.getWidth() / 2) * mDensityRatio,
                        offsetViewBounds.top * mDensityRatio);
            }
        }
    }

    public void setText(String text) {
        mText.setText(text);
    }

}
