package org.mozilla.vrbrowser;

import android.app.Activity;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.gecko.util.GeckoBundle;
import org.mozilla.vrbrowser.SessionStore;
import org.mozilla.vrbrowser.ui.URLBarWidget;

public class BrowserActivity extends Activity {
    private static final String LOGTAG = "VRB";

    private static final String DEFAULT_URL = "https://vr.mozilla.org";
    private static final int REQUEST_PERMISSIONS = 2;
    /* package */ static final int REQUEST_FILE_PICKER = 1;
    private FrameLayout mContainer;
    private GeckoView mGeckoView;
    private GeckoSession mGeckoSession;
    private URLBarWidget mNavigationBar;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(LOGTAG, "BrowserActivity onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.browser_activity);
        mNavigationBar = findViewById(R.id.navigationBar2D);

        // Keep the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mContainer = findViewById(R.id.container);
        mGeckoView = findViewById(R.id.geckoview);
        mGeckoView.coverUntilFirstPaint(Color.TRANSPARENT);
        setContentView(mContainer);
        loadFromIntent(getIntent());
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        final String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            if (intent.getData() != null) {
                loadFromIntent(intent);
            }
        }
    }

    @Override
    public void onBackPressed() {
        SessionStore.get().goBack();
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        Log.e(LOGTAG, "BrowserActivity onPause");
        if (mGeckoView != null) {
            mGeckoView.setSession(null);
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        Log.e(LOGTAG, "BrowserActivity onResume");
        if (mGeckoSession != null && mGeckoView != null) {
            if (!mGeckoSession.isOpen()) {
                mGeckoView.setSession(mGeckoSession);
            }
            mGeckoView.requestFocus();
        }
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        Log.e(LOGTAG, "BrowserActivity onDestroy");
        if (mNavigationBar != null) {
            mNavigationBar.releaseWidget();
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode,
                                    final Intent data) {
        if (requestCode == REQUEST_FILE_PICKER) {
            //final BasicGeckoViewPrompt prompt = (BasicGeckoViewPrompt)
            //        mGeckoSession.getPromptDelegate();
            //prompt.onFileCallbackResult(resultCode, data);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode,
                                           final String[] permissions,
                                           final int[] grantResults) {
        Log.e(LOGTAG,"Got onRequestPermissionsResult");
        if (requestCode == REQUEST_PERMISSIONS && (mGeckoSession != null)) {
            final MyGeckoViewPermission permission = (MyGeckoViewPermission)
                    mGeckoSession.getPermissionDelegate();
            permission.onRequestPermissionsResult(permissions, grantResults);
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void loadFromIntent(final Intent intent) {
        final Uri uri = intent.getData();
        mGeckoSession = SessionStore.get().getCurrentSession();
        String uriValue;
        if (mGeckoSession == null) {
            int id = SessionStore.get().createSession();
            SessionStore.get().setCurrentSession(id, this);
            mGeckoSession = SessionStore.get().getCurrentSession();
            final MyGeckoViewPermission permission = new MyGeckoViewPermission();
            permission.androidPermissionRequestCode = REQUEST_PERMISSIONS;
            mGeckoSession.setPermissionDelegate(permission);
            uriValue = (uri != null ? uri.toString() : DEFAULT_URL);
            Log.e(LOGTAG, "BrowserActivity create session and load URI from intent: " + uriValue);
            mGeckoSession.loadUri(uriValue);
            mGeckoView.setSession(mGeckoSession);
        } else if (uri != null) {
            uriValue = uri.toString();
            Log.e(LOGTAG, "BrowserActivity load URI from intent: " + uriValue);
            mGeckoSession.loadUri(uriValue);
        } else {
            uriValue = SessionStore.get().getCurrentUri();
            Log.e(LOGTAG, "BrowserActivity URI current session: " + uriValue);
        }
    }

    public void setFullScreen() {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;


        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private class MyGeckoViewPermission implements GeckoSession.PermissionDelegate {

        int androidPermissionRequestCode = 1;
        private Callback mCallback;

        void onRequestPermissionsResult(final String[] permissions,
                                        final int[] grantResults) {
            if (mCallback == null) {
                return;
            }

            final Callback cb = mCallback;
            mCallback = null;
            for (final int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    // At least one permission was not granted.
                    cb.reject();
                    return;
                }
            }
            Log.e(LOGTAG, "Permission Granted!");
            cb.grant();
        }

        @Override
        public void onAndroidPermissionsRequest(final GeckoSession session, final String[] permissions,
                                                final Callback callback) {
            mCallback = callback;
            requestPermissions(permissions, androidPermissionRequestCode);
        }

        @Override
        public void onContentPermissionRequest(final GeckoSession session, final String uri,
                                               final int type, final String access,
                                               final Callback callback) {
/*            final int resId;
            if ("geolocation".equals(type)) {
                resId = R.string.request_geolocation;
            } else if ("desktop-notification".equals(type)) {
                resId = R.string.request_notification;
            } else {
                Log.w(LOGTAG, "Unknown permission: " + type);
                callback.reject();
                return;
            }

            final String title = getString(resId, Uri.parse(uri).getAuthority());
            final BasicGeckoViewPrompt prompt = (BasicGeckoViewPrompt)
                    mGeckoSession.getPromptDelegate();
            prompt.promptForPermission(session, title, callback);*/
        }

        @Override
        public void onMediaPermissionRequest(GeckoSession geckoSession, String s,
                                             MediaSource[] mediaSources, MediaSource[] mediaSources1,
                                             MediaCallback mediaCallback) {

        }

        private void normalizeMediaName(final GeckoBundle[] sources) {
/*            if (sources == null) {
                return;
            }
            for (final GeckoBundle source : sources) {
                final String mediaSource = source.getString("mediaSource");
                String name = source.getString("name");
                if ("camera".equals(mediaSource)) {
                    if (name.toLowerCase(Locale.ENGLISH).contains("front")) {
                        name = getString(R.string.media_front_camera);
                    } else {
                        name = getString(R.string.media_back_camera);
                    }
                } else if (!name.isEmpty()) {
                    continue;
                } else if ("microphone".equals(mediaSource)) {
                    name = getString(R.string.media_microphone);
                } else {
                    name = getString(R.string.media_other);
                }
                source.putString("name", name);
            }*/
        }
    }
}
