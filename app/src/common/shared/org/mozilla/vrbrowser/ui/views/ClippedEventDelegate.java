package org.mozilla.vrbrowser.ui.views;

import android.graphics.Region;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;

import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.utils.ViewUtils;

public abstract class ClippedEventDelegate implements View.OnHoverListener, View.OnTouchListener {

    private boolean mHovered;
    private boolean mTouched;
    private OnClickListener mClickListener;
    private View.OnHoverListener mOnHoverListener;

    View mView;
    Region mRegion;

    ClippedEventDelegate(@NonNull View view) {
        mView = view;
        mHovered = false;
        mTouched = false;
        mRegion = null;

        view.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!mView.isShown()) {
                mView.onHoverChanged(false);
                mView.setHovered(false);
                mTouched = false;
            }
        });
        view.getViewTreeObserver().addOnPreDrawListener(this::onUpdateRegion);
    }

    // The region should be recreated in this event based on the current state drawable
    abstract boolean onUpdateRegion();

    public void setOnHoverListener(View.OnHoverListener listener) {
        mOnHoverListener = listener;
    }

    public void setOnClickListener(OnClickListener listener) {
        mClickListener = listener;
    }

    @Override
    public boolean onHover(View v, MotionEvent event) {
        if(!isInside(event)) {
            if (mHovered) {
                mHovered = false;
                mView.setHovered(false);

                mView.onHoverChanged(false);
                event.setAction(MotionEvent.ACTION_HOVER_EXIT);

                if (mOnHoverListener != null) {
                    mOnHoverListener.onHover(v, event);
                }

                return v.onHoverEvent(event);
            }

            mView.onHoverChanged(false);
            return false;

        } else {
            mHovered = true;
            mView.setHovered(true);

            if (event.getAction() != MotionEvent.ACTION_HOVER_MOVE) {
                mView.onHoverChanged(true);
                event.setAction(MotionEvent.ACTION_HOVER_ENTER);
            }

            if (mOnHoverListener != null) {
                mOnHoverListener.onHover(v, event);
            }

            return v.onHoverEvent(event);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        v.getParent().requestDisallowInterceptTouchEvent(true);

        if(!isInside(event)) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_UP:
                    if (mTouched) {
                        v.requestFocus();
                        v.requestFocusFromTouch();
                        if (mClickListener != null) {
                            v.performClick();
                            mClickListener.onClick(v);
                        }
                    }
                    v.onHoverChanged(false);
                    v.setPressed(false);
                    v.setHovered(false);
                    mTouched = false;
            }

            return true;

        } else {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    mTouched = true;
                    return true;

                case MotionEvent.ACTION_UP:
                    if (mTouched && ViewUtils.isInsideView(v, (int)event.getRawX(), (int)event.getRawY())) {
                        v.requestFocus();
                        v.requestFocusFromTouch();
                        if (mClickListener != null) {
                            v.performClick();
                            mClickListener.onClick(v);
                        }
                    }
                    v.setHovered(false);
                    v.onHoverChanged(false);
                    v.setPressed(false);
                    mTouched = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    mTouched = false;
                    return true;
            }

            return false;
        }
    }

    boolean isInside(MotionEvent event) {
        if (mRegion != null) {
            return mRegion.contains((int)event.getX(),(int) event.getY());
        }

        return false;
    }

}
