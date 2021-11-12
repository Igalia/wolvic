package org.mozilla.vrbrowser.ui.widgets;

import android.content.Context;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.jetbrains.annotations.NotNull;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.browser.Accounts;
import org.mozilla.vrbrowser.browser.HistoryStore;
import org.mozilla.vrbrowser.browser.Media;
import org.mozilla.vrbrowser.browser.Services;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.adapter.ComponentsAdapter;
import org.mozilla.vrbrowser.browser.components.GeckoEngineSession;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.browser.engine.SessionState;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.downloads.DownloadsManager;
import org.mozilla.vrbrowser.telemetry.GleanMetricsService;
import org.mozilla.vrbrowser.ui.widgets.dialogs.PromptDialogWidget;
import org.mozilla.vrbrowser.ui.widgets.dialogs.UIDialog;
import org.mozilla.vrbrowser.utils.BitmapCache;
import org.mozilla.vrbrowser.utils.ConnectivityReceiver;
import org.mozilla.vrbrowser.utils.DeviceType;
import org.mozilla.vrbrowser.utils.StringUtils;
import org.mozilla.vrbrowser.utils.SystemUtils;
import org.mozilla.vrbrowser.utils.UrlUtils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import mozilla.components.concept.sync.AccountObserver;
import mozilla.components.concept.sync.AuthFlowError;
import mozilla.components.concept.sync.AuthType;
import mozilla.components.concept.sync.OAuthAccount;
import mozilla.components.concept.sync.Profile;
import mozilla.components.concept.sync.TabData;

import static org.mozilla.vrbrowser.ui.widgets.settings.SettingsView.SettingViewType.FXA;

