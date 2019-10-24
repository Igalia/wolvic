package org.mozilla.vrbrowser.ui.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Interpolator;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AnimationUtils;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;

import org.mozilla.vrbrowser.R;

public class CustomScrollView extends ScrollView {

    public static long SCROLLER_FADE_TIMEOUT = 1500;

    private static final int[] DRAWABLE_STATE_PRESSED = new int[] { android.R.attr.state_pressed };
    private static final int[] DRAWABLE_STATE_HOVER = new int[] { android.R.attr.state_hovered };
    private static final int[] DRAWABLE_STATE_DEFAULT = new int[] {};

    private static final float[] OPAQUE = { 255 };
    private static final float[] TRANSPARENT = { 0.0f };

    private float mDownY;
    private Rect mThumbRect;
    private Drawable mThumbDrawable;
    private int mThumbMinHeight;
    private ScrollAnimator mScrollCache;
    private boolean mThumbDynamicHeight;
    private boolean mIsAlwaysVisible;
    private boolean mIsHandlingTouchEvent = false;
    private int mSize;

    public CustomScrollView(Context context) {
        super(context);
        createScrollDelegate(context, null, 0);
    }

    public CustomScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        createScrollDelegate(context, attrs, 0);
    }

    public CustomScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        createScrollDelegate(context, attrs, defStyle);
    }

    private void createScrollDelegate(Context context, AttributeSet attrs, int defStyle) {

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CustomScrollView, 0, defStyle);
            mThumbDrawable = a.getDrawable(R.styleable.CustomScrollView_android_fastScrollThumbDrawable);
            if (mThumbDrawable == null) {
                mThumbDrawable = getResources().getDrawable(R.drawable.scrollbar_thumb, getContext().getTheme());
            }
            mIsAlwaysVisible = a.getBoolean(R.styleable.CustomScrollView_android_fastScrollAlwaysVisible, false);
            mThumbDynamicHeight = a.getBoolean(R.styleable.CustomScrollView_dynamicHeight, true);
            mSize = a.getDimensionPixelSize(R.styleable.CustomScrollView_android_scrollbarSize, getResources().getDimensionPixelSize(R.dimen.scrollbarWidth));
            a.recycle();
        }

        setVerticalScrollBarEnabled(false);
        mScrollCache = new ScrollAnimator(this);
        mThumbRect = new Rect(0, 0, mSize, mSize);

        setThumbDrawable(mThumbDrawable);
        setAlwaysVisible(mIsAlwaysVisible);
        setThumbSize(mSize, mSize);
        setThumbDynamicHeight(mThumbDynamicHeight);
    }

    public void setThumbDrawable(@NonNull Drawable drawable) {
        mThumbDrawable = drawable;
        updateThumbRect(0);
    }

    public void setThumbSize(int widthDp, int heightDp) {
        mThumbRect.left = mThumbRect.right - widthDp;
        mThumbMinHeight = heightDp;
        updateThumbRect(0);
    }

    public void setThumbDynamicHeight(boolean isDynamicHeight) {
        if (mThumbDynamicHeight != isDynamicHeight) {
            mThumbDynamicHeight = isDynamicHeight;
            updateThumbRect(0);
        }
    }

    public void setAlwaysVisible(boolean visible) {
        mIsAlwaysVisible = visible;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (onInterceptTouchEventInternal(ev)) {
            return true;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (onTouchEventInternal(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        if (onHoverEventInternal(event)) {
            return true;
        }
        return super.onHoverEvent(event);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        initialAwakenScrollBars();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);

        if (visibility == VISIBLE) {
            if (ViewCompat.isAttachedToWindow(this)) {
                initialAwakenScrollBars();
            }

        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);

        if (visibility == VISIBLE) {
            initialAwakenScrollBars();
        }
    }

    @Override
    protected boolean awakenScrollBars() {
        return awakenScrollBarsInternal(SCROLLER_FADE_TIMEOUT);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        drawScrollBars(canvas);
    }

    // Internal methods

    private boolean onInterceptTouchEventInternal(@NonNull MotionEvent ev) {
        final int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            return onTouchEventInternal(ev);
        }
        return false;
    }

    private boolean onTouchEventInternal(@NonNull MotionEvent event) {
        final int action = event.getActionMasked();
        final float y = event.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                if (mScrollCache.mState == ScrollAnimatorState.OFF) {
                    mIsHandlingTouchEvent = false;
                    return false;
                }
                if (!mIsHandlingTouchEvent) {
                    updateThumbRect(0);
                    final float x = event.getX();
                    if (y >= mThumbRect.top && y <= mThumbRect.bottom && x >= mThumbRect.left && x <= mThumbRect.right) {
                        mIsHandlingTouchEvent = true;
                        mDownY = y;
                        super.onTouchEvent(event);
                        MotionEvent fakeCancelMotionEvent = MotionEvent.obtain(event);
                        fakeCancelMotionEvent.setAction(MotionEvent.ACTION_CANCEL);
                        super.onTouchEvent(fakeCancelMotionEvent);
                        fakeCancelMotionEvent.recycle();
                        setHoveredThumb(false);
                        setPressedThumb(true);
                        updateThumbRect(0, true);
                        removeCallbacks(mScrollCache);
                    }
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mIsHandlingTouchEvent) {
                    final int touchDeltaY = Math.round(y - mDownY);
                    if (touchDeltaY != 0) {
                        updateThumbRect(touchDeltaY);
                        mDownY = y;
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (mIsHandlingTouchEvent) {
                    mIsHandlingTouchEvent = false;
                    setPressedThumb(false);
                    awakenScrollBars();
                }
                break;
            }

        }
        if (mIsHandlingTouchEvent) {
            invalidate();
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }
        return false;
    }

    private boolean onHoverEventInternal(@NonNull MotionEvent event) {
        final int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_MOVE: {
                setHoveredThumb(true);
                return true;
            }
            case MotionEvent.ACTION_HOVER_EXIT: {
                setHoveredThumb(false);
                return true;
            }
        }
        return false;
    }

    private boolean initialAwakenScrollBars() {
        return awakenScrollBarsInternal(mScrollCache.mScrollBarDefaultDelayBeforeFade * 4);
    }

    private boolean awakenScrollBarsInternal(long startDelay) {
        ViewCompat.postInvalidateOnAnimation(this);
        if (!mIsHandlingTouchEvent) {
            if (mScrollCache.mState == ScrollAnimatorState.OFF) {
                final int KEY_REPEAT_FIRST_DELAY = 750;
                startDelay = Math.max(KEY_REPEAT_FIRST_DELAY, startDelay);
            }
            long fadeStartTime = AnimationUtils.currentAnimationTimeMillis() + startDelay;
            mScrollCache.mFadeStartTime = fadeStartTime;
            mScrollCache.mState = ScrollAnimatorState.ON;
            removeCallbacks(mScrollCache);
            postDelayed(mScrollCache, fadeStartTime - AnimationUtils.currentAnimationTimeMillis());
        }
        return false;
    }

    private void drawScrollBars(Canvas canvas) {
        boolean invalidate = false;
        if (mIsHandlingTouchEvent) {
            mThumbDrawable.setAlpha(255);

        } else {
            if (!mIsAlwaysVisible) {
                final ScrollAnimator cache = mScrollCache;
                final ScrollAnimatorState state = cache.mState;
                if (state == ScrollAnimatorState.OFF) {
                    return;
                }
                if (state == ScrollAnimatorState.FADING) {
                    if (cache.mInterpolatorValues == null) {
                        cache.mInterpolatorValues = new float[1];
                    }
                    float[] values = cache.mInterpolatorValues;
                    if (cache.mScrollBarInterpolator.timeToValues(values) == Interpolator.Result.FREEZE_END) {
                        cache.mState = ScrollAnimatorState.OFF;
                    } else {
                        mThumbDrawable.setAlpha(Math.round(values[0]));
                    }
                    invalidate = true;

                } else {
                    mThumbDrawable.setAlpha(255);
                }
            }
        }

        if (updateThumbRect(0)) {
            final int scrollY = getScrollY();
            final int scrollX = getScrollX();
            mThumbDrawable.setBounds(mThumbRect.left + scrollX, mThumbRect.top + scrollY, mThumbRect.right + scrollX,
                    mThumbRect.bottom + scrollY);
            mThumbDrawable.draw(canvas);
        }
        if (invalidate) {
            invalidate();
        }
    }

    private void setPressedThumb(boolean pressed) {
        mThumbDrawable.setState(pressed ? DRAWABLE_STATE_PRESSED : DRAWABLE_STATE_DEFAULT);
        invalidate();
    }

    private void setHoveredThumb(boolean hover) {
        mThumbDrawable.setState(hover ? DRAWABLE_STATE_HOVER : DRAWABLE_STATE_DEFAULT);
        invalidate();
    }

    private boolean updateThumbRect(int touchDeltaY) {
        return updateThumbRect(touchDeltaY, false);
    }

    private boolean updateThumbRect(int touchDeltaY, boolean forceReportFastScrolled) {
        final int thumbWidth = mThumbRect.width();
        mThumbRect.right = getWidth();
        mThumbRect.left = mThumbRect.right - thumbWidth;
        final int scrollRange = super.computeVerticalScrollRange();
        if (scrollRange <= 0) {
            return false;
        }
        final int scrollOffset = super.computeVerticalScrollOffset();
        final int scrollExtent = super.computeVerticalScrollExtent();
        final int scrollMaxOffset = scrollRange - scrollExtent;
        if (scrollMaxOffset <= 0) {
            return false;
        }
        final float scrollPercent = scrollOffset * 1f / (scrollMaxOffset);
        final float visiblePercent = scrollExtent * 1f / scrollRange;
        final int viewHeight = getHeight();
        final int thumbHeight = mThumbDynamicHeight ? Math
                .max(mThumbMinHeight, Math.round(visiblePercent * viewHeight)) : mThumbMinHeight;
        mThumbRect.bottom = mThumbRect.top + thumbHeight;
        final int thumbTop = Math.round((viewHeight - thumbHeight) * scrollPercent);
        mThumbRect.offsetTo(mThumbRect.left, thumbTop);

        if (touchDeltaY != 0) {
            int newThumbTop = thumbTop + touchDeltaY;
            final int minThumbTop = 0;
            final int maxThumbTop = viewHeight - thumbHeight;
            if (newThumbTop > maxThumbTop) {
                newThumbTop = maxThumbTop;

            } else if (newThumbTop < minThumbTop) {
                newThumbTop = minThumbTop;
            }

            final float newScrollPercent = newThumbTop * 1f / maxThumbTop;
            final int newScrollOffset = Math.round((scrollRange - scrollExtent) * newScrollPercent);
            final int viewScrollDeltaY = newScrollOffset - scrollOffset;
            scrollBy(0, viewScrollDeltaY);
        }

        return true;
    }

    public enum ScrollAnimatorState {
        OFF,
        ON,
        FADING
    }

    private class ScrollAnimator implements Runnable {

        final int mScrollBarDefaultDelayBeforeFade;
        final int mScrollBarFadeDuration;
        float[] mInterpolatorValues;
        View mHost;
        long mFadeStartTime;
        public ScrollAnimatorState mState = ScrollAnimatorState.OFF;
        final Interpolator mScrollBarInterpolator = new Interpolator(1, 2);

        public ScrollAnimator(View host) {
            mScrollBarDefaultDelayBeforeFade = ViewConfiguration.getScrollDefaultDelay();
            mScrollBarFadeDuration = ViewConfiguration.getScrollBarFadeDuration();
            mHost = host;
        }

        public void run() {
            long now = AnimationUtils.currentAnimationTimeMillis();
            if (now >= mFadeStartTime) {
                int nextFrame = (int) now;
                int framesCount = 0;

                Interpolator interpolator = mScrollBarInterpolator;
                interpolator.setKeyFrame(framesCount++, nextFrame, OPAQUE);

                nextFrame += mScrollBarFadeDuration;
                interpolator.setKeyFrame(framesCount, nextFrame, TRANSPARENT);

                mState = ScrollAnimatorState.FADING;

                mHost.invalidate();
            }
        }
    }

}
