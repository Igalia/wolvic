package com.igalia.wolvic.browser.api.impl;

import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.api.WTextInput;

import org.mozilla.geckoview.GeckoSession;

public class TextInputImpl implements WTextInput {
    SessionImpl mSession;
    WSession.TextInputDelegate mDelegate;

    public TextInputImpl(SessionImpl session) {
        mSession = session;
    }

    @NonNull
    @Override
    public Handler getHandler(@NonNull Handler defHandler) {
        return getTextInput().getHandler(defHandler);
    }

    @Nullable
    @Override
    public View getView() {
        return getTextInput().getView();
    }

    @Override
    public void setView(@Nullable View view) {
        getTextInput().setView(view);
    }

    @Nullable
    @Override
    public InputConnection onCreateInputConnection(@NonNull EditorInfo attrs) {
        return getTextInput().onCreateInputConnection(attrs);
    }

    @Override
    public boolean onKeyPreIme(int keyCode, @NonNull KeyEvent event) {
        return getTextInput().onKeyPreIme(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        return getTextInput().onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
        return getTextInput().onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, @NonNull KeyEvent event) {
        return getTextInput().onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, @NonNull KeyEvent event) {
        return getTextInput().onKeyMultiple(keyCode, repeatCount, event);
    }

    @Override
    public void setDelegate(@Nullable WSession.TextInputDelegate delegate) {
        if (mDelegate == delegate) {
            return;
        }
        mDelegate = delegate;
        if (delegate == null) {
            getTextInput().setDelegate(null);
            return;
        }

        getTextInput().setDelegate(new GeckoSession.TextInputDelegate() {
            @Override
            public void restartInput(@NonNull GeckoSession session, int reason) {
                mDelegate.restartInput(mSession, reason);
            }

            @Override
            public void showSoftInput(@NonNull GeckoSession session) {
                mDelegate.showSoftInput(mSession, null);
            }

            @Override
            public void hideSoftInput(@NonNull GeckoSession session) {
                mDelegate.hideSoftInput(mSession);
            }

            @Override
            public void updateSelection(@NonNull GeckoSession session, int selStart, int selEnd, int compositionStart, int compositionEnd) {
                mDelegate.updateSelection(mSession, selStart, selEnd, compositionStart, compositionEnd);
            }

            @Override
            public void updateExtractedText(@NonNull GeckoSession session, @NonNull ExtractedTextRequest request, @NonNull ExtractedText text) {
                mDelegate.updateExtractedText(mSession, request, text);
            }

            @Override
            public void updateCursorAnchorInfo(@NonNull GeckoSession session, @NonNull CursorAnchorInfo info) {
                mDelegate.updateCursorAnchorInfo(mSession, info);
            }
        });
    }

    @NonNull
    @Override
    public WSession.TextInputDelegate getDelegate() {
        return mDelegate;
    }

    private org.mozilla.geckoview.SessionTextInput getTextInput() {
        return mSession.getGeckoSession().getTextInput();
    }
}
