package com.igalia.wolvic.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.widget.FrameLayout;

public class FadingFrameLayout extends FrameLayout {

    private static final int[] FADE_COLORS_REVERSE = new int[]{Color.BLACK, Color.TRANSPARENT};

    private Paint mPaint;
    private Rect mRect;
    private boolean mDirty;

    public FadingFrameLayout(Context context) {
        super(context);
        init();
    }

    public FadingFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FadingFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        PorterDuffXfermode mode = new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setXfermode(mode);

        mRect = new Rect();
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        if (getPaddingRight() != right) {
            mDirty = true;
        }
        super.setPadding(left, top, right, bottom);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw) {
            mDirty = true;
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int newWidth = getWidth(), newHeight = getHeight();
        if (getVisibility() == GONE || newWidth == 0 || newHeight == 0) {
            super.dispatchDraw(canvas);
            return;
        }

        if (mDirty) {
            int actualWidth = getWidth() - getPaddingLeft() - getPaddingRight();
            int size = Math.min(getHorizontalFadingEdgeLength(), actualWidth);
            int l = getPaddingLeft() + actualWidth - size;
            int t = getPaddingTop();
            int r = l + size;
            int b = getHeight() - getPaddingBottom();
            mRect.set(l,  t, r, b);
            LinearGradient gradient = new LinearGradient(l, t, r, t, FADE_COLORS_REVERSE, null, Shader.TileMode.CLAMP);
            mPaint.setShader(gradient);
        }

        int count = canvas.saveLayer(0.0f, 0.0f, (float) getWidth(), (float) getHeight(), null, Canvas.ALL_SAVE_FLAG);
        super.dispatchDraw(canvas);

        if (isHorizontalFadingEdgeEnabled() && getHorizontalFadingEdgeLength() > 0) {
            canvas.drawRect(mRect, mPaint);
        }
        canvas.restoreToCount(count);
    }

}
