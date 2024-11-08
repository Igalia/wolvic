package com.igalia.wolvic.browser.api.impl;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WAllowOrDeny;
import com.igalia.wolvic.browser.api.WResult;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.ui.adapters.WebApp;
import com.igalia.wolvic.utils.SystemUtils;

import org.chromium.base.task.PostTask;
import org.chromium.base.task.TaskTraits;
import org.chromium.blink.mojom.DisplayMode;
import org.chromium.content_public.browser.InvalidateTypes;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.WebContents;
import org.chromium.url.GURL;
import org.chromium.wolvic.Tab;
import org.chromium.wolvic.WolvicWebContentsDelegate;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class TabWebContentsDelegate extends WolvicWebContentsDelegate {
    private static final String LOGTAG = SystemUtils.createLogtag(TabWebContentsDelegate.class);

    private @NonNull SessionImpl mSession;
    private @NonNull final WebContents mWebContents;

    private boolean mIsFullscreen;

    public TabWebContentsDelegate(@NonNull SessionImpl session, WebContents webContents) {
        mSession = session;
        mWebContents = webContents;
    }

   @Override
   public boolean takeFocus(boolean reverse) {
       @Nullable WSession.ContentDelegate delegate = mSession.getContentDelegate();
       if (delegate != null && !reverse) {
           delegate.onFocusRequest(mSession);
           return true;
       }
       return false;
   }

    @Override
    public void enterFullscreenModeForTab(boolean prefersNavigationBar, boolean prefersStatusBar) {
        @Nullable WSession.ContentDelegate delegate = mSession.getContentDelegate();
        if (delegate == null) return;

        mIsFullscreen = true;
        delegate.onFullScreen(mSession, true);
    }

    @Override
    public void exitFullscreenModeForTab() {
        @Nullable WSession.ContentDelegate delegate = mSession.getContentDelegate();
        if (delegate == null) return;

        mIsFullscreen = false;
        delegate.onFullScreen(mSession, false);
    }

    @Override
    public boolean isFullscreenForTabOrPending() {
        return mIsFullscreen;
    }

    @Override
    public int getDisplayMode() {
        return !mIsFullscreen ? DisplayMode.BROWSER : DisplayMode.FULLSCREEN;
    }

    @Override
    public void navigationStateChanged(int flags) {
        if ((flags & InvalidateTypes.TITLE) != 0) {
            WSession.ContentDelegate delegate = mSession.getContentDelegate();
            if (delegate != null) {
                delegate.onTitleChange(mSession, mWebContents.getTitle());
            }
        }
    }

    @Override
    public void onWebAppManifest(WebContents webContents, @NonNull String manifest) {
         @Nullable WSession.ContentDelegate delegate = mSession.getContentDelegate();
        if (delegate == null)
            return;
        // We parse the manifest here to prevent errors later in case it is malformed.
        try {
            WebApp webAppManifest = new WebApp(new JSONObject(manifest));
            delegate.onWebAppManifest(mSession, webAppManifest);
        } catch (JSONException e) {
            Log.w(LOGTAG, "Error parsing JSON: " + e.getMessage());
        } catch (IOException e) {
            Log.w(LOGTAG, "Error when receiving Web App manifest: " + e.getMessage());
        }
    }

    public class OnNewSessionCallback implements WSession.NavigationDelegate.OnNewSessionCallback {
        public OnNewSessionCallback(RuntimeImpl runtime, WebContents webContents) {
            mRuntime = runtime;
            mWebContents = webContents;
        }

        @Override
        public void onNewSession(WSession session) {
            ((SessionImpl) session).invokeOnReady(mRuntime, mWebContents);
        }

        private RuntimeImpl mRuntime;
        private WebContents mWebContents;
    }

    @Override
    public void onCreateNewWindow(WebContents webContents) {
        WSession.NavigationDelegate delegate = mSession.getNavigationDelegate();
        assert delegate != null;

        // We must return before executing this, otherwise we might end up messing around the native
        // objects that chromium uses to back up some of the Java objects we use. For example the
        // WebContentsDelegate created by Chromium might be freed by the onNewSession() call when it
        // sets the new WebContentsDelegate object created by Wolvic.
        PostTask.postDelayedTask(TaskTraits.UI_DEFAULT, () -> {
            delegate.onNewSession(mSession, webContents.getVisibleUrl().getSpec(),
                                  new OnNewSessionCallback(mSession.mRuntime, webContents));
        }, 0);
    }

    @Override
    public void closeContents() {
        WSession.ContentDelegate delegate = mSession.getContentDelegate();
        assert delegate != null;

        PostTask.postDelayedTask(TaskTraits.UI_DEFAULT, () -> {
            delegate.onCloseRequest(mSession);
        }, 0);
    }

    @Override
    public void onUpdateUrl(GURL url) {
        String newUrl = YoutubeUrlHelper.maybeRewriteYoutubeURL(url);
        // If mobile Youtube URL is detected, redirect to the desktop version.
        if (!url.getSpec().equals(newUrl)) {
            LoadUrlParams params = new LoadUrlParams(newUrl);
            mWebContents.getNavigationController().setEntryExtraData(
                    mWebContents.getNavigationController().getLastCommittedEntryIndex(),
                    Tab.NAVIGATION_ENTRY_MARKED_AS_SKIPPED_KEY,
                    Tab.NAVIGATION_ENTRY_MARKED_AS_SKIPPED_VALUE);
            mWebContents.getNavigationController().loadUrl(params);
            return;
        }

        WSession.NavigationDelegate delegate = mSession.getNavigationDelegate();
        if (delegate != null) {
            delegate.onLocationChange(mSession, mWebContents.getVisibleUrl().getSpec());
        }
    }

    @Override
    public void showRepostFormWarningDialog() {
        mSession.getChromiumPromptDelegate().onRepostConfirmWarningDialog().then(result -> {
            if (result.allowOrDeny() == WAllowOrDeny.ALLOW) {
                mWebContents.getNavigationController().continuePendingReload();
            } else {
                mWebContents.getNavigationController().cancelPendingReload();
            }
            return WResult.fromValue(null);
        });
    }
}
