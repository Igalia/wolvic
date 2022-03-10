package com.igalia.wolvic.browser.api;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.CancellationException;

public interface WResult<T> {
    /**
     * Construct a empty result with pending state.
     */
    static @NonNull <U> WResult<U> create() {
        return WFactory.creteResult();
    }

    /**
     * Construct a result that is completed with the specified value.
     *
     * @param value The value used to complete the newly created result.
     * @param <U> Type for the result.
     * @return The completed {@link WResult}
     */
    static @NonNull <U> WResult<U> fromValue(@Nullable final U value) {
        final WResult<U> result = create();
        result.complete(value);
        return result;
    }

    /**
     * Construct a result that is completed with the specified {@link Throwable}. May not be null.
     *
     * @param error The exception used to complete the newly created result.
     * @param <T> Type for the result if the result had been completed without exception.
     * @return The completed {@link WResult}
     */
    static @NonNull <T> WResult<T> fromException(@NonNull final Throwable error) {
        final WResult<T> result = create();
        result.completeExceptionally(error);
        return result;
    }

    /** @return a {@link WResult} that resolves to {@link WAllowOrDeny#DENY} */
    @AnyThread
    @NonNull
    static WResult<WAllowOrDeny> deny() {
        return WResult.fromValue(WAllowOrDeny.DENY);
    }

    /** @return a {@link WResult} that resolves to {@link WAllowOrDeny#ALLOW} */
    @AnyThread
    @NonNull
    static WResult<WAllowOrDeny> allow() {
        return WResult.fromValue(WAllowOrDeny.ALLOW);
    }


    /**
     * Complete the result with the specified value. IllegalStateException is thrown if the result is
     * already complete.
     *
     * @param value The value used to complete the result.
     * @throws IllegalStateException If the result is already completed.
     */
    void complete(final @Nullable T value);


    /**
     * Complete the result with the specified {@link Throwable}. IllegalStateException is thrown if
     * the result is already complete.
     *
     * @param exception The {@link Throwable} used to complete the result.
     * @throws IllegalStateException If the result is already completed.
     */
    void completeExceptionally(@NonNull final Throwable exception);

    /**
     * Convenience method for {@link #then(WResult.OnValueListener, WResult.OnExceptionListener)}.
     *
     * @param valueListener An instance of {@link WResult.OnValueListener}, called when the {@link
     *     WResult} is completed with a value.
     * @param <U> Type of the new result that is returned by the listener.
     * @return A new {@link WResult} that the listener will complete.
     */
    @NonNull <U> WResult<U> then(@NonNull final WResult.OnValueListener<T, U> valueListener);


    /**
     * Convenience method for {@link #then(WResult.OnValueListener, WResult.OnExceptionListener)}.
     *
     * @param exceptionListener An instance of {@link WResult.OnExceptionListener}, called when the {@link
     *     WResult} is completed with an {@link Exception}.
     * @param <U> Type of the new result that is returned by the listener.
     * @return A new {@link WResult} that the listener will complete.
     */
     @NonNull <U> WResult<U> exceptionally(@NonNull final WResult.OnExceptionListener<U> exceptionListener);



    /**
     * Adds listeners to be called when the {@link WResult} is completed either with a value or
     * {@link Throwable}. Listeners will be invoked on the {@link Looper} returned from {@link
     * #getLooper()}. If null, this method will throw {@link IllegalThreadStateException}.
     *
     * <p>If the result is already complete when this method is called, listeners will be invoked in a
     * future {@link Looper} iteration.
     *
     * @param valueListener An instance of {@link WResult.OnValueListener}, called when the {@link
     *     WResult} is completed with a value.
     * @param exceptionListener An instance of {@link WResult.OnExceptionListener}, called when the {@link
     *     WResult} is completed with an {@link Throwable}.
     * @param <U> Type of the new result that is returned by the listeners.
     * @return A new {@link WResult} that the listeners will complete.
     */
    @NonNull <U> WResult<U> then(
            @Nullable final WResult.OnValueListener<T, U> valueListener,
            @Nullable final WResult.OnExceptionListener<U> exceptionListener);


    /**
     * Attempts to cancel the operation associated with this result.
     *
     * <p>If this result has a {@link WResult.CancellationDelegate} attached via {@link
     * #setCancellationDelegate(WResult.CancellationDelegate)}, the return value will be the result of calling
     * {@link WResult.CancellationDelegate#cancel()} on that instance. Otherwise, if this result is chained to
     * another result (via return value from {@link WResult.OnValueListener}), we will walk up the chain until
     * a CancellationDelegate is found and run it. If no CancellationDelegate is found, a result
     * resolving to "false" will be returned.
     *
     * <p>If this result is already complete, the returned result will always resolve to false.
     *
     * <p>If the returned result resolves to true, this result will be completed with a {@link
     * CancellationException}.
     *
     * @return A WResult resolving to a boolean indicating success or failure of the cancellation
     *     attempt.
     */
    @NonNull
    WResult<Boolean> cancel();

    /**
     * Sets the instance of {@link WResult.CancellationDelegate} that will be invoked by {@link #cancel()}.
     *
     * @param delegate an instance of CancellationDelegate.
     */
    void setCancellationDelegate(final @Nullable WResult.CancellationDelegate delegate);

    /**
     * An interface used to deliver values to listeners of a {@link WResult}
     *
     * @param <T> Type of the value delivered via {@link #onValue(Object)}
     * @param <U> Type of the value for the result returned from {@link #onValue(Object)}
     */
    interface OnValueListener<T, U> {
        /**
         * Called when a {@link WResult} is completed with a value. Will be called on the same
         * thread where the IResult was created or on the {@link Handler} provided via {@link
         * #withHandler(Handler)}.
         *
         * @param value The value of the {@link WResult}
         * @return Result used to complete the next result in the chain. May be null.
         * @throws Throwable Exception used to complete next result in the chain.
         */
        @AnyThread
        @Nullable
        WResult<U> onValue(@Nullable T value) throws Throwable;
    }

    interface OnExceptionListener<V> {
        /**
         * Called when a {@link WResult} is completed with an exception. Will be called on the same
         * thread where the IResult was created or on the {@link Handler} provided via {@link
         * #withHandler(Handler)}.
         *
         * @param exception Exception that completed the result.
         * @return Result used to complete the next result in the chain. May be null.
         * @throws Throwable Exception used to complete next result in the chain.
         */
        @AnyThread
        @Nullable
        WResult<V> onException(@NonNull Throwable exception) throws Throwable;
    }


    /** Interface used to delegate cancellation operations for a {@link WResult}. */
    @AnyThread
    interface CancellationDelegate {

        /**
         * This method should attempt to cancel the in-progress operation for the result to which this
         * instance was attached. See {@link WResult#cancel()} for more details.
         *
         * @return A {@link WResult} resolving to "true" if cancellation was successful, "false"
         *     otherwise.
         */
        default @NonNull
        WResult<Boolean> cancel() {
            return WResult.fromValue(false);
        }
    }
}
