package com.igalia.wolvic.ui.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ObservableBoolean;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.ui.viewmodel.WindowViewModel;
import com.igalia.wolvic.utils.SystemUtils;

public class MousePointerWidget extends UIWidget {

    private static final String LOGTAG = SystemUtils.createLogtag(MousePointerWidget.class);

    private WindowWidget mAttachedWindow;
    private boolean mIsWindowAttached;

    private Paint mPaint;
    private Path mPointerPath;

    private float mCurrentX = 0;
    private float mCurrentY = 0;

    public MousePointerWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public MousePointerWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public MousePointerWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(@NonNull Context aContext) {
        updateUI();
    }

    private void updateUI() {
        removeAllViews();

        // Initialize paint for drawing the pointer
        mPaint = new Paint();
        mPaint.setColor(Color.BLACK); // Or any color you prefer
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setAntiAlias(true);

        // Initialize path for the pointer shape (e.g., a triangle)
        mPointerPath = new Path();
        // Define the shape of your mouse pointer here
        // Example: a simple triangle pointing right-down
        mPointerPath.moveTo(0, 0);
        mPointerPath.lineTo(20, 10);
        mPointerPath.lineTo(10, 20);
        mPointerPath.close();

        // Set initial visibility (you might want to hide it initially)
        mWidgetPlacement.visible = false;
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.width = 8;
        aPlacement.height = 8;
        aPlacement.cylinder = false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Log.d(LOGTAG, "onDraw: " + mCurrentX + ", " + mCurrentY);
        super.onDraw(canvas);

        // Draw the mouse pointer shape on the canvas
        canvas.drawPath(mPointerPath, mPaint);
    }

    // Method to update the pointer's position based on mouse events
    // Method to update the pointer's position based on mouse events
    public void updatePointerPosition(float x, float y) {
        Log.d(LOGTAG, "updatePointerPosition: " + x + ", " + y);
        mCurrentX = x;
        mCurrentY = y;

        // Update the widget's placement directly
        // You'll need to translate screen coordinates (from MotionEvent)
        // to your VR scene coordinates. This will depend on your VR rendering setup.
        // For a simple overlay, you might just set the translation directly.

        // Example (conceptual):
        mWidgetPlacement.translationX = mCurrentX;
        mWidgetPlacement.translationY = mCurrentY;
        // mWidgetPlacement.translationZ = ... // Adjust Z if needed

        // Notify the WidgetManager that the placement has changed
        if (mWidgetManager != null) {
            mWidgetManager.updateWidget(this);
        }
    }

    // You might need methods to show and hide the pointer
    public void showPointer() {
        Log.d(LOGTAG, "showPointer");
        mWidgetPlacement.visible = true;
        mWidgetManager.updateWidget(this);
    }

    public void hidePointer() {
        Log.d(LOGTAG, "hidePointer");
        mWidgetPlacement.visible = false;
        mWidgetManager.updateWidget(this);
    }

    @Override
    public void show(@ShowFlags int aShowFlags) {
        Log.d(LOGTAG, "show: " + aShowFlags);
        if (!mWidgetPlacement.visible) {
            mWidgetPlacement.visible = true;
            mWidgetManager.addWidget(this);
        }
    }

    @Override
    public void hide(@HideFlags int aHideFlags) {
        Log.d(LOGTAG, "hide: " + aHideFlags);
        if (mWidgetPlacement.visible) {
            mWidgetPlacement.visible = false;
            if (aHideFlags == REMOVE_WIDGET) {
                mWidgetManager.removeWidget(this);
            } else {
                mWidgetManager.updateWidget(this);
            }
        }
    }

    @Override
    public void detachFromWindow() {
        Log.d(LOGTAG, "detachFromWindow");
        mWidgetPlacement.parentHandle = -1;

        mIsWindowAttached = false;
    }

    @Override
    public void attachToWindow(@NonNull WindowWidget aWindow) {
        Log.d(LOGTAG, "attachToWindow: " + aWindow);
        if (mAttachedWindow == aWindow) {
            return;
        }
        detachFromWindow();

        mAttachedWindow = aWindow;
        mWidgetPlacement.parentHandle = aWindow.getHandle();


        mIsWindowAttached = true;
    }

    Observer<ObservableBoolean> mIsVisibleObserver = aVisible -> {
        if (aVisible.get()) {
            this.show(REQUEST_FOCUS);

        } else {
            this.hide(UIWidget.KEEP_WIDGET);
        }

        mWidgetManager.updateWidget(MousePointerWidget.this);
    };
}
