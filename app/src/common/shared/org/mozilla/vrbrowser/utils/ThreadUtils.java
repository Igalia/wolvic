package org.mozilla.vrbrowser.utils;

import android.os.Handler;
import android.os.Looper;

public class ThreadUtils extends Thread {
    private static final Handler sUiHandler = new Handler(Looper.getMainLooper());

    private static final String LOOPER_NAME = "VRBBackgroundThread";

    // Guarded by 'ThreadUtils.class'.
    private static Handler mBackgroundHandler;
    private static Thread mBackgroundThread;

    // The initial Runnable to run on the new mBackgroundThread. Its purpose
    // is to avoid us having to wait for the new mBackgroundThread to start.
    private Runnable mInitialRunnable;

    // Singleton, so private constructor.
    private ThreadUtils(final Runnable initialRunnable) {
        mInitialRunnable = initialRunnable;
    }

    @Override
    public void run() {
        setName(LOOPER_NAME);
        Looper.prepare();

        synchronized (ThreadUtils.class) {
            mBackgroundHandler = new Handler();
            ThreadUtils.class.notifyAll();
        }

        if (mInitialRunnable != null) {
            mInitialRunnable.run();
            mInitialRunnable = null;
        }

        Looper.loop();
    }

    private static void startThread(final Runnable initialRunnable) {
        mBackgroundThread = new ThreadUtils(initialRunnable);
        mBackgroundThread.setDaemon(true);
        mBackgroundThread.start();
    }

    // Get a Handler for a looper mBackgroundThread, or create one if it doesn't yet exist.
    /*package*/ static synchronized Handler getHandler() {
        if (mBackgroundThread == null) {
            startThread(null);
        }

        while (mBackgroundHandler == null) {
            try {
                ThreadUtils.class.wait();
            } catch (final InterruptedException e) {
            }
        }
        return mBackgroundHandler;
    }

    public static synchronized void postToBackgroundThread(final Runnable runnable) {
        if (mBackgroundThread == null) {
            startThread(runnable);
            return;
        }
        getHandler().post(runnable);
    }

    public static void postToUiThread(final Runnable runnable) {
        sUiHandler.post(runnable);
    }

    public static void postDelayedToUiThread(final Runnable runnable, final long timeout) {
        sUiHandler.postDelayed(runnable, timeout);
    }

    public static void removeCallbacksFromUiThread(final Runnable runnable) {
        sUiHandler.removeCallbacks(runnable);
    }
}
