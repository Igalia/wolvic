package com.igalia.wolvic.browser.api.impl;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SimpleArrayMap;

import com.igalia.wolvic.browser.api.WResult;

import java.util.ArrayList;
import java.util.concurrent.CancellationException;

public class ResultImpl<T> implements WResult<T> {
    private ResultImpl.Dispatcher mDispatcher;
    private boolean mComplete;
    private T mValue;
    private Throwable mError;
    private boolean mIsUncaughtError;
    private SimpleArrayMap<ResultImpl.Dispatcher, ArrayList<Runnable>> mListeners = new SimpleArrayMap<>();
    private CancellationDelegate mCancellationDelegate;
    private WResult<?> mParent;


    public ResultImpl() {
        if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
            mDispatcher = new HandlerDispatcher(new Handler(Looper.getMainLooper()));
        } else if (Looper.myLooper() != null) {
            mDispatcher = new HandlerDispatcher(new Handler());
        } else {
            mDispatcher = new DirectDispatcher();
        }
    }

    @Override
    public synchronized void complete(@Nullable T value) {
        mValue = value;
        mComplete = true;

        dispatch();
        notifyAll();
    }

    @Override
    public synchronized void completeExceptionally(@NonNull Throwable exception) {
        mError = exception;
        mComplete = true;

        dispatch();
        notifyAll();
    }

    @NonNull
    @Override
    public WResult<Boolean> cancel() {
        if (haveValue() || haveError()) {
            return WResult.fromValue(false);
        }

        if (mCancellationDelegate != null) {
            return mCancellationDelegate
                    .cancel()
                    .then(
                            value -> {
                                if (value) {
                                    try {
                                        this.completeExceptionally(new CancellationException());
                                    } catch (final IllegalStateException e) {
                                        e.printStackTrace();
                                    }
                                }
                                return WResult.fromValue(value);
                            });
        }

        if (mParent != null) {
            return mParent.cancel();
        }

        return WResult.fromValue(false);
    }

    @Override
    public void setCancellationDelegate(@Nullable CancellationDelegate delegate) {
        mCancellationDelegate = delegate;
    }

    @NonNull
    @Override
    public synchronized <U> WResult<U> then(@Nullable OnValueListener<T, U> valueListener, @Nullable OnExceptionListener<U> exceptionListener) {
        return thenInternal(mDispatcher, valueListener, exceptionListener);
    }

    @NonNull
    @Override
    public synchronized <U> WResult<U> exceptionally(@NonNull OnExceptionListener<U> exceptionListener) {
        return then(null, exceptionListener);
    }

    @NonNull
    @Override
    public synchronized <U>WResult<U> then(@NonNull OnValueListener<T, U> valueListener) {
        return then(valueListener, null);
    }

    private  void completeFrom(final @Nullable WResult<T> aOther) {
        if (aOther == null) {
            complete(null);
            return;
        }

        ResultImpl<T> other = (ResultImpl<T>)aOther;
        this.mCancellationDelegate = other.mCancellationDelegate;
        other.thenInternal(
                ResultImpl.DirectDispatcher.sInstance,
                () -> {
                    if (other.haveValue()) {
                        complete(other.mValue);
                    } else {
                        mIsUncaughtError = other.mIsUncaughtError;
                        completeExceptionally(other.mError);
                    }
                });
    }


    private @NonNull <U> WResult<U> thenInternal(
            @NonNull final ResultImpl.Dispatcher dispatcher,
            @Nullable final WResult.OnValueListener<T, U> valueListener,
            @Nullable final WResult.OnExceptionListener<U> exceptionListener) {
        if (valueListener == null && exceptionListener == null) {
            throw new IllegalArgumentException("At least one listener should be non-null");
        }

        final ResultImpl<U> result = new ResultImpl<U>();
        result.mParent = this;
        thenInternal(
                dispatcher,
                () -> {
                    try {
                        if (haveValue()) {
                            result.completeFrom(valueListener != null ? valueListener.onValue(mValue) : null);
                        } else if (!haveError()) {
                            // Listener called without completion?
                            throw new AssertionError();
                        } else if (exceptionListener != null) {
                            result.completeFrom(exceptionListener.onException(mError));
                        } else {
                            result.mIsUncaughtError = mIsUncaughtError;
                            result.completeExceptionally(mError);
                        }
                    } catch (final Throwable e) {
                        if (!result.mComplete) {
                            result.mIsUncaughtError = true;
                            result.completeExceptionally(e);
                        } else if (e instanceof RuntimeException) {
                            // This should only be UncaughtException, but we rethrow all RuntimeExceptions
                            // to avoid squelching logic errors in GeckoResult itself.
                            throw (RuntimeException) e;
                        }
                    }
                });
        return result;
    }

    private synchronized void thenInternal(@NonNull final ResultImpl.Dispatcher dispatcher, @NonNull final Runnable listener) {
        if (mComplete) {
            dispatcher.dispatch(listener);
        } else {
            if (!mListeners.containsKey(dispatcher)) {
                mListeners.put(dispatcher, new ArrayList<>(1));
            }
            mListeners.get(dispatcher).add(listener);
        }
    }

    private void dispatch() {
        if (!mComplete) {
            throw new IllegalStateException("Cannot dispatch unless result is complete");
        }

        if (mListeners.isEmpty()) {
            if (mIsUncaughtError) {
                // We have no listeners to forward the uncaught exception to;
                // rethrow the exception to make it visible.
                throw new ResultImpl.UncaughtException(mError);
            }
            return;
        }

        if (mDispatcher == null) {
            throw new AssertionError("Shouldn't have listeners with null dispatcher");
        }

        for (int i = 0; i < mListeners.size(); ++i) {
            final ResultImpl.Dispatcher dispatcher = mListeners.keyAt(i);
            final ArrayList<Runnable> jobs = mListeners.valueAt(i);
            dispatcher.dispatch(
                    () -> {
                        for (final Runnable job : jobs) {
                            job.run();
                        }
                    });
        }
        mListeners.clear();
    }

    private boolean haveValue() {
        return mComplete && mError == null;
    }

    private boolean haveError() {
        return mComplete && mError != null;
    }


    private interface Dispatcher {
        void dispatch(Runnable r);
    }

    private static class HandlerDispatcher implements ResultImpl.Dispatcher {
        HandlerDispatcher(final Handler h) {
            mHandler = h;
        }

        public void dispatch(final Runnable r) {
            mHandler.post(r);
        }

        @Override
        public boolean equals(final Object other) {
            if (!(other instanceof ResultImpl.HandlerDispatcher)) {
                return false;
            }
            return mHandler.equals(((ResultImpl.HandlerDispatcher) other).mHandler);
        }

        @Override
        public int hashCode() {
            return mHandler.hashCode();
        }

        Handler mHandler;
    }

    private static class DirectDispatcher implements ResultImpl.Dispatcher {
        public void dispatch(final Runnable r) {
            r.run();
        }

        static ResultImpl.DirectDispatcher sInstance = new ResultImpl.DirectDispatcher();

        private DirectDispatcher() {}
    }

    public static final class UncaughtException extends RuntimeException {
        @SuppressWarnings("checkstyle:javadocmethod")
        public UncaughtException(final Throwable cause) {
            super(cause);
        }
    }
}
