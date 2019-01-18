package org.mozilla.servo;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.Surface;

import org.mozilla.geckoview.GeckoDisplay;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.servoview.Servo;
import org.mozilla.servoview.ServoSurface;

public class ServoSession extends GeckoSession {
    private static final String LOGTAG = "ServoSession";
    private ServoSurface mServo;
    private Activity mActivity;

    private int mWidth;
    private int mHeight;
    private ServoDisplay mDisplay;
    private ServoPanZoomController mPanZoomController;
    private boolean mIsOpen = false;
    private String mUrl = "about:blank";

    private ProgressDelegate mProgressDelegate;
    private NavigationDelegate mNavigationDelegate;
    private ContentDelegate mContentDelegate;

    public ServoSession(Context aContext) {
        Log.d(LOGTAG, "ServoSession()");
        mActivity = (Activity) aContext;
    }

    public void onSurfaceReady(Surface surface, int left, int top, int width, int height) {
        Log.d(LOGTAG, "onSurfaceReady()");
        if (mServo == null) {
            mWidth = width;
            mHeight = height;
            mServo = new ServoSurface(surface, width, height);
            mServo.setClient(new ServoCallbacks());
            mServo.setActivity(mActivity);
            mServo.runLoop();

        } else {
            Log.w(LOGTAG, "onSurfaceReady called twice");
        }
    }

    public void onSurfaceDestroyed() {
      Log.d(LOGTAG, "onSurfaceDestroyed");
      // FIXME: Pause compositor.
      // See: https://github.com/servo/servo/issues/21860
    }

    @Override
    public ServoPanZoomController getPanZoomController() {
        if (mPanZoomController == null) {
            mPanZoomController = new ServoPanZoomController(this);
        }
        return mPanZoomController;
    }

    @Override
    public ProgressDelegate getProgressDelegate() {
        return mProgressDelegate;
    }

    @Override
    public void setProgressDelegate(ProgressDelegate delegate) {
        mProgressDelegate = delegate;
    }

    @Override
    public NavigationDelegate getNavigationDelegate() {
        return mNavigationDelegate;
    }

    @Override
    public void setNavigationDelegate(NavigationDelegate delegate) {
        mNavigationDelegate = delegate;
    }

    @Override
    public ContentDelegate getContentDelegate() {
        return mContentDelegate;
    }

    @Override
    public void setContentDelegate(ContentDelegate delegate) {
        mContentDelegate = delegate;
    }

    @Override
    public void loadUri(String uri, String referrer, int flags) {
        Log.d(LOGTAG, "loadUri()");
        mUrl = uri;
        mServo.loadUri(uri);
    }

    @Override
    public boolean isOpen() {
        Log.d(LOGTAG, "isOpen()");
        return mIsOpen;
    }

    @Override
    public void open(GeckoRuntime runtime) {
        Log.d(LOGTAG, "open()");
        mIsOpen = true;
    }

    @Override
    public void close() {
        Log.d(LOGTAG, "close()");
        mServo.shutdown();
        mServo = null;
        mIsOpen = false;
        mUrl = "about:blank";
    }

    @Override
    public void reload() {
        Log.d(LOGTAG, "reload()");
        mServo.reload();
    }

    @Override
    public void stop() {
        Log.d(LOGTAG, "stop()");
        mServo.stop();
    }

    @Override
    public void goBack() {
        Log.d(LOGTAG, "goBack()");
        mServo.goBack();
    }

    @Override
    public void goForward() {
        Log.d(LOGTAG, "goForward()");
        mServo.goForward();
    }

    @Override
    public void setActive(boolean active) {
        Log.d(LOGTAG, "setActive(" + active + ")");
    }

    @Override
    public void getSurfaceBounds(final Rect rect) {
        rect.set(0, 0, mWidth, mHeight);
    }

    @Override
    public GeckoDisplay acquireDisplay() {
        Log.d(LOGTAG, "acquireDisplay()");
        if (mDisplay == null) {
            mDisplay = new ServoDisplay(this);
        }
        return mDisplay;
    }

    @Override
    public void releaseDisplay(final GeckoDisplay display) {
        Log.d(LOGTAG, "releaseDisplay()");
        if (display != mDisplay) {
            throw new IllegalArgumentException("Display not attached");
        }

        mDisplay = null;
    }


    public void click(final int x, final int y) {
        Log.d(LOGTAG, "click()");
        mServo.click(x, y);
    }

    public void scrollStart(final int deltaX, final int deltaY, final int x, final int y) {
        mServo.scrollStart(deltaX, deltaY, x, y);
    }

    public void scroll(final int deltaX, final int deltaY, final int x, final int y) {
        mServo.scroll(deltaX, deltaY, x, y);
    }

    public void scrollEnd(final int deltaX, final int deltaY, final int x, final int y) {
        mServo.scrollEnd(deltaX, deltaY, x, y);
    }

    class ServoCallbacks implements Servo.Client {

        public void onLoadStarted() {
            Log.d(LOGTAG, "ServoCallback::onLoadStarted()");
            getProgressDelegate().onPageStart(ServoSession.this, mUrl);
        }

        public void onLoadEnded() {
            Log.d(LOGTAG, "ServoCallback::onLoadEnded()");
            getProgressDelegate().onPageStop(ServoSession.this, true);
        }

        public void onTitleChanged(final String title) {
            Log.d(LOGTAG, "ServoCallback::onTitleChanged(" + title + ")");
            getContentDelegate().onTitleChange(ServoSession.this, title);
        }

        public void onUrlChanged(final String url) {
            Log.d(LOGTAG, "ServoCallback::onUrlChanged(" + url + ")");
            mUrl = url;
            getNavigationDelegate().onLocationChange(ServoSession.this, url);
        }

        public void onHistoryChanged(final boolean canGoBack, final boolean canGoForward) {
            Log.d(LOGTAG, "ServoCallback::onHistoryChanged()");
            getNavigationDelegate().onCanGoBack(ServoSession.this, canGoBack);
            getNavigationDelegate().onCanGoForward(ServoSession.this, canGoForward);
        }

        public void onRedrawing(boolean redrawing) {
            Log.d(LOGTAG, "ServoCallback::onRedrawing: " + redrawing);
        }
    }
}
