package org.mozilla.vrbrowser;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class AppExecutors {

    private final Executor mDiskIO;

    private final Executor mNetworkIO;

    private final Executor mMainThread;

    private final HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    private final ScheduledExecutorService mScheduled;

    private AppExecutors(Executor diskIO, Executor networkIO, Executor mainThread, ScheduledExecutorService scheduled) {
        this.mDiskIO = diskIO;
        this.mNetworkIO = networkIO;
        this.mMainThread = mainThread;
        this.mScheduled = scheduled;
        mBackgroundThread = new HandlerThread("BackgroundThread");
    }

    public AppExecutors() {
        this(Executors.newSingleThreadExecutor(),
                Executors.newFixedThreadPool(3),
                new MainThreadExecutor(),
                Executors.newSingleThreadScheduledExecutor());
    }

    public Executor diskIO() {
        return mDiskIO;
    }

    public Executor networkIO() {
        return mNetworkIO;
    }

    public Executor mainThread() {
        return mMainThread;
    }

    public ScheduledExecutorService scheduled() {
        return mScheduled;
    }

    public Handler backgroundThread() {
        if (!mBackgroundThread.isAlive()) {
            mBackgroundThread.start();
            mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        }
        return mBackgroundHandler;
    }

    private static class MainThreadExecutor implements Executor {
        private Handler mainThreadHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable command) {
            mainThreadHandler.post(command);
        }
    }
}
