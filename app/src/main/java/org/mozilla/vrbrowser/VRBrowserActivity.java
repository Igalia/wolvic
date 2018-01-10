package org.mozilla.vrbrowser;

import android.app.Activity;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.InputStream;
import java.io.IOException;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
//import com.google.vr.ndk.base.AndroidCompat;
//import com.google.vr.ndk.base.GvrLayout;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.os.Bundle;

public class VRBrowserActivity extends Activity {
    static String LOGTAG = "VRBrowser";
    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    private GLSurfaceView mView;

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
            activityPaused();
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
        super.onCreate(savedInstanceState);

        mView = new GLSurfaceView(this);
        mView.setEGLContextClientVersion(3);
        mView.setEGLConfigChooser(8, 8, 8, 0, 16, 0);
        mView.setPreserveEGLContextOnPause(true);
        mView.setRenderer(
                new GLSurfaceView.Renderer() {
                    @Override
                    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                        initGL();
                    }

                    @Override
                    public void onSurfaceChanged(GL10 gl, int width, int height) {
                        updateGL(width, height);
                    }

                    @Override
                    public void onDrawFrame(GL10 gl) {
                        drawGL();
                    }
                });
        mView.queueEvent(activityCreatedRunnable);
        setContentView(mView);
    }

    @Override
    protected void onPause() {
        mView.queueEvent(activityPausedRunnable);
        mView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mView.onResume();
        mView.queueEvent(activityResumedRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mView.queueEvent(activityDestroyedRunnable);
    }

    private native void activityCreated(Object aAssetManager);
    private native void activityPaused();
    private native void activityResumed();
    private native void activityDestroyed();
    private native void initGL();
    private native void updateGL(int width, int height);
    private native void drawGL();
}
