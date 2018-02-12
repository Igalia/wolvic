package org.mozilla.vrbrowser;

import com.htc.vr.BuildConfig;
import com.htc.vr.sdk.VRActivity;

import android.content.res.AssetManager;
import android.graphics.SurfaceTexture;
import android.os.Bundle;

public class VRBrowserActivity extends VRActivity {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    public VRBrowserActivity() {
        super.setUsingRenderBaseActivity(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        queueRunnable(new Runnable() {
            @Override
            public void run() {
                initializeJava(getAssets());
            }
        });
    }

    private void setSurfaceTexture(SurfaceTexture aSurface, int aWidth, int aHeight) {

    }

    private native void queueRunnable(Runnable aRunnable);
    private native void initializeJava(AssetManager aAssets);
}
