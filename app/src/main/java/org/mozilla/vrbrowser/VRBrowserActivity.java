package org.mozilla.vrbrowser;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.graphics.SurfaceTexture;
import android.view.Surface;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import org.mozilla.gecko.GeckoSession;

import android.os.Bundle;

public class VRBrowserActivity extends Activity {
    static String LOGTAG = "VRBrowser";
    static final String DEFAULT_URL = "https://mozvr.com";
    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    private GLSurfaceView mView;
    private static GeckoSession mSession;
    private static Surface mBrowserSurface;

    private final Runnable activityCreatedRunnable = new Runnable() {
        @Override
        public void run() {
            activityCreated(getAssets());
        }
    };

    private final Runnable activityDestroyedRunnable = new Runnable() {
        @Override
        public void run() {
            activityDestroyed();
        }
    };

    private final Runnable activityPausedRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (this) {
                activityPaused();
                notifyAll();
            }
        }
    };

    private final Runnable activityResumedRunnable = new Runnable() {
        @Override
        public void run() {
            activityResumed();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(LOGTAG,"in onCreate");
        super.onCreate(savedInstanceState);
        if (mSession == null) {
            mSession = new GeckoSession();
        }
        mView = new GLSurfaceView(this);
        mView.setEGLContextClientVersion(3);
        mView.setEGLConfigChooser(8, 8, 8, 0, 16, 0);
        mView.setPreserveEGLContextOnPause(true);
        mView.setRenderer(
                new GLSurfaceView.Renderer() {
                    @Override
                    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                        Log.e(LOGTAG, "In onSurfaceCreated");
                        initGL();
                    }

                    @Override
                    public void onSurfaceChanged(GL10 gl, int width, int height) {
                        Log.e(LOGTAG, "In onSurfaceChanged");
                        updateGL(width, height);
                    }

                    @Override
                    public void onDrawFrame(GL10 gl) {
                        //Log.e(LOGTAG, "in onDrawFrame");
                        drawGL();
                    }
                });
        mView.queueEvent(activityCreatedRunnable);
        setContentView(mView);
        loadFromIntent(getIntent());
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        Log.e(LOGTAG,"In onNewIntent");
        super.onNewIntent(intent);
        setIntent(intent);
        final String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            if (intent.getData() != null) {
                loadFromIntent(intent);
            }
        }
    }

    private void loadFromIntent(final Intent intent) {
        final Uri uri = intent.getData();
//        if (intent.hasCategory(Constants.DAYDREAM_CATEGORY)) {
//            Log.e(LOGTAG,"Intent has DAYDREAM_CATEGORY");
//            return;
//        }
        Log.e(LOGTAG, "Load URI from intent: " + (uri != null ? uri.toString() : DEFAULT_URL));
        String uriValue = (uri != null ? uri.toString() : DEFAULT_URL);
        mSession.loadUri(uriValue);
    }

    @Override
    protected void onPause() {
        Log.e(LOGTAG, "In onPause");
        synchronized (activityPausedRunnable) {
            mView.queueEvent(activityPausedRunnable);
            try {
                activityPausedRunnable.wait();
            } catch(InterruptedException e) {

            }
        }
        mView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        Log.e(LOGTAG, "in onResume");
        super.onResume();
        mView.onResume();
        mView.queueEvent(activityResumedRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mView.queueEvent(activityDestroyedRunnable);
    }

    private void setSurfaceTexture(String aName, final SurfaceTexture aTexture, final int aWidth, final int aHeight) {
        runOnUiThread(new Runnable() {
            public void run() {
                createBrowser(aTexture, aWidth, aHeight);
            }
        });
    }

    private void createBrowser(SurfaceTexture aTexture, int aWidth, int aHeight) {
        if (aTexture != null) {
            Log.e(LOGTAG,"In createBrowser");
            aTexture.setDefaultBufferSize(aWidth, aHeight);
            mBrowserSurface = new Surface(aTexture);
            mSession.acquireDisplay().surfaceChanged(mBrowserSurface, aWidth, aHeight);
            mSession.openWindow(this);
        }
    }

    private native void activityCreated(Object aAssetManager);
    private native void activityPaused();
    private native void activityResumed();
    private native void activityDestroyed();
    private native void initGL();
    private native void updateGL(int width, int height);
    private native void drawGL();
}
