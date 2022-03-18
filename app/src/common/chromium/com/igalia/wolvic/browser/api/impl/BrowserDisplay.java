package com.igalia.wolvic.browser.api.impl;

import android.content.Context;
import android.graphics.Point;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.igalia.wolvic.browser.SettingsStore;
import org.chromium.weblayer.Browser;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class BrowserDisplay extends FrameLayout {
    protected FragmentContainerView mFragmentContainer;
    protected Fragment mBrowserFragment;
    private FragmentManager mFragmentManager;
    protected Browser mBrowser;
    protected boolean mAcquired;
    private Object mContentViewRenderViewListener;
    private Method mMethodSurfaceCreated;
    private Method mMethodSurfaceChanged;
    private Method mMethodSurfaceDestroyed;
    private Method mMethodSurfaceRedrawNeededAsync;

    public BrowserDisplay(Context context) {
        super(context);
    }

    public void attach(@NonNull FragmentManager fragmentManager, @NonNull ViewGroup parentContainer, @NonNull Fragment fragment) {
        if (mFragmentContainer != null) {
            throw new IllegalStateException("Already attached");
        }
        mFragmentManager = fragmentManager;

        // Create the container for the weblayer fragment. Android recommends using FragmentContainerView.
        mFragmentContainer = new FragmentContainerView(getContext());
        mFragmentContainer.setId(View.generateViewId());

        // Add the weblayer container to the activity content. Use default width & height for now.
        // It can be resized later in surfaceChanged.
        SettingsStore settings = SettingsStore.getInstance(getContext());
        parentContainer.addView(this, new ViewGroup.LayoutParams(settings.getWindowWidth(), settings.getWindowHeight()));
        this.addView(mFragmentContainer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));


        // Retain the fragment instance to simulate the behavior of
        // external embedders (note that if this is changed, then WebLayer Shell should handle
        // rotations and resizes itself via its manifest, as otherwise the user loses all state
        // when the shell is rotated in the foreground).
        fragment.setRetainInstance(true);

        // Attach fragment to mFragmentContainer
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(mFragmentContainer.getId(), fragment);

        // Note the commitNow() instead of commit(). We want the fragment to get attached to
        // activity synchronously, so we can use all the functionality immediately. Otherwise we'd
        // have to wait until the commit is executed.
        transaction.commitNow();

        mBrowserFragment = fragment;
        mBrowser = Browser.fromFragment(fragment);
        assert mBrowser != null;

        Point point = getDisplaySize();
        mBrowser.setMinimumSurfaceSize(point.x, point.y);

        overrideFragmentSurface();
    }

    private void overrideFragmentSurface() {
        // ContentViewRenderView is the class in Chromium that manages the Surface that is used by
        // the chromium compositor and the WebLayer BrowserFragment.
        View view = mBrowserFragment.getView();
        assert view != null;

        // It's a private class, we need to use reflection for now. Ideally we can submit some
        // public surface override API upstream at some time.
        try {
            Object surfaceData = getPrivateField(view, "mRequested");
            SurfaceView surfaceView = (SurfaceView)getPrivateField(surfaceData, "mSurfaceView");
            SurfaceHolder.Callback2 callback = (SurfaceHolder.Callback2)getPrivateField(surfaceData, "mSurfaceCallback");

            // Prevent ContentViewRenderView from rendering into it's internal SurfaceView.
            surfaceView.getHolder().removeCallback(callback);

            // This listener is used to dispatch our Surface callbacks.
            mContentViewRenderViewListener = getPrivateField(callback, "mListener");
            mMethodSurfaceCreated = mContentViewRenderViewListener.getClass().getDeclaredMethod("surfaceCreated");
            mMethodSurfaceChanged = mContentViewRenderViewListener.getClass().getDeclaredMethod("surfaceChanged", Surface.class, boolean.class, int.class, int.class, boolean.class);
            mMethodSurfaceDestroyed = mContentViewRenderViewListener.getClass().getDeclaredMethod("surfaceDestroyed", boolean.class);
            mMethodSurfaceRedrawNeededAsync = mContentViewRenderViewListener.getClass().getDeclaredMethod("surfaceRedrawNeededAsync", Runnable.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Object getPrivateField(Object instance, String name) throws NoSuchFieldException, IllegalAccessException {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    public Browser getBrowser() {
        return mBrowser;
    }

    public boolean isAcquired() {
        return mAcquired;
    }

    public void setAcquired(boolean value) {
        mAcquired = value;
    }

    public void surfaceChanged(@NonNull Surface surface, int width, int height, @NonNull Runnable firstCompositeCallback) {
        mFragmentContainer.setLayoutParams(new FrameLayout.LayoutParams(width, height));
        try {
            // Dispatch onSurfaceCreated
            mMethodSurfaceCreated.invoke(mContentViewRenderViewListener);

            // Dispatch onSurfaceChanged
            final boolean canBeUsedWithSurfaceControl = false;
            final boolean transparentBackground = false;
            mMethodSurfaceChanged.invoke(mContentViewRenderViewListener, surface, canBeUsedWithSurfaceControl, width, height, transparentBackground);

            // Dispatch SurfaceRedrawNeededAsync
            postDelayed((Runnable) () -> {
                try {
                    mMethodSurfaceRedrawNeededAsync.invoke(mContentViewRenderViewListener, firstCompositeCallback);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }, 100);


        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void surfaceDestroyed() {
        try {
            // Dispatch onSurfaceChanged
            final boolean cacheBackBuffer = false;
            mMethodSurfaceDestroyed.invoke(mContentViewRenderViewListener, cacheBackBuffer);
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private Point getDisplaySize() {
        Point point = new Point();
        WindowManager windowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        display.getRealSize(point);
        return point;
    }
}
