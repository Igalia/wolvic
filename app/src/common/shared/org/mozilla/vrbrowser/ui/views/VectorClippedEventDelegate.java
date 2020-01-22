package org.mozilla.vrbrowser.ui.views;

import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.utils.ViewUtils;

import java.util.Deque;

public class VectorClippedEventDelegate implements View.OnHoverListener, View.OnTouchListener {

    private View mView;
    private @DrawableRes int mRes;
    private Region mRegion;
    private boolean mHovered;
    private boolean mTouched;
    private OnClickListener mClickListener;

    VectorClippedEventDelegate(@DrawableRes int res, @NonNull View view) {
        mView = view;
        mRes = res;
        mHovered = false;
        mTouched = false;

        view.getViewTreeObserver().addOnGlobalLayoutListener(mLayoutListener);
    }

    private ViewTreeObserver.OnGlobalLayoutListener mLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            Path path = createPathFromResource(mRes);
            RectF bounds = new RectF();
            path.computeBounds(bounds, true);

            bounds = new RectF();
            path.computeBounds(bounds, true);
            mRegion = new Region();
            mRegion.setPath(path, new Region((int) bounds.left, (int) bounds.top, (int) bounds.right, (int) bounds.bottom));

            mView.getViewTreeObserver().removeOnGlobalLayoutListener(mLayoutListener);
        }
    };

    public void setOnClickListener(OnClickListener listener) {
        mClickListener = listener;
    }

    private Path createPathFromResource(@DrawableRes int res) {
        VectorShape shape = new VectorShape(mView.getContext(), res);
        shape.onResize(mView.getWidth(), mView.getHeight());
        Deque<VectorShape.Layer> layers = shape.getLayers();
        VectorShape.Layer layer = layers.getFirst();

        // TODO Handle state changes and update the Region based on the new current state shape

        return layer.transformedPath;
    }

    @Override
    public boolean onHover(View v, MotionEvent event) {
        if(!isInside(event)) {
            if (mHovered) {
                mHovered = false;
                event.setAction(MotionEvent.ACTION_HOVER_EXIT);
                return v.onHoverEvent(event);
            }

            return false;

        } else {
            if (!mHovered) {
                mHovered = true;
                event.setAction(MotionEvent.ACTION_HOVER_ENTER);
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
                            mClickListener.onClick(v);
                        }
                    }
                    v.setPressed(false);
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
                            mClickListener.onClick(v);
                        }
                    }
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

    public boolean isInside(MotionEvent event) {
        return mRegion.contains((int)event.getX(),(int) event.getY());
    }

}
