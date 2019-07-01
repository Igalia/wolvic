package org.mozilla.vrbrowser.browser.engine;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.browser.Media;

import java.util.ArrayList;

class SessionState {

    public boolean mCanGoBack;
    public boolean mCanGoForward;
    public boolean mIsLoading;
    public boolean mIsInputActive;
    public GeckoSession.ProgressDelegate.SecurityInformation mSecurityInformation;
    public String mUri;
    public String mPreviousUri;
    public String mTitle;
    public boolean mFullScreen;
    public GeckoSession mSession;
    public SessionSettings mSettings;
    public ArrayList<Media> mMediaElements = new ArrayList<>();
    public GeckoSession.SessionState mSessionState;
}
