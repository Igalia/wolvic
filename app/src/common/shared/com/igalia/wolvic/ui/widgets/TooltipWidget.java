package com.igalia.wolvic.ui.widgets;

import android.content.Context;
import android.content.res.Configuration;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.igalia.wolvic.R;
import com.igalia.wolvic.utils.AnimationHelper;

public class TooltipWidget extends UIWidget {

    protected TextView mText;
    protected ViewGroup mLayout;
    protected int mLayoutRes;
    protected float mDefaultDensity;
    protected float mDensityScale = 1.0f;

    public TooltipWidget(@NonNull Context aContext, @NonNull  @LayoutRes int layoutRes) {
        super(aContext);

        initialize(layoutRes);
    }

    public TooltipWidget(Context aContext) {
        super(aContext);

        initialize(R.layout.tooltip);
    }

    private void initialize(@NonNull @LayoutRes int layoutRes) {
        mLayoutRes = layoutRes;
        updateUI();
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
        mDefaultDensity = getResources().getDisplayMetrics().density;
        aPlacement.density = mDefaultDensity * mDensityScale;
    }

    public void updateUI() {
        removeAllViews();

        inflate(getContext(), mLayoutRes, this);

        mLayout = findViewById(R.id.layout);
        mText = findViewById(R.id.tooltipText);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        updateUI();
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

    // Modify the widget scale to change the size of the widget.
    public void setDensityScale(float densityScale) {
        mDensityScale = densityScale;
        getPlacement().density = mDefaultDensity * mDensityScale;
    }
}
