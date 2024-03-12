package com.igalia.wolvic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

public class AlignDynamicButton extends MaterialButton {

    private PointF mPointPosition = new PointF();
    private RectF mSquareRect = new RectF();
    private Paint mPaint;
    private float mStrokeWidth;
    private float mCornerRadius;
    private float mSquarePadding;
    private float mCirclePadding;

    private final int PAINT_COLOR = 0xFFFFFFFF;

    public AlignDynamicButton(@NonNull Context context) {
        super(context);
        init();
    }

    public AlignDynamicButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AlignDynamicButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // metrics
        mStrokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
        mCornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
        mSquarePadding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        mCirclePadding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());

        mPaint = new Paint();
        mPaint.setColor(PAINT_COLOR);
        mPaint.setStrokeWidth(mStrokeWidth);
        mPaint.setAntiAlias(true);

    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw square
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setAlpha(150);
        mSquareRect.set(mSquarePadding, mSquarePadding, getWidth() - mSquarePadding, getHeight() - mSquarePadding);

        canvas.drawRoundRect(mSquareRect, mCornerRadius, mCornerRadius, mPaint);

        // Draw circle
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setAlpha(255);
        float radius = Math.min(getWidth(), getHeight()) / 10f;
        float centerX = mCirclePadding + ((mPointPosition.x + 1) * (getWidth() - mCirclePadding) / 2f);
        float centerY = mCirclePadding + ((mPointPosition.y + 1) * (getHeight() - mCirclePadding) / 2f);

        canvas.drawCircle(centerX, centerY, radius, mPaint);
    }

    public void updatePosition(float x, float y) {
        mPointPosition.set(Math.max(-1.0f, Math.min(1.0f, x)),
                Math.max(-1.0f, Math.min(1.0f, y)));

        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(width, height);
        setMeasuredDimension(size, size);
    }
}
