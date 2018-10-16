/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PlatformActivity extends Activity {
    static String LOGTAG = "VRB";
    static final float ROTATION = 0.098174770424681f;

    public static boolean filterPermission(final String aPermission) {
        return false;
    }

    private GLSurfaceView mView;

    private final Runnable activityDestroyedRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (this) {
                activityDestroyed();
                notifyAll();
            }
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
        Log.e(LOGTAG, "PlatformActivity onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.noapi_layout);
        mView = findViewById(R.id.gl_view);
        mView.setEGLContextClientVersion(3);
        mView.setEGLConfigChooser(8, 8, 8, 0, 16, 0);
        //mView.setPreserveEGLContextOnPause(true);

        mView.setRenderer(
                new GLSurfaceView.Renderer() {
                    @Override
                    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                        Log.e(LOGTAG, "In onSurfaceCreated");
                        activityCreated(getAssets());
                    }

                    @Override
                    public void onSurfaceChanged(GL10 gl, int width, int height) {
                        Log.e(LOGTAG, "In onSurfaceChanged");
                        updateViewport(width, height);
                    }

                    @Override
                    public void onDrawFrame(GL10 gl) {
                        drawGL();
                    }
                });
        setupUI();
    }

    @Override
    public boolean onTouchEvent(MotionEvent aEvent) {
        if (aEvent.getActionIndex() != 0) {
            Log.e(LOGTAG,"aEvent.getActionIndex()=" + aEvent.getActionIndex());
            return false;
        }

        int action = aEvent.getAction();
        boolean down = false;
        if (action == MotionEvent.ACTION_DOWN) {
            down = true;
        } else if (action == MotionEvent.ACTION_UP) {
            down = false;
        } else if (action == MotionEvent.ACTION_MOVE) {
            down = true;
        } else {
            return false;
        }


        Log.e(LOGTAG, "real onTouchEvent: " + aEvent.toString());
        final boolean isDown = down;

        final float xx = aEvent.getX(0);
        final float yy = aEvent.getY(0);
        mView.queueEvent(new Runnable() {
            @Override
            public void run() {
                touchEvent(isDown, xx, yy);
            }
        });
        return true;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent aEvent) {
        if (aEvent.getActionIndex() != 0) {
            Log.e(LOGTAG,"aEvent.getActionIndex()=" + aEvent.getActionIndex());
            return false;
        }

        if (aEvent.getAction() != MotionEvent.ACTION_HOVER_MOVE) {
            return false;
        }

        // Log.e(LOGTAG, "real onGenericMotionEvent: " + aEvent.toString());

        final float xx = aEvent.getX(0);
        final float yy = aEvent.getY(0);
        mView.queueEvent(new Runnable() {
            @Override
            public void run() {
                touchEvent(false, xx, yy);
            }
        });
        return true;
    }

    @Override
    protected void onPause() {
        Log.e(LOGTAG, "PlatformActivity onPause");
        synchronized (activityPausedRunnable) {
            mView.queueEvent(activityPausedRunnable);
            try {
                activityPausedRunnable.wait();
            } catch(InterruptedException e) {
                Log.e(LOGTAG, "activityPausedRunnable interrupted: " + e.toString());
            }
        }
        mView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        Log.e(LOGTAG, "PlatformActivity onResume");
        super.onResume();
        mView.onResume();
        mView.queueEvent(activityResumedRunnable);
        // setImmersiveSticky();
    }

    @Override
    protected void onDestroy() {
        Log.e(LOGTAG, "PlatformActivity onDestroy");
        super.onDestroy();
        synchronized (activityDestroyedRunnable) {
            mView.queueEvent(activityDestroyedRunnable);
            try {
                activityDestroyedRunnable.wait();
            } catch(InterruptedException e) {
                Log.e(LOGTAG, "activityDestroyedRunnable interrupted: " + e.toString());
            }
        }
    }

//    void setImmersiveSticky() {
//        getWindow()
//                .getDecorView()
//                .setSystemUiVisibility(
//                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
//                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
//                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
//                                | View.SYSTEM_UI_FLAG_FULLSCREEN
//                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
//    }

    void queueRunnable(Runnable aRunnable) {
        mView.queueEvent(aRunnable);
    }

    private void setupUI() {
        findViewById(R.id.up_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchMoveAxis(0, 1, 0);
            }
        });
       findViewById(R.id.down_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchMoveAxis(0, -1, 0);
            }
        });
        findViewById(R.id.forward_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchMoveAxis(0, 0, -1);
            }
        });
        findViewById(R.id.backward_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchMoveAxis(0, 0, 1);
            }
        });
        findViewById(R.id.left_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchMoveAxis(-1, 0, 0);
            }
        });

        findViewById(R.id.right_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchMoveAxis(1, 0, 0);
            }
        });

        findViewById(R.id.home_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchMoveAxis(0,0,0);
            }
        });

        findViewById(R.id.right_turn_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchRotateHeading(-ROTATION);
            }
        });

        findViewById(R.id.left_turn_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchRotateHeading(ROTATION);
            }
        });
    }

    private void dispatchMoveAxis(final float aX, final float aY, final float aZ) {
        mView.queueEvent(new Runnable() {
            @Override
            public void run() {
                moveAxis(aX, aY, aZ);
            }
        });
    }

    private void dispatchRotateHeading(final float aHeading) {
        mView.queueEvent(new Runnable() {
            @Override
            public void run() {
                rotateHeading(aHeading);
            }
        });
    }

    private native void activityCreated(Object aAssetManager);
    private native void updateViewport(int width, int height);
    private native void activityPaused();
    private native void activityResumed();
    private native void activityDestroyed();
    private native void drawGL();
    private native void moveAxis(float aX, float aY, float aZ);
    private native void rotateHeading(float aHeading);
    private native void touchEvent(boolean aDown, float aX, float aY);
}