public class Windows implements TrayListener, TopBarWidget.Delegate, TitleBarWidget.Delegate,
        WindowWidget.WindowListener, TabsWidget.TabDelegate, Services.TabReceivedDelegate {

    private static final String LOGTAG = SystemUtils.createLogtag(Windows.class);

    public static final int WHITE = 0xFFFFFFFF;
    public static final int GRAY = 0x555555FF;

    @IntDef(value = { OPEN_IN_FOREGROUND, OPEN_IN_BACKGROUND, OPEN_IN_NEW_WINDOW})
    public @interface NewTabLocation {}
    public static final int OPEN_IN_FOREGROUND = 0;
    public static final int OPEN_IN_BACKGROUND = 1;
    public static final int OPEN_IN_NEW_WINDOW = 2;


    private static final String WINDOWS_SAVE_FILENAME = "windows_state.json";

    private static final int TAB_ADDED_NOTIFICATION_ID = 0;
    private static final int TAB_SENT_NOTIFICATION_ID = 1;
    private static final int BOOKMARK_ADDED_NOTIFICATION_ID = 2;

    class WindowState {
        WindowPlacement placement;
        int textureWidth;
        int textureHeight;
        float worldWidth;
        int tabIndex = -1;
        @PanelType int panelType = Windows.NONE;

        public void load(@NonNull WindowWidget aWindow, WindowsState aState, int aTabIndex) {
            WidgetPlacement widgetPlacement;
            if (aWindow.isFullScreen()) {
                widgetPlacement = aWindow.getBeforeFullscreenPlacement();
                placement = aWindow.getWindowPlacementBeforeFullscreen();
            } else if (aWindow.isResizing()) {
                widgetPlacement = aWindow.getBeforeResizePlacement();
                placement = aWindow.getWindowPlacement();
            } else {
                widgetPlacement = aWindow.getPlacement();
                placement = aWindow.getWindowPlacement();
            }

            textureWidth = widgetPlacement.width;
            textureHeight = widgetPlacement.height;
            worldWidth = widgetPlacement.worldWidth;
            tabIndex = aTabIndex;
            if (aWindow.isLibraryVisible()) {
                panelType = aWindow.getSelectedPanel();

            } else {
                panelType = Windows.NONE;
            }
        }
    }

    class WindowsState {
        WindowPlacement focusedWindowPlacement = WindowPlacement.FRONT;
        ArrayList<WindowState> regularWindowsState = new ArrayList<>();
        ArrayList<SessionState> tabs = new ArrayList<>();
        boolean privateMode = false;
    }

    private Context mContext;
    private WidgetManagerDelegate mWidgetManager;
    private Delegate mDelegate;
    private ArrayList<WindowWidget> mRegularWindows;
    private ArrayList<WindowWidget> mPrivateWindows;
    private WindowWidget mFocusedWindow;
    private static int sIndex;
    private boolean mPrivateMode = false;
    public static final int MAX_WINDOWS = 3;
    private WindowWidget mFullscreenWindow;
    private WindowPlacement mRegularWindowPlacement;
    private WindowPlacement mPrivateWindowPlacement;
    private boolean mStoredCurvedMode = false;
    private boolean mForcedCurvedMode = false;
    private boolean mIsPaused = false;
    private TabsWidget mTabsWidget;
    private Accounts mAccounts;
    private Services mServices;
    private PromptDialogWidget mNoInternetDialog;
    private boolean mCompositorPaused = false;
    private WindowsState mWindowsState;
    private boolean mIsRestoreEnabled;
    private boolean mAfterRestore;
    private String mAddedTabUri;
    private @NewTabLocation int mAddedTabLocation = OPEN_IN_FOREGROUND;
    private DownloadsManager mDownloadsManager;
    private ConnectivityReceiver mConnectivityReceived;

    @IntDef(value = { NONE, BOOKMARKS, HISTORY, DOWNLOADS, ADDONS})
    public @interface PanelType {}
    public static final int NONE = 0;
    public static final int BOOKMARKS = 1;
    public static final int HISTORY = 2;
    public static final int DOWNLOADS = 3;
    public static final int ADDONS = 4;

    public enum WindowPlacement{
        FRONT(0),
        LEFT(1),
        RIGHT(2);

        private final int value;

        WindowPlacement(final int aValue) {
            value = aValue;
        }

        public int getValue() { return value; }
    }

    public interface Delegate {
        void onFocusedWindowChanged(@NonNull WindowWidget aFocusedWindow, @Nullable WindowWidget aPrevFocusedWindow);
        void onWindowBorderChanged(@NonNull WindowWidget aChangeWindow);
        void onWindowsMoved();
        void onWindowClosed();
        void onWindowVideoAvailabilityChanged(@NonNull WindowWidget aWindow);
    }

    public Windows(Context aContext) {
        mContext = aContext;
        mWidgetManager = (WidgetManagerDelegate) aContext;
        mRegularWindows = new ArrayList<>();
        mPrivateWindows = new ArrayList<>();

        mRegularWindowPlacement = WindowPlacement.FRONT;
        mPrivateWindowPlacement = WindowPlacement.FRONT;

        mStoredCurvedMode = SettingsStore.getInstance(mContext).getCylinderDensity() > 0.0f;

        mAccounts = mWidgetManager.getServicesProvider().getAccounts();
        mAccounts.addAccountListener(mAccountObserver);
        mServices = mWidgetManager.getServicesProvider().getServices();
        mServices.setTabReceivedDelegate(this);

        mConnectivityReceived = mWidgetManager.getServicesProvider().getConnectivityReceiver();
        mConnectivityReceived.addListener(mConnectivityDelegate);

        mDownloadsManager = mWidgetManager.getServicesProvider().getDownloadsManager();

        mIsRestoreEnabled = SettingsStore.getInstance(mContext).isRestoreTabsEnabled();
        mWindowsState = restoreState();
        restoreWindows();
    }

    public void saveState() {
        File file = new File(mContext.getFilesDir(), WINDOWS_SAVE_FILENAME);
        try (Writer writer = new FileWriter(file)) {
            WindowsState state = new WindowsState();
            state.privateMode = mPrivateMode;
            state.focusedWindowPlacement = mFocusedWindow.isFullScreen() ?  mFocusedWindow.getWindowPlacementBeforeFullscreen() : mFocusedWindow.getWindowPlacement();
            ArrayList<Session> sessions = SessionStore.get().getSortedSessions(false);
            state.tabs = sessions.stream()
                    .map(Session::getSessionState)
                    .filter(sessionState -> HistoryStore.getBLOCK_LIST().stream().noneMatch(uri ->
                        sessionState.mUri != null && sessionState.mUri.startsWith(uri)
                    ))
                    .collect(Collectors.toCollection(ArrayList::new));
            for (WindowWidget window : mRegularWindows) {
                if (window.getSession() != null) {
                    WindowState windowState = new WindowState();
                    windowState.load(window, state, state.tabs.indexOf(window.getSession().getSessionState()));
                    state.regularWindowsState.add(windowState);
                }
            }
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(state, writer);
            writer.flush();

            Log.d(LOGTAG, "Windows state saved");

        } catch (IOException e) {
            Log.e(LOGTAG, "Error saving windows state: " + e.getLocalizedMessage());
            file.delete();
        }
    }

    private WindowsState restoreState() {
        WindowsState restored = null;

        File file = new File(mContext.getFilesDir(), WINDOWS_SAVE_FILENAME);
        try (Reader reader = new FileReader(file)) {
            Gson gson = new GsonBuilder().create();
            Type type = new TypeToken<WindowsState>() {}.getType();
            restored = gson.fromJson(reader, type);

            Log.d(LOGTAG, "Windows state restored");

        } catch (Exception e) {
            file.delete();
        }

        return restored;
    }

    public void setDelegate(Delegate aDelegate) {
        mDelegate = aDelegate;
    }

    public WindowWidget getFocusedWindow() {
        if (mFullscreenWindow != null) {
            return mFullscreenWindow;
        }
        return mFocusedWindow;
    }

    @Nullable
    public WindowWidget addWindow() {
        if (getCurrentWindows().size() >= MAX_WINDOWS) {
            return null;
        }

        if (mFullscreenWindow != null) {
            mFullscreenWindow.getSession().exitFullScreen();
            onFullScreen(mFullscreenWindow, false);
        }

        WindowWidget frontWindow = getFrontWindow();
        WindowWidget leftWindow = getLeftWindow();
        WindowWidget rightWindow = getRightWindow();

        WindowWidget newWindow = createWindow(null);
        WindowWidget focusedWindow = getFocusedWindow();

        if (frontWindow == null) {
            // First window
            placeWindow(newWindow, WindowPlacement.FRONT);
        } else if (leftWindow == null && rightWindow == null) {
            // Opening a new window from one window
            placeWindow(newWindow, WindowPlacement.FRONT);
            placeWindow(frontWindow, WindowPlacement.LEFT);
        } else if (leftWindow != null && focusedWindow == leftWindow) {
            // Opening a new window from left window
            placeWindow(newWindow, WindowPlacement.FRONT);
            placeWindow(frontWindow, WindowPlacement.RIGHT);
        } else if (leftWindow != null && focusedWindow == frontWindow) {
            // Opening a new window from front window
            placeWindow(newWindow, WindowPlacement.FRONT);
            placeWindow(frontWindow, WindowPlacement.RIGHT);
        } else if (rightWindow != null && focusedWindow == rightWindow) {
            // Opening a new window from right window
            placeWindow(newWindow, WindowPlacement.FRONT);
            placeWindow(frontWindow, WindowPlacement.LEFT);
        } else if (rightWindow != null && focusedWindow == frontWindow) {
            // Opening a new window from right window
            placeWindow(newWindow, WindowPlacement.FRONT);
            placeWindow(frontWindow, WindowPlacement.LEFT);
        }

        updateMaxWindowScales();
        mWidgetManager.addWidget(newWindow);
        focusWindow(newWindow);
        updateCurvedMode(true);
        updateViews();

        // We are only interested in general windows opened.
        if (!isInPrivateMode()) {
            GleanMetricsService.newWindowOpenEvent();
        }
        return newWindow;
    }

    private WindowWidget addRestoredWindow(@NonNull WindowState aState, @Nullable Session aSession) {
        if (getCurrentWindows().size() >= MAX_WINDOWS) {
            return null;
        }

        if (aSession != null) {
            aSession.setActive(true);
        }
        WindowWidget newWindow = createWindow(aSession);
        newWindow.getPlacement().width = aState.textureWidth;
        newWindow.getPlacement().height = aState.textureHeight;
        newWindow.getPlacement().worldWidth = aState.worldWidth;
        placeWindow(newWindow, aState.placement);
        if (newWindow.getSession() != null) {
            switch (aState.panelType) {
                case BOOKMARKS:
                    newWindow.getSession().loadUri(UrlUtils.ABOUT_BOOKMARKS);
                    break;
                case HISTORY:
                    newWindow.getSession().loadUri(UrlUtils.ABOUT_HISTORY);
                    break;
                case DOWNLOADS:
                    newWindow.getSession().loadUri(UrlUtils.ABOUT_DOWNLOADS);
                    break;
                case ADDONS:
                    newWindow.getSession().loadUri(UrlUtils.ABOUT_ADDONS);
                    break;
                case NONE:
                    break;
            }
        }
        updateCurvedMode(true);

        mWidgetManager.addWidget(newWindow);
        return newWindow;
    }

    public void closeWindow(@NonNull WindowWidget aWindow) {
        WindowWidget frontWindow = getFrontWindow();
        WindowWidget leftWindow = getLeftWindow();
        WindowWidget rightWindow = getRightWindow();

        aWindow.hidePanel();

        if (leftWindow == aWindow) {
            removeWindow(leftWindow);
            if (mFocusedWindow == leftWindow && frontWindow != null) {
                focusWindow(frontWindow);
            }
        } else if (rightWindow == aWindow) {
            removeWindow(rightWindow);
            if (mFocusedWindow == rightWindow && frontWindow != null) {
                focusWindow(frontWindow);
            }
        } else if (frontWindow == aWindow) {
            removeWindow(frontWindow);
            if (rightWindow != null) {
                placeWindow(rightWindow, WindowPlacement.FRONT);
            } else if (leftWindow != null) {
                placeWindow(leftWindow, WindowPlacement.FRONT);
            }

            if (mFocusedWindow == frontWindow && !getCurrentWindows().isEmpty() && getFrontWindow() != null) {
                focusWindow(getFrontWindow());
            }

        }

        boolean empty = getCurrentWindows().isEmpty();
        if (empty && isInPrivateMode()) {
            // Clear private tabs
            SessionStore.get().destroyPrivateSessions();
            // Exit private mode if the only window is closed.
            exitPrivateMode();
        } else if (empty) {
            // Ensure that there is at least one window.
            WindowWidget window = addWindow();
            if (window != null) {
                window.loadHome();
            }
        }

        updateViews();
        if (mDelegate != null) {
            mDelegate.onWindowClosed();
        }
    }

    public void moveWindowRight(@NonNull WindowWidget aWindow) {
        WindowWidget frontWindow = getFrontWindow();
        WindowWidget leftWindow = getLeftWindow();
        WindowWidget rightWindow = getRightWindow();

        if (aWindow == leftWindow && frontWindow != null) {
            placeWindow(leftWindow, WindowPlacement.FRONT);
            placeWindow(frontWindow, WindowPlacement.LEFT);
            switchTopBars(leftWindow, frontWindow);
        } else if (aWindow == frontWindow) {
            if (rightWindow != null) {
                placeWindow(rightWindow, WindowPlacement.FRONT);
                switchTopBars(rightWindow, frontWindow);
            } else if (leftWindow != null) {
                placeWindow(leftWindow, WindowPlacement.FRONT);
                switchTopBars(leftWindow, frontWindow);
            }
            placeWindow(frontWindow, WindowPlacement.RIGHT);
        }
        updateViews();
        if (mDelegate != null) {
            mDelegate.onWindowsMoved();
        }
    }

    public void moveWindowLeft(@NonNull WindowWidget aWindow) {
        WindowWidget frontWindow = getFrontWindow();
        WindowWidget leftWindow = getLeftWindow();
        WindowWidget rightWindow = getRightWindow();

        if (aWindow == rightWindow && frontWindow != null) {
            placeWindow(rightWindow, WindowPlacement.FRONT);
            placeWindow(frontWindow, WindowPlacement.RIGHT);
            switchTopBars(rightWindow, frontWindow);
        } else if (aWindow == frontWindow) {
            if (leftWindow != null) {
                placeWindow(leftWindow, WindowPlacement.FRONT);
                switchTopBars(leftWindow, frontWindow);
            } else if (rightWindow != null) {
                placeWindow(rightWindow, WindowPlacement.FRONT);
                switchTopBars(rightWindow, frontWindow);
            }
            placeWindow(frontWindow, WindowPlacement.LEFT);
        }
        updateViews();
        if (mDelegate != null) {
            mDelegate.onWindowsMoved();
        }
    }

    public void focusWindow(@Nullable WindowWidget aWindow) {
        if (aWindow != mFocusedWindow) {
            WindowWidget prev = mFocusedWindow;
            mFocusedWindow = aWindow;
            if (prev != null && getCurrentWindows().contains(prev)) {
                prev.setActiveWindow(false);
            }
            mFocusedWindow.setActiveWindow(true);
            if (mDelegate != null) {
                mDelegate.onFocusedWindowChanged(mFocusedWindow, prev);
            }
        }
    }

    public void pauseCompositor() {
        if (mCompositorPaused) {
            return;
        }
        mCompositorPaused = true;
        for (WindowWidget window: mRegularWindows) {
            window.pauseCompositor();
        }
        for (WindowWidget window: mPrivateWindows) {
            window.pauseCompositor();
        }
    }

    public void resumeCompositor() {
        if (!mCompositorPaused) {
            return;
        }
        mCompositorPaused = false;
        for (WindowWidget window: mRegularWindows) {
            window.resumeCompositor();
        }
        for (WindowWidget window: mPrivateWindows) {
            window.resumeCompositor();
        }
    }

    public void onPause() {
        mIsPaused = true;

        saveState();
    }

    public void onResume() {
        mIsPaused = false;
        if (mCompositorPaused) {
            resumeCompositor();
        }

        GleanMetricsService.resetOpenedWindowsCount(mRegularWindows.size(), false);
        GleanMetricsService.resetOpenedWindowsCount(mPrivateWindows.size(), true);
    }

    public boolean isPaused() {
        return mIsPaused;
    }

    public void onDestroy() {
        if (mTabsWidget != null && !mTabsWidget.isReleased()) {
            mTabsWidget.releaseWidget();
            mTabsWidget = null;
        }
        mDelegate = null;
        for (WindowWidget window: mRegularWindows) {
            window.close();
        }
        for (WindowWidget window: mPrivateWindows) {
            window.close();
        }
        mAccounts.removeAccountListener(mAccountObserver);
        mServices.setTabReceivedDelegate(null);
        mConnectivityReceived.removeListener(mConnectivityDelegate);
    }

    public boolean isInPrivateMode() {
        return mPrivateMode;
    }

    public boolean isVideoAvailable() {
        for (WindowWidget window: getCurrentWindows()) {
            if (window.getSession().isVideoAvailable()) {
                return true;
            }
        }

        return false;
    }

    public void enterImmersiveMode() {
        if (!isInPrivateMode()) {
            for (WindowWidget window: mRegularWindows) {
                if (window != mFocusedWindow) {
                    window.onPause();
                }
            }

        } else {
            for (WindowWidget window: mPrivateWindows) {
                if (window != mFocusedWindow) {
                    window.onPause();
                }
            }
        }
    }

    public void exitImmersiveMode() {
        if (mIsPaused) {
            return;
        }

        if (!isInPrivateMode()) {
            for (WindowWidget window: mRegularWindows) {
                if (window != mFocusedWindow) {
                    window.onResume();
                }
            }

        } else {
            for (WindowWidget window: mPrivateWindows) {
                if (window != mFocusedWindow) {
                    window.onResume();
                }
            }
        }
    }

    public void enterPrivateMode() {
        if (mPrivateMode) {
            return;
        }
        mPrivateMode = true;

        if (mFocusedWindow != null) {
            mRegularWindowPlacement = mFocusedWindow.getWindowPlacement();

        } else {
            mRegularWindowPlacement = WindowPlacement.FRONT;
        }
        for (int i=0; i<mRegularWindows.size(); i++) {
            WindowWidget window = mRegularWindows.get(i);
            setWindowVisible(window, false);
        }

        updateViews();
        updateCurvedMode(true);

        for (int i=0; i<mPrivateWindows.size(); i++) {
            WindowWidget window = mPrivateWindows.get(i);
            setWindowVisible(window, true);

            // Make sure that if the Window session it's been closed we restore a valid session
            if (SessionStore.get().getSession(window.getSession().getId()) == null) {
                onTabsClose(Collections.singletonList(window.getSession()));
            }
        }

        if (mPrivateWindows.size() == 0) {
            WindowWidget window = addWindow();
            if (window != null) {
                window.loadHome();
            }

        } else {
            focusWindow(getWindowWithPlacement(mPrivateWindowPlacement));
        }

        mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);
    }

    public void exitPrivateMode() {
        if (!mPrivateMode) {
            return;
        }
        mPrivateMode = false;

        if (mFocusedWindow != null) {
            mPrivateWindowPlacement = mFocusedWindow.getWindowPlacement();

        } else {
            mPrivateWindowPlacement = WindowPlacement.FRONT;
        }
        for (int i=0; i<mPrivateWindows.size(); i++) {
            WindowWidget window = mPrivateWindows.get(i);
            setWindowVisible(window, false);
        }

        updateViews();
        updateCurvedMode(true);

        for (int i=0; i<mRegularWindows.size(); i++) {
            WindowWidget window = mRegularWindows.get(i);
            setWindowVisible(window, true);

            // Make sure that if the Window session it's been closed we restore a valid session
            if (SessionStore.get().getSession(window.getSession().getId()) == null) {
                onTabsClose(Collections.singletonList(window.getSession()));
            }
        }
        WindowWidget window = getWindowWithPlacement(mRegularWindowPlacement);
        if (window != null) {
            focusWindow(window);
        }

        mWidgetManager.popWorldBrightness(this);
    }

    public boolean handleBack() {
        if (mFocusedWindow == null) {
            return false;
        }
        if (mFocusedWindow.getSession().canGoBack()) {
            mFocusedWindow.getSession().goBack();
            return true;
        } else if (isInPrivateMode()) {
            exitPrivateMode();
            return true;
        }

        return false;
    }

    void updateMaxWindowScales() {
        float maxScale = 3;
        if (mFullscreenWindow == null && getCurrentWindows().size() >= 3) {
            maxScale = 1.5f;
        } else if (mFullscreenWindow == null && getCurrentWindows().size() == 2) {
            maxScale = 2.0f;
        }

        for (WindowWidget window: getCurrentWindows()) {
            window.setMaxWindowScale(maxScale);
        }
    }

    public ArrayList<WindowWidget> getCurrentWindows(boolean privateMode) {
        return privateMode ? mPrivateWindows : mRegularWindows;
    }

    public ArrayList<WindowWidget> getCurrentWindows() {
        return getCurrentWindows(mPrivateMode);
    }

    @Nullable
    private WindowWidget getWindowWithPlacement(WindowPlacement aPlacement, boolean privateMode) {
        for (WindowWidget window: privateMode ? mPrivateWindows : mRegularWindows) {
            if (window.getWindowPlacement() == aPlacement) {
                return window;
            }
        }
        return null;
    }

    @Nullable
    private WindowWidget getWindowWithPlacement(WindowPlacement aPlacement) {
        return getWindowWithPlacement(aPlacement, mPrivateMode);
    }

    @Nullable
    private WindowWidget getFrontWindow(boolean privateMode) {
        if (mFullscreenWindow != null) {
            return mFullscreenWindow;
        }
        return getWindowWithPlacement(WindowPlacement.FRONT, privateMode);
    }

    @Nullable
    private WindowWidget getFrontWindow() {
        return getFrontWindow(mPrivateMode);
    }

    @Nullable
    private WindowWidget getLeftWindow() {
        return getWindowWithPlacement(WindowPlacement.LEFT);
    }

    @Nullable
    private WindowWidget getRightWindow() {
        return getWindowWithPlacement(WindowPlacement.RIGHT);
    }

    private void restoreWindows() {
        if (mIsRestoreEnabled && mWindowsState != null) {
            for (WindowState windowState : mWindowsState.regularWindowsState) {
                addRestoredWindow(windowState, null);
            }

            WindowWidget windowToFocus = getWindowWithPlacement(mWindowsState.focusedWindowPlacement);
            if (windowToFocus == null) {
                windowToFocus = getFrontWindow();
                if (windowToFocus == null && getCurrentWindows().size() > 0) {
                    windowToFocus = getCurrentWindows().get(0);
                }
            }
            if (windowToFocus != null) {
                focusWindow(windowToFocus);
            }

        }
        if (getCurrentWindows().size() == 0) {
            WindowWidget window = addWindow();
            focusWindow(window);
        }
        updateMaxWindowScales();
        updateViews();
    }

    public void restoreSessions() {
        if (mIsRestoreEnabled && mWindowsState != null) {
            ArrayList<Session> restoredSessions = new ArrayList<>();
            if (mWindowsState.tabs != null) {
                mWindowsState.tabs.forEach(state -> {
                    restoredSessions.add(SessionStore.get().createSuspendedSession(state));
                    GleanMetricsService.Tabs.openedCounter(GleanMetricsService.Tabs.TabSource.PRE_EXISTING);
                });
            }

            for (WindowState windowState : mWindowsState.regularWindowsState) {
                WindowWidget targetWindow = getWindowWithPlacement(windowState.placement);
                if (targetWindow != null) {
                    if (windowState.tabIndex >= 0 && windowState.tabIndex < restoredSessions.size()) {
                        Session defaultSession = targetWindow.getSession();
                        Session session = restoredSessions.get(windowState.tabIndex);
                        targetWindow.setSession(session, WindowWidget.DEACTIVATE_CURRENT_SESSION);
                        session.setActive(true);
                        // Destroy the default blank session
                        SessionStore.get().destroySession(defaultSession);

                    } else {
                        targetWindow.loadHome();
                    }
                }
            }

            if (mWindowsState.privateMode) {
                enterPrivateMode();
            } else {
                exitPrivateMode();
            }
        }

        if (mAddedTabUri != null) {
            openNewTab(mAddedTabUri, mAddedTabLocation);
            mAddedTabUri = null;
        } else if (DeviceType.isHVRBuild()) {
            String uri = SettingsStore.getInstance(mContext).getTabAfterRestore();
            if (!StringUtils.isEmpty(uri)) {
                openNewTab(uri, Windows.OPEN_IN_FOREGROUND);
                SettingsStore.getInstance(mContext).setTabAfterRestore(null);
            }
        }

        mAfterRestore = true;
    }

    private void removeWindow(@NonNull WindowWidget aWindow) {
        BitmapCache.getInstance(mContext).removeBitmap(aWindow.getSession().getId());
        mWidgetManager.removeWidget(aWindow);
        mRegularWindows.remove(aWindow);
        mPrivateWindows.remove(aWindow);
        aWindow.removeWindowListener(this);
        aWindow.close();
        updateMaxWindowScales();
        updateCurvedMode(true);

        if (mPrivateMode) {
            GleanMetricsService.openWindowsEvent(mPrivateWindows.size() + 1, mPrivateWindows.size(), true);
        } else {
            GleanMetricsService.openWindowsEvent(mRegularWindows.size() + 1, mRegularWindows.size(), false);
        }
    }

    private void setWindowVisible(@NonNull WindowWidget aWindow, boolean aVisible) {
        if (aVisible && (aWindow.getSession() != null) && (aWindow.getSession().getGeckoSession() == null)) {
            setFirstPaint(aWindow, aWindow.getSession());
        }
        aWindow.setVisible(aVisible);
        if (aWindow != mFocusedWindow) {
            aWindow.getTitleBar().setVisible(aVisible);
        }
        aWindow.getTopBar().setVisible(aVisible);
    }

    private void placeWindow(@NonNull WindowWidget aWindow, WindowPlacement aPosition) {
        placeWindow(aWindow, aPosition, mStoredCurvedMode || mForcedCurvedMode);
    }

    private void placeWindow(@NonNull WindowWidget aWindow, WindowPlacement aPosition, boolean curvedMode) {
        WidgetPlacement placement = aWindow.getPlacement();
        aWindow.setWindowPlacement(aPosition);
        switch (aPosition) {
            case FRONT:
                placement.anchorX = 0.5f;
                placement.anchorY = 0.0f;
                placement.rotation = 0;
                placement.rotationAxisX = 0;
                placement.rotationAxisY = 0;
                placement.rotationAxisZ = 0;
                placement.translationX = 0.0f;
                placement.translationY = WidgetPlacement.unitFromMeters(mContext, R.dimen.window_world_y);
                placement.translationZ = WidgetPlacement.unitFromMeters(mContext, R.dimen.window_world_z);
                break;
            case LEFT:
                placement.anchorX = 1.0f;
                placement.anchorY = 0.0f;
                placement.parentAnchorX = 0.0f;
                placement.parentAnchorY = 0.0f;
                placement.rotationAxisX = 0;
                placement.rotationAxisZ = 0;
                if (curvedMode) {
                    placement.rotationAxisY = 0;
                    placement.rotation = 0;
                } else {
                    placement.rotationAxisY = 1.0f;
                    placement.rotation = (float) Math.toRadians(WidgetPlacement.floatDimension(mContext, R.dimen.multi_window_angle));
                }
                placement.translationX = -WidgetPlacement.dpDimension(mContext, R.dimen.multi_window_padding);
                placement.translationY = 0.0f;
                placement.translationZ = 0.0f;
                break;
            case RIGHT:
                placement.anchorX = 0.0f;
                placement.anchorY = 0.0f;
                placement.parentAnchorX = 1.0f;
                placement.parentAnchorY = 0.0f;
                placement.rotationAxisX = 0;
                placement.rotationAxisZ = 0;
                if (curvedMode) {
                    placement.rotationAxisY = 0;
                    placement.rotation = 0;
                } else {
                    placement.rotationAxisY = 1.0f;
                    placement.rotation = (float) Math.toRadians(-WidgetPlacement.floatDimension(mContext, R.dimen.multi_window_angle));
                }

                placement.translationX = WidgetPlacement.dpDimension(mContext, R.dimen.multi_window_padding);
                placement.translationY = 0.0f;
                placement.translationZ = 0.0f;
        }
    }

    public void updateCurvedMode(boolean force) {
        float density = SettingsStore.getInstance(mContext).getCylinderDensity();
        boolean storedCurvedMode = density > 0.0f;
        boolean forcedCurvedMode = getCurrentWindows().size() > 1;

        if (force) {
            boolean curved = forcedCurvedMode || storedCurvedMode;

            for (WindowWidget window : getCurrentWindows()) {
                placeWindow(window, window.getWindowPlacement(), curved);
            }
            updateViews();
            mWidgetManager.setCylinderDensity(curved ? SettingsStore.CYLINDER_DENSITY_ENABLED_DEFAULT : density);

        } else if ((storedCurvedMode != mStoredCurvedMode) || (forcedCurvedMode != mForcedCurvedMode)) {
            mStoredCurvedMode = storedCurvedMode;
            mForcedCurvedMode = forcedCurvedMode;

            boolean curved = mStoredCurvedMode || mForcedCurvedMode;
            for (WindowWidget window : getCurrentWindows()) {
                placeWindow(window, window.getWindowPlacement(), curved);
            }
            updateViews();
            mWidgetManager.setCylinderDensity(curved ? SettingsStore.CYLINDER_DENSITY_ENABLED_DEFAULT : density);
        }
    }

    public int getWindowsCount() {
        return getCurrentWindows().size();
    }

    public boolean canOpenNewWindow() {
        return getWindowsCount() < MAX_WINDOWS;
    }

    private void switchTopBars(WindowWidget w1, WindowWidget w2) {
        // Used to fix a minor visual glitch.
        // See https://github.com/MozillaReality/FirefoxReality/issues/1722
        TopBarWidget bar1 = w1.getTopBar();
        TopBarWidget bar2 = w2.getTopBar();
        w1.setTopBar(bar2);
        w2.setTopBar(bar1);
    }

    private void updateViews() {
        WindowWidget frontWindow = getFrontWindow();
        WindowWidget leftWindow = getLeftWindow();
        WindowWidget rightWindow = getRightWindow();
        // Make sure that left or right window have the correct parent
        if (frontWindow != null && leftWindow != null) {
            leftWindow.getPlacement().parentHandle = frontWindow.getHandle();
        }
        if (frontWindow != null &&  rightWindow != null) {
            rightWindow.getPlacement().parentHandle = frontWindow.getHandle();
        }
        if (frontWindow != null) {
            frontWindow.getPlacement().parentHandle = -1;
        }

        ArrayList<WindowWidget> windows = getCurrentWindows();
        for (WindowWidget window: windows) {
            window.setIsOnlyWindow(windows.size() == 1);
        }

        // Sort windows so frontWindow is the first one. Required for proper native matrix updates.
        windows.sort((o1, o2) -> o1 == frontWindow ? -1 : 0);
        for (WindowWidget window: getCurrentWindows()) {
            mWidgetManager.updateWidget(window);
            mWidgetManager.updateWidget(window.getTopBar());
            mWidgetManager.updateWidget(window.getTitleBar());
        }
    }

    @NonNull
    private WindowWidget createWindow(@Nullable Session aSession) {
        int newWindowId = sIndex++;
        WindowWidget window;
        if (aSession != null) {
            window = new WindowWidget(mContext, newWindowId, aSession);
        } else {
            window = new WindowWidget(mContext, newWindowId, mPrivateMode);
        }

        window.addWindowListener(this);
        getCurrentWindows().add(window);
        window.getTopBar().setDelegate(this);
        window.getTitleBar().setDelegate(this);

        if (mPrivateMode) {
            GleanMetricsService.openWindowsEvent(mPrivateWindows.size() - 1, mPrivateWindows.size(), true);
        } else {
            GleanMetricsService.openWindowsEvent(mRegularWindows.size() - 1, mRegularWindows.size(), false);
        }

        mForcedCurvedMode = getCurrentWindows().size() > 1;

        return window;
    }

    public void enterResizeMode() {
        if (mFullscreenWindow == null) {
            for (WindowWidget window : getCurrentWindows()) {
                window.setResizeMode(true);
            }
        }
    }

    public void exitResizeMode() {
        if (mFullscreenWindow == null) {
            for (WindowWidget window : getCurrentWindows()) {
                window.setResizeMode(false);
            }
        }
    }

    private AccountObserver mAccountObserver = new AccountObserver() {
        @Override
        public void onLoggedOut() {

        }

        @Override
        public void onAuthenticated(@NonNull OAuthAccount oAuthAccount, @NonNull AuthType authType) {
            if (authType == AuthType.Signin.INSTANCE || authType == AuthType.Signup.INSTANCE) {
                UIDialog.closeAllDialogs();

                Session fxaSession = SessionStore.get().getSession(mAccounts.getOriginSessionId());
                if (fxaSession != null) {
                    Session originSession = SessionStore.get().getSession(fxaSession.getId());
                    closeTabs(Collections.singletonList(originSession), fxaSession.isPrivateMode(), true);
                    addTab(getFocusedWindow(), mAccounts.getConnectionSuccessURL());
                }

                switch (mAccounts.getLoginOrigin()) {
                    case BOOKMARKS:
                        mFocusedWindow.getSession().loadUri(UrlUtils.ABOUT_BOOKMARKS);
                        break;

                    case HISTORY:
                        mFocusedWindow.getSession().loadUri(UrlUtils.ABOUT_HISTORY);
                        break;

                    case SETTINGS:
                        mWidgetManager.getTray().showSettingsDialog(FXA);
                        break;
                }
            }
        }

        @Override
        public void onProfileUpdated(@NonNull Profile profile) {

        }

        @Override
        public void onAuthenticationProblems() {

        }

        @Override
        public void onFlowError(@NotNull AuthFlowError authFlowError) {

        }

    };

    // Tray Listener

    @Override
    public void onPrivateBrowsingClicked() {
        if (mPrivateMode) {
            exitPrivateMode();
        } else {
            enterPrivateMode();
        }
    }

    @Override
    public void onAddWindowClicked() {
        WindowWidget window = addWindow();
        if (window != null) {
            window.loadHome();
        }
    }

    @Override
    public void onLibraryClicked() {
        if (mDownloadsManager.isDownloading()) {
            mFocusedWindow.switchPanel(Windows.DOWNLOADS);

        } else {
            mFocusedWindow.switchPanel(Windows.NONE);
        }
    }

    @Override
    public void onTabsClicked() {
        if (mTabsWidget == null) {
            mTabsWidget = new TabsWidget(mContext);
            mTabsWidget.setTabDelegate(this);
        }

        if (mFocusedWindow != null) {
            mTabsWidget.getPlacement().parentHandle = mFocusedWindow.getHandle();
            mTabsWidget.attachToWindow(mFocusedWindow);
            mTabsWidget.show(UIWidget.KEEP_FOCUS);
            // If we're signed-in, poll for any new device events (e.g. received tabs)
            // There's no push support right now, so this helps with the perception of speedy tab delivery.
            ((VRBrowserApplication)mContext.getApplicationContext()).getAccounts().refreshDevicesAsync();
            ((VRBrowserApplication)mContext.getApplicationContext()).getAccounts().pollForEventsAsync();
        }

        // Capture active session snapshots when showing the tabs menu
        for (WindowWidget window: getCurrentWindows()) {
            window.captureImage();
        }
    }

    private void setFirstPaint(@NonNull final WindowWidget aWindow, @NonNull final Session aSession) {
        if (aSession.getGeckoSession() == null) {
            aWindow.waitForFirstPaint();
        } else {
            // If the new session has a GeckoSession there won't be a first paint event.
            // So trigger the first paint callback in case the window is grayed out
            // waiting for the first paint event.
            aWindow.onFirstContentfulPaint(aSession.getGeckoSession());
        }
    }

    // TopBarWidget Delegate
    @Override
    public void onCloseClicked(TopBarWidget aWidget) {
        WindowWidget window = aWidget.getAttachedWindow();
        if (window != null) {
            closeWindow(window);
        }
    }

    @Override
    public void onMoveLeftClicked(TopBarWidget aWidget) {
        WindowWidget window = aWidget.getAttachedWindow();
        if (window != null) {
            GleanMetricsService.windowsMoveEvent();
            moveWindowLeft(window);
        }
    }

    @Override
    public void onMoveRightClicked(TopBarWidget aWidget) {
        WindowWidget window = aWidget.getAttachedWindow();
        if (window != null) {
            GleanMetricsService.windowsMoveEvent();

            moveWindowRight(window);
        }
    }

    // Title Bar Delegate
    @Override
    public void onTitleClicked(@NonNull TitleBarWidget titleBar) {
        if (titleBar.getAttachedWindow() != null) {
            focusWindow(titleBar.getAttachedWindow());
        }
    }

    @Override
    public void onMediaPlayClicked(@NonNull TitleBarWidget titleBar) {
        for (WindowWidget window : getCurrentWindows()) {
            if (window.getTitleBar() == titleBar &&
                    window.getSession() != null &&
                    window.getSession().getActiveVideo() != null) {
                window.getSession().getActiveVideo().play();
            }
        }
    }

    @Override
    public void onMediaPauseClicked(@NonNull TitleBarWidget titleBar) {
        for (WindowWidget window : getCurrentWindows()) {
            if (window.getTitleBar() == titleBar &&
                    window.getSession() != null &&
                    window.getSession().getActiveVideo() != null) {
                window.getSession().getActiveVideo().pause();
            }
        }
    }

    private void setFullScreenSize(WindowWidget aWindow) {
        final float minScale = WidgetPlacement.floatDimension(mContext, R.dimen.window_fullscreen_min_scale);
        // Set browser fullscreen size
        float aspect = SettingsStore.getInstance(mContext).getWindowAspect();
        Session session = mFocusedWindow.getSession();
        if (session == null) {
            return;
        }
        Media media = session.getFullScreenVideo();
        if (media != null && media.getWidth() > 0 && media.getHeight() > 0) {
            aspect = (float)media.getWidth() / (float)media.getHeight();
        }
        float scale = aWindow.getCurrentScale();
        // Enforce min fullscreen size.
        // If current window area is larger only resize if the aspect changes (e.g. media).
        if (scale < minScale || aspect != aWindow.getCurrentAspect()) {
            aWindow.resizeByMultiplier(aspect, Math.max(scale, minScale));
        }
    }

    @Nullable
    private WindowWidget getWindowWithSession(GeckoSession aSession) {
        for (WindowWidget window: getCurrentWindows()) {
            if (window.getSession().getGeckoSession() == aSession) {
                return window;
            }
        }
        return null;
    }

    @Nullable
    private WindowWidget getWindowWithSession(Session aSession, boolean privateMode) {
        for (WindowWidget window: getCurrentWindows(privateMode)) {
            if (window.getSession() == aSession) {
                return window;
            }
        }
        return null;
    }

    @Nullable
    private WindowWidget getWindowWithSession(Session aSession) {
        return getWindowWithSession(aSession, mPrivateMode);
    }

    // WindowWidget.Delegate
    @Override
    public void onFocusRequest(@NonNull WindowWidget aWindow) {
        focusWindow(aWindow);
    }

    @Override
    public void onBorderChanged(@NonNull WindowWidget aWindow) {
        if (mDelegate != null) {
            mDelegate.onWindowBorderChanged(aWindow);
        }
    }

    @Override
    public void onVideoAvailabilityChanged(@NonNull WindowWidget aWindow) {
        if (mDelegate != null) {
            mDelegate.onWindowVideoAvailabilityChanged(aWindow);
        }
    }

    @Override
    public void onFullScreen(@NonNull WindowWidget aWindow, boolean aFullScreen) {
        if (aFullScreen) {
            mFullscreenWindow = aWindow;
            aWindow.saveBeforeFullscreenPlacement();
            // Do not depend on how many windows are opened to select flat/curved when entering fullscreen.
            boolean fullscreenCurved = SettingsStore.getInstance(mContext).isCurvedModeEnabled() && (mStoredCurvedMode || mForcedCurvedMode);
            aWindow.getPlacement().cylinder = fullscreenCurved;
            setFullScreenSize(aWindow);
            placeWindow(aWindow, WindowPlacement.FRONT, fullscreenCurved);
            focusWindow(aWindow);
            for (WindowWidget win: getCurrentWindows()) {
                setWindowVisible(win, win == mFullscreenWindow);
            }
            updateMaxWindowScales();
            updateViews();
        } else if (mFullscreenWindow != null) {
            aWindow.restoreBeforeFullscreenPlacement();
            mFullscreenWindow = null;
            for (WindowWidget win : getCurrentWindows()) {
                setWindowVisible(win, true);
            }
            updateMaxWindowScales();
            updateViews();
        }
    }

