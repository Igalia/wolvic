package com.igalia.wolvic.browser.api.impl;

import android.os.Handler;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.api.WTextInput;

import org.chromium.content_public.browser.ImeAdapter;
import org.chromium.content_public.browser.InputMethodManagerWrapper;
import org.chromium.ui.base.WindowAndroid;

public class TextInputImpl implements WTextInput {
    SessionImpl mSession;
    View mView;
    WSession.TextInputDelegate mDelegate;

    public class InputMethodManagerWrapperImpl implements InputMethodManagerWrapper {
        @Override
        public void restartInput(View view) {
            // TODO : Chromium doesn't have an interface for the restarting reason, and we would
            //        consider to extend parameters if necessary.
            if (mDelegate != null)
                mDelegate.restartInput(mSession, WSession.TextInputDelegate.RESTART_REASON_FOCUS);
        }

        @Override
        public void showSoftInput(final View view, int flags, ResultReceiver resultReceiver) {
            EditorInfo outAttrs = new EditorInfo();
            view.onCreateInputConnection(outAttrs);
            if (mDelegate != null)
                mDelegate.showSoftInput(mSession, (View) mSession.getContentView());

            // We don't take content space for the keyboard, and we report back to the ImeAdapter
            // that the keyboard was always showing.
            resultReceiver.send(InputMethodManager.RESULT_UNCHANGED_SHOWN, null);
        }

        @Override
        public boolean isActive(View view) {
            return mView != null && mView == view;
        }

        @Override
        public boolean hideSoftInputFromWindow(
                IBinder windowToken, int flags, ResultReceiver resultReceiver) {
            if (mDelegate != null)
                mDelegate.hideSoftInput(mSession);
            return false;
        }

        @Override
        public void updateSelection(
                View view, int selStart, int selEnd, int candidatesStart, int candidatesEnd) {
            // Chromium does not need to convey the update selection to the delegate since
            // ImeAdapterImpl gets this notification first from the renderer and handles it inside.
        }

        @Override
        public void updateCursorAnchorInfo(View view, CursorAnchorInfo cursorAnchorInfo) {
            if (mDelegate != null)
                mDelegate.updateCursorAnchorInfo(mSession, cursorAnchorInfo);
        }

        @Override
        public void updateExtractedText(
                View view, int token, android.view.inputmethod.ExtractedText text) {
            if (mDelegate == null)
                return;
            ExtractedTextRequest request = new ExtractedTextRequest();
            request.token = token;
            mDelegate.updateExtractedText(mSession, request, text);
        }

        @Override
        public void onWindowAndroidChanged(WindowAndroid newWindowAndroid) {}

        @Override
        public void onInputConnectionCreated() {}
    }

    private InputMethodManagerWrapperImpl mInputMethodManagerWrapper;

    public TextInputImpl(SessionImpl session) {
        mSession = session;
        mInputMethodManagerWrapper = new InputMethodManagerWrapperImpl();
    }

    @NonNull
    @Override
    public Handler getHandler(@NonNull Handler defHandler) {
        ImeAdapter imeAdapter = getImeAdapter();
        if (imeAdapter == null) return defHandler;

        InputConnection ic = imeAdapter.getActiveInputConnection();
        if (ic == null) return defHandler;

        return ic.getHandler();
    }

    @Nullable
    @Override
    public View getView() {
        return mView;
    }

    @Override
    public void setView(@Nullable View view) {
        // We allows only the ContentView to adapt IME.
        View contentView = mSession.getContentView();
        if (contentView == null || contentView != view) return;

        mView = view;
        ImeAdapter imeAdapter = getImeAdapter();
        if (imeAdapter == null) return;
        imeAdapter.setInputMethodManagerWrapper(mInputMethodManagerWrapper);
    }

    @Nullable
    @Override
    public InputConnection onCreateInputConnection(@NonNull EditorInfo attrs) {
        return getImeAdapter().onCreateInputConnection(attrs);
    }

    @Override
    public boolean onKeyPreIme(int keyCode, @NonNull KeyEvent event) {
        View contentView = mSession.getContentView();
        if (contentView == null) return false;
        return contentView.onKeyPreIme(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        View contentView = mSession.getContentView();
        if (contentView == null) return false;
        return contentView.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
        View contentView = mSession.getContentView();
        if (contentView == null) return false;
        return contentView.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, @NonNull KeyEvent event) {
        View contentView = mSession.getContentView();
        if (contentView == null) return false;
        return contentView.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, @NonNull KeyEvent event) {
        View contentView = mSession.getContentView();
        if (contentView == null) return false;
        return contentView.onKeyMultiple(keyCode, repeatCount, event);
    }

    @Override
    public void setDelegate(@Nullable WSession.TextInputDelegate delegate) {
        mDelegate = delegate;
    }

    @NonNull
    @Override
    public WSession.TextInputDelegate getDelegate() {
        return mDelegate;
    }

    private ImeAdapter getImeAdapter() {
        return mSession.getTab() != null ? mSession.getTab().getImeAdapter() : null;
    }
}
