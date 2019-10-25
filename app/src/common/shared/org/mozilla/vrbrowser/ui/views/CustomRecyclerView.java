package org.mozilla.vrbrowser.ui.views;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.util.AttributeSet;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.RecyclerView;

import org.mozilla.vrbrowser.R;

public class CustomRecyclerView extends RecyclerView {

    private CustomFastScroller mFastScroller;

    public CustomRecyclerView(@NonNull Context context) {
        this(context, null);
    }

    public CustomRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        if (attrs != null) {
            int defStyleRes = 0;
            TypedArray a = context.obtainStyledAttributes(attrs, androidx.recyclerview.R.styleable.RecyclerView,
                    defStyle, defStyleRes);
            StateListDrawable verticalThumbDrawable = (StateListDrawable) a
                    .getDrawable(androidx.recyclerview.R.styleable.RecyclerView_fastScrollVerticalThumbDrawable);
            Drawable verticalTrackDrawable = a
                    .getDrawable(androidx.recyclerview.R.styleable.RecyclerView_fastScrollVerticalTrackDrawable);
            StateListDrawable horizontalThumbDrawable = (StateListDrawable) a
                    .getDrawable(androidx.recyclerview.R.styleable.RecyclerView_fastScrollHorizontalThumbDrawable);
            Drawable horizontalTrackDrawable = a
                    .getDrawable(androidx.recyclerview.R.styleable.RecyclerView_fastScrollHorizontalTrackDrawable);
            a.recycle();

            TypedArray customAttributes = context.obtainStyledAttributes(attrs, R.styleable.CustomRecyclerView,
                    defStyle, defStyleRes);
            boolean alwaysVisible = customAttributes.getBoolean(R.styleable.CustomRecyclerView_android_fastScrollAlwaysVisible, false);
            boolean enabled = customAttributes.getBoolean(R.styleable.CustomRecyclerView_customFastScrollEnabled, false);
            customAttributes.recycle();
            if (enabled) {
                initFastScroller(alwaysVisible, verticalThumbDrawable, verticalTrackDrawable, horizontalThumbDrawable, horizontalTrackDrawable);
            }

        } else {
            setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        }

        getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (getVisibility() == VISIBLE) {
                if (mFastScroller != null) {
                    mFastScroller.updateScrollPosition(computeHorizontalScrollOffset(),
                            computeVerticalScrollOffset());
                }
            }
        });
    }

    String exceptionLabel() {
        return " " + super.toString()
                + ", adapter:" + getAdapter()
                + ", layout:" + getLayoutManager()
                + ", context:" + getContext();
    }

    @VisibleForTesting
    void initFastScroller(boolean alwaysVisible, StateListDrawable verticalThumbDrawable,
                          Drawable verticalTrackDrawable, StateListDrawable horizontalThumbDrawable,
                          Drawable horizontalTrackDrawable) {
        if (verticalThumbDrawable == null || verticalTrackDrawable == null
                || horizontalThumbDrawable == null || horizontalTrackDrawable == null) {
            throw new IllegalArgumentException(
                    "Trying to set fast scroller without both required drawables." + exceptionLabel());
        }

        Resources resources = getContext().getResources();
        mFastScroller = new CustomFastScroller(this, verticalThumbDrawable, verticalTrackDrawable,
                horizontalThumbDrawable, horizontalTrackDrawable,
                resources.getDimensionPixelSize(R.dimen.fastscroll_default_thickness),
                resources.getDimensionPixelSize(R.dimen.fastscroll_minimum_range),
                resources.getDimensionPixelOffset(R.dimen.fastscroll_margin), alwaysVisible);
    }
}