public void selectTab(@NonNull Session aTab) {
        onTabSelect(aTab);
    }

    @Override
    public void onTabSelect(Session aTab) {
        if (mFocusedWindow.getSession() != aTab) {
            GleanMetricsService.Tabs.activatedEvent();
        }

        WindowWidget targetWindow = mFocusedWindow;
        WindowWidget windowToMove = getWindowWithSession(aTab);
        if (windowToMove != null && windowToMove != targetWindow) {
            // Move session between windows
            Session moveFrom = windowToMove.getSession();
            Session moveTo = targetWindow.getSession();
            moveFrom.surfaceDestroyed();
            moveTo.surfaceDestroyed();
            windowToMove.setSession(moveTo, WindowWidget.SESSION_DO_NOT_RELEASE_DISPLAY, WindowWidget.LEAVE_CURRENT_SESSION_ACTIVE);
            targetWindow.setSession(moveFrom, WindowWidget.SESSION_DO_NOT_RELEASE_DISPLAY, WindowWidget.LEAVE_CURRENT_SESSION_ACTIVE);
            windowToMove.setActiveWindow(false);
            targetWindow.setActiveWindow(true);

        } else {
            setFirstPaint(targetWindow, aTab);
            // The Web Extensions require an active target session so we need to make sure we always keep the
            // Web Extension target session active when switching tabs.
            Session currentWindowSession = targetWindow.getSession();
            Session popUpSession = ComponentsAdapter.get().getStore().getState().getExtensions().values().stream()
                    .filter(extensionState -> extensionState.getPopupSession() != null)
                    .map(extensionState -> ((GeckoEngineSession) extensionState.getPopupSession()).getSession())
                    .findFirst().orElse(null);
            Session parentPopupSession = null;
            if (popUpSession != null) {
                parentPopupSession = SessionStore.get().getSession(popUpSession.getSessionState().mParentId);
            }
            targetWindow.setSession(aTab,
                    (popUpSession == null && aTab.isWebExtensionSession()) || parentPopupSession == currentWindowSession ?
                            WindowWidget.LEAVE_CURRENT_SESSION_ACTIVE :
                            WindowWidget.DEACTIVATE_CURRENT_SESSION);
        }
    }

    public void addTab(WindowWidget targetWindow) {
        addTab(targetWindow, null);
    }

    public void openNewTabAfterRestore(@NonNull String aUri, @NewTabLocation int aLocation) {
        if (mAfterRestore) {
            openNewTab(aUri, aLocation);
            saveState();
        } else {
            mAddedTabUri = aUri;
            mAddedTabLocation = aLocation;
            if (DeviceType.isHVRBuild()) {
                /*
                 * HVR usually shows the room setup when receiving an intent.
                 * This causes the app to be restarted and the tab to restore is not processed.
                 * We save it onto settings to recover it from this situation.
                 */
                SettingsStore.getInstance(mContext).setTabAfterRestore(aUri);
            }
        }
    }

    private void openNewTab(@NonNull String aUri, @NewTabLocation int aLocation) {
        if (aLocation == OPEN_IN_NEW_WINDOW) {
            WindowWidget newWindow = addWindow();
            if ((newWindow != null) && (newWindow.getSession() != null)) {
                newWindow.getSession().loadUri(aUri);
            }
        } else if (mFocusedWindow != null) {
            if (aLocation == OPEN_IN_FOREGROUND) {
                addTab(mFocusedWindow, aUri);
            } else if (aLocation == OPEN_IN_BACKGROUND) {
                addBackgroundTab(mFocusedWindow, aUri);
            }
        }
    }

    public void addTab(@NonNull WindowWidget targetWindow, @Nullable String aUri) {
        Session session = SessionStore.get().createSuspendedSession(aUri, targetWindow.getSession().isPrivateMode());
        session.setParentSession(targetWindow.getSession());
        setFirstPaint(targetWindow, session);
        targetWindow.setSession(session, WindowWidget.DEACTIVATE_CURRENT_SESSION);
        if (aUri == null || aUri.isEmpty()) {
            session.loadHomePage();
        }
    }

    public void addBackgroundTab(WindowWidget targetWindow, String aUri) {
        Session session = SessionStore.get().createSuspendedSession(aUri, targetWindow.getSession().isPrivateMode());
        session.updateLastUse();
        mFocusedWindow.getSession().updateLastUse();
        showTabAddedNotification();
    }

    @Override
    public void onTabAdd() {
        addTab(mFocusedWindow);
        GleanMetricsService.Tabs.openedCounter(GleanMetricsService.Tabs.TabSource.TABS_DIALOG);
    }

    @Override
    public void onTabsClose(List<Session> aTabs) {
        closeTabs(aTabs, mPrivateMode, true);
    }

    public void closeTab(@NonNull Session aTab) {
        if (isSessionFocused(aTab)) {
            closeTabs(Collections.singletonList(aTab), mPrivateMode, true);
        } else {
            closeTabs(Collections.singletonList(aTab), mPrivateMode, false);
        }
    }

    private void closeTabs(List<Session> aTabs, boolean privateMode, boolean hidePanel) {
        WindowWidget targetWindow = mFocusedWindow;
        // Prepare available tabs to choose from
        ArrayList<Session> available = SessionStore.get().getSortedSessions(privateMode);
        available.removeAll(aTabs);
        available.removeIf(session -> getWindowWithSession(session, privateMode) != null);

        // Sort windows by priority to take an available tab
        WindowWidget front = getFrontWindow(privateMode);
        ArrayList<WindowWidget> windows =  getCurrentWindows(privateMode);
        windows.sort((w1, w2) -> {
            // Max priority for the target window
            if (w1 == targetWindow) {
                return -1;
            }
            if (w2 == targetWindow) {
                return 1;
            }
            // Front window has next max priority
            if (w1 == front) {
                return -1;
            }
            if (w2 == front) {
                return 1;
            }
            return 0;
        });

        // Take tabs for each window
        for (WindowWidget window: windows) {
            if (!aTabs.contains(window.getSession())) {
                // Window already contains a no closed tab
                continue;
            }
            if (available.size() > 0) {
                // Window contains a closed tab and we have a tab available from the list
                Session tab = available.get(0);
                if (tab != null) {
                    setFirstPaint(window, tab);
                    window.setSession(tab, WindowWidget.LEAVE_CURRENT_SESSION_ACTIVE, hidePanel);
                }

                available.remove(0);
            } else {
                // We don't have more tabs available for the front window, load home.
                addTab(window);
            }
        }

        BitmapCache cache = BitmapCache.getInstance(mContext);
        for (Session session: aTabs) {
            cache.removeBitmap(session.getId());
            SessionStore.get().destroySession(session);
        }
    }

    @Override
    public void onTabsReceived(@NonNull List<TabData> aTabs) {
        WindowWidget targetWindow = mFocusedWindow;

        boolean fullscreen = targetWindow.getSession().isInFullScreen();
        for (int i = aTabs.size() - 1; i >= 0; --i) {
            Session session = SessionStore.get().createSession(targetWindow.getSession().isPrivateMode());
            // Cache the provided data to avoid delays if the tabs are loaded at the same time the
            // tabs panel is shown.
            session.getSessionState().mTitle = aTabs.get(i).getTitle();
            session.getSessionState().mUri = aTabs.get(i).getUrl();
            session.loadUri(aTabs.get(i).getUrl());
            session.updateLastUse();

            GleanMetricsService.Tabs.openedCounter(GleanMetricsService.Tabs.TabSource.RECEIVED);

            if (i == 0 && !fullscreen) {
                // Set the first received tab of the list the current one.
                targetWindow.setSession(session, WindowWidget.DEACTIVATE_CURRENT_SESSION);
            }
        }

        if (!fullscreen) {
            showTabAddedNotification();
        }

        if (mTabsWidget != null && mTabsWidget.isVisible()) {
            mTabsWidget.refreshTabs();
        }
    }

    private ConnectivityReceiver.Delegate mConnectivityDelegate = connected -> {
        if (mNoInternetDialog == null) {
            mNoInternetDialog = new PromptDialogWidget(mContext);
            mNoInternetDialog.setButtons(new int[] {
                    R.string.ok_button
            });
            mNoInternetDialog.setCheckboxVisible(false);
            mNoInternetDialog.setDescriptionVisible(false);
            mNoInternetDialog.setTitle(R.string.no_internet_title);
            mNoInternetDialog.setBody(R.string.no_internet_message);
            mNoInternetDialog.setButtonsDelegate((index, isChecked) -> {
                mNoInternetDialog.hide(UIWidget.REMOVE_WIDGET);
                mNoInternetDialog.releaseWidget();
                mNoInternetDialog = null;
            });
        }

        if (!connected && !mNoInternetDialog.isVisible()) {
            mNoInternetDialog.show(UIWidget.REQUEST_FOCUS);

        } else if (connected && mNoInternetDialog.isVisible()) {
            mNoInternetDialog.hide(UIWidget.REMOVE_WIDGET);
            mNoInternetDialog.releaseWidget();
            mNoInternetDialog = null;
        }
    };

    public void showTabAddedNotification() {
        if (mFocusedWindow.isFullScreen()) {
            mWidgetManager.getNavigationBar().showTabAddedNotification();

        } else {
            if (mWidgetManager.getTray().isVisible()) {
                mWidgetManager.getTray().showTabAddedNotification();

            } else {
                NotificationManager.Notification notification = new NotificationManager.Builder(mFocusedWindow)
                        .withString(R.string.tab_added_notification)
                        .withZTranslation(25.0f)
                        .withCurved(true).build();
                NotificationManager.show(TAB_ADDED_NOTIFICATION_ID, notification);
            }
        }

    }

    public void showTabSentNotification() {
        if (mFocusedWindow.isFullScreen()) {
            mWidgetManager.getNavigationBar().showTabSentNotification();

        } else {
            if (mWidgetManager.getTray().isVisible()) {
                mWidgetManager.getTray().showTabSentNotification();

            } else {
                NotificationManager.Notification notification = new NotificationManager.Builder(mFocusedWindow)
                        .withString(R.string.tab_sent_notification)
                        .withZTranslation(25.0f)
                        .withCurved(true).build();
                NotificationManager.show(TAB_SENT_NOTIFICATION_ID, notification);
            }
        }
    }

    public void showBookmarkAddedNotification() {
        if (mFocusedWindow.isFullScreen()) {
            mWidgetManager.getNavigationBar().showBookmarkAddedNotification();

        } else {
            if (mWidgetManager.getTray().isVisible()) {
                mWidgetManager.getTray().showBookmarkAddedNotification();

            } else {
                NotificationManager.Notification notification = new NotificationManager.Builder(mFocusedWindow)
                        .withString(R.string.bookmarks_saved_notification)
                        .withZTranslation(25.0f)
                        .withCurved(true).build();
                NotificationManager.show(BOOKMARK_ADDED_NOTIFICATION_ID, notification);
            }
        }
    }

    public boolean isSessionFocused(@NonNull Session session) {
        return mRegularWindows.stream().anyMatch(window -> window.getSession() == session && !window.isLibraryVisible() && session.isPrivateMode() == mPrivateMode) ||
                mPrivateWindows.stream().anyMatch(window -> window.getSession() == session && !window.isLibraryVisible() && session.isPrivateMode() == mPrivateMode );
    }
}
