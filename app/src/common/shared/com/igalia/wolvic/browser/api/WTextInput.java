package com.igalia.wolvic.browser.api;

import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

/**
 * {@code SessionTextInput} handles text input for {@code WSession} through key events or input
 * methods. It is typically used to implement certain methods in {@link android.view.View} such as
 * {@link android.view.View#onCreateInputConnection}, by forwarding such calls to corresponding
 * methods in {@code SessionTextInput}.
 *
 * <p>For full functionality, {@code SessionTextInput} requires a {@link android.view.View} to be
 * set first through {@link #setView}. When a {@link android.view.View} is not set or set to null,
 * {@code SessionTextInput} will operate in a reduced functionality mode. See {@link
 * #onCreateInputConnection} and methods in {@link WSession.TextInputDelegate} for changes in
 * behavior in this viewless mode.
 */
public interface WTextInput {
    /**
     * Get a Handler for the background input method thread. In order to use a background thread for
     * input method operations on systems prior to Nougat, first override {@code View.getHandler()}
     * for the View returning the InputConnection instance, and then call this method from the
     * overridden method.
     *
     * <p>For example:
     *
     * <pre>
     * &#64;Override
     * public Handler getHandler() {
     *     if (Build.VERSION.SDK_INT &gt;= 24) {
     *         return super.getHandler();
     *     }
     *     return getSession().getTextInput().getHandler(super.getHandler());
     * }</pre>
     *
     * @param defHandler Handler returned by the system {@code getHandler} implementation.
     * @return Handler to return to the system through {@code getHandler}.
     */
    @AnyThread
    @NonNull Handler getHandler(final @NonNull Handler defHandler);

    /**
     * Get the current {@link android.view.View} for text input.
     *
     * @return Current text input View or null if not set.
     * @see #setView(View)
     */
    @UiThread
    @Nullable View getView();

    /**
     * Set the current {@link android.view.View} for text input. The {@link android.view.View} is used
     * to interact with the system input method manager and to display certain text input UI elements.
     * See the {@code SessionTextInput} class documentation for information on viewless mode, when the
     * current {@link android.view.View} is not set or set to null.
     *
     * @param view Text input View or null to clear current View.
     * @see #getView()
     */
    @UiThread
    void setView(final @Nullable View view);

    /**
     * Get an {@link android.view.inputmethod.InputConnection} instance. In viewless mode, this method
     * still fills out the {@link android.view.inputmethod.EditorInfo} object, but the return value
     * will always be null.
     *
     * @param attrs EditorInfo instance to be filled on return.
     * @return InputConnection instance, or null if there is no active input (or if in viewless mode).
     */
    @AnyThread
    @Nullable InputConnection onCreateInputConnection(final @NonNull EditorInfo attrs);

    /**
     * Process a KeyEvent as a pre-IME event.
     *
     * @param keyCode Key code.
     * @param event KeyEvent instance.
     * @return True if the event was handled.
     */
    @UiThread
    boolean onKeyPreIme(final int keyCode, final @NonNull KeyEvent event);

    /**
     * Process a KeyEvent as a key-down event.
     *
     * @param keyCode Key code.
     * @param event KeyEvent instance.
     * @return True if the event was handled.
     */
    @UiThread
    boolean onKeyDown(final int keyCode, final @NonNull KeyEvent event);

    /**
     * Process a KeyEvent as a key-up event.
     *
     * @param keyCode Key code.
     * @param event KeyEvent instance.
     * @return True if the event was handled.
     */
    @UiThread
    boolean onKeyUp(final int keyCode, final @NonNull KeyEvent event);

    /**
     * Process a KeyEvent as a long-press event.
     *
     * @param keyCode Key code.
     * @param event KeyEvent instance.
     * @return True if the event was handled.
     */
    @UiThread
    boolean onKeyLongPress(final int keyCode, final @NonNull KeyEvent event);

    /**
     * Process a KeyEvent as a multiple-press event.
     *
     * @param keyCode Key code.
     * @param repeatCount Key repeat count.
     * @param event KeyEvent instance.
     * @return True if the event was handled.
     */
    @UiThread
    boolean onKeyMultiple(final int keyCode, final int repeatCount, final @NonNull KeyEvent event);

    /**
     * Set the current text input delegate.
     *
     * @param delegate TextInputDelegate instance or null to restore to default.
     */
    @UiThread
    void setDelegate(@Nullable final WSession.TextInputDelegate delegate);

    /**
     * Get the current text input delegate.
     *
     * @return TextInputDelegate instance or a default instance if no delegate has been set.
     */
    @UiThread
    @NonNull WSession.TextInputDelegate getDelegate();
}
