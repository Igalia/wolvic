package com.igalia.wolvic.browser.api.impl;

import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.api.WTextInput;
import com.wpe.wpeview.WPEView;

public class TextInputImpl implements WTextInput {
    SessionImpl mSession;
    View mView;
    WSession.TextInputDelegate mDelegate;

    public TextInputImpl(SessionImpl session) {
        mSession = session;
    }

    @NonNull
    @Override
    public Handler getHandler(@NonNull Handler defHandler) {
        return null;
    }

    @Nullable
    @Override
    public View getView() {
        return null;
    }

    @Override
    public void setView(@Nullable View view) {
        mView = view;
    }

    @Nullable
    @Override
    public InputConnection onCreateInputConnection(@NonNull EditorInfo attrs) {
        return mView.onCreateInputConnection(attrs);
    }

    @Override
    public boolean onKeyPreIme(int keyCode, @NonNull KeyEvent event) {
        @Nullable View view = getViewForEvents();
        if (view == null) {
            return false;
        }
        return view.onKeyPreIme(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        @Nullable View view = getViewForEvents();
        if (view == null) {
            return false;
        }
        return view.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
        @Nullable View view = getViewForEvents();
        if (view == null) {
            return false;
        }
        return view.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, @NonNull KeyEvent event) {
        @Nullable View view = getViewForEvents();
        if (view == null) {
            return false;
        }
        return view.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, @NonNull KeyEvent event) {
        @Nullable View view = getViewForEvents();
        if (view == null) {
            return false;
        }
        return view.onKeyMultiple(keyCode, repeatCount, event);
    }

    @Override
    public void setDelegate(@Nullable WSession.TextInputDelegate delegate) {
        // TODO: Implement
        mDelegate = delegate;
    }

    @NonNull
    @Override
    public WSession.TextInputDelegate getDelegate() {
        return mDelegate;
    }

    private @Nullable
    View getViewForEvents() {
        if (mSession == null) {
            return null;
        }
        return mSession.mWPEView.getGfxView();
    }
}
