package org.mozilla.vrbrowser.ui.widgets;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.engine.SessionStack;
import org.mozilla.vrbrowser.telemetry.TelemetryWrapper;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;

public class Windows implements TrayListener, TopBarWidget.Delegate, GeckoSession.ContentDelegate {

    private static final String WINDOWS_SAVE_FILENAME = "windows_state.json";

    class WindowState {
        WindowPlacement placement;
        SessionStack sessionStack;
        int currentSessionId;
        int textureWidth;
        int textureHeight;
        float worldWidth;

        public void load(WindowWidget aWindow) {
            placement = aWindow.getWindowPlacement();
            sessionStack = aWindow.getSessionStack();
            currentSessionId = aWindow.getSessionStack().getCurrentSessionId();
            textureWidth = aWindow.getPlacement().width;
            textureHeight = aWindow.getPlacement().height;
            worldWidth = aWindow.getPlacement().worldWidth;
        }
    }

    class WindowsState {
        WindowPlacement focusedWindowPlacement = WindowPlacement.FRONT;
        ArrayList<WindowState> regularWindowsState = new ArrayList<>();
        ArrayList<WindowState> privateWindowsState = new ArrayList<>();
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
    private WindowPlacement mPrevWindowPlacement;
    private boolean mCurvedMode = false;

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
    }

    public Windows(Context aContext) {
        mContext = aContext;
        mWidgetManager = (WidgetManagerDelegate) aContext;
        mRegularWindows = new ArrayList<>();
        mPrivateWindows = new ArrayList<>();

        restoreWindows();
    }

    private void saveState() {
        File file = new File(mContext.getFilesDir(), WINDOWS_SAVE_FILENAME);
        try (Writer writer = new FileWriter(file)) {
            WindowsState state = new WindowsState();
            state.privateMode = mPrivateMode;
            state.focusedWindowPlacement = mFocusedWindow.getWindowPlacement();
            state.regularWindowsState = new ArrayList<>();
            for (WindowWidget window : mRegularWindows) {
                WindowState windowState = new WindowState();
                windowState.load(window);
                state.regularWindowsState.add(windowState);
            }
            for (WindowWidget window : mPrivateWindows) {
                WindowState windowState = new WindowState();
                windowState.load(window);
                state.privateWindowsState.add(windowState);
            }
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(state, writer);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private WindowsState restoreState() {
        WindowsState restored = null;

        File file = new File(mContext.getFilesDir(), WINDOWS_SAVE_FILENAME);
        try (Reader reader = new FileReader(file)) {
            Gson gson = new GsonBuilder().create();
            Type type = new TypeToken<WindowsState>() {}.getType();
            restored = gson.fromJson(reader, type);

        } catch (IOException e) {
            Log.w(getClass().getCanonicalName(), "Error restoring persistent windows state", e);

        } finally {
            boolean deleted = file.delete();
            if (deleted)
                Log.d(getClass().getCanonicalName(), "Persistent windows state successfully restored");
            else
                Log.d(getClass().getCanonicalName(), "Persistent window state couldn't be deleted");
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

    public WindowWidget addWindow() {
        if (getCurrentWindows().size() >= MAX_WINDOWS) {
            showMaxWindowsMessage();
            return null;
        }

        if (mFullscreenWindow != null) {
            mFullscreenWindow.getSessionStack().exitFullScreen();
            onFullScreen(mFullscreenWindow.getSessionStack().getCurrentSession(), false);
        }

        WindowWidget frontWindow = getFrontWindow();
        WindowWidget leftWindow = getLeftWindow();
        WindowWidget rightWindow = getRightWindow();

        WindowWidget newWindow = createWindow();
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
            placeWindow(newWindow, WindowPlacement.RIGHT);
        } else if (rightWindow != null && focusedWindow == rightWindow) {
            // Opening a new window from right window
            placeWindow(newWindow, WindowPlacement.FRONT);
            placeWindow(frontWindow, WindowPlacement.LEFT);
        } else if (rightWindow != null && focusedWindow == frontWindow) {
            // Opening a new window from right window
            placeWindow(newWindow, WindowPlacement.LEFT);
        }

        updateMaxWindowScales();
        mWidgetManager.addWidget(newWindow);
        focusWindow(newWindow);
        updateCurvedMode();
        updateViews();
        return newWindow;
    }

    private WindowWidget addWindow(WindowState aState) {
        if (getCurrentWindows().size() >= MAX_WINDOWS) {
            showMaxWindowsMessage();
            return null;
        }

        WindowWidget newWindow = createWindow();
        newWindow.getPlacement().width = aState.textureWidth;
        newWindow.getPlacement().height = aState.textureHeight;
        newWindow.getPlacement().worldWidth = aState.worldWidth;
        newWindow.setRestored(true);
        placeWindow(newWindow, aState.placement);
        updateCurvedMode();

        mWidgetManager.addWidget(newWindow);
        return newWindow;
    }

    public void closeWindow(@NonNull WindowWidget aWindow) {
        WindowWidget frontWindow = getFrontWindow();
        WindowWidget leftWindow = getLeftWindow();
        WindowWidget rightWindow = getRightWindow();

        aWindow.hideBookmarks();

        if (leftWindow == aWindow) {
            removeWindow(leftWindow);
            if (mFocusedWindow == leftWindow) {
                focusWindow(frontWindow);
            }
        } else if (rightWindow == aWindow) {
            removeWindow(rightWindow);
            if (mFocusedWindow == rightWindow) {
                focusWindow(frontWindow);
            }
        } else if (frontWindow == aWindow) {
            removeWindow(frontWindow);
            if (rightWindow != null) {
                placeWindow(rightWindow, WindowPlacement.FRONT);
            } else if (leftWindow != null) {
                placeWindow(leftWindow, WindowPlacement.FRONT);
            }

            if (mFocusedWindow == frontWindow && !getCurrentWindows().isEmpty()) {
                focusWindow(getFrontWindow());
            }

        }

        boolean empty = getCurrentWindows().isEmpty();
        if (empty && isInPrivateMode()) {
            // Exit private mode if the only window is closed.
            exitPrivateMode();
        } else if (empty) {
            // Ensure that there is at least one window.
            WindowWidget window = addWindow();
            if (window != null)
                window.loadHome();
        }

        updateViews();
    }

    public void moveWindowRight(@NonNull WindowWidget aWindow) {
        WindowWidget frontWindow = getFrontWindow();
        WindowWidget leftWindow = getLeftWindow();
        WindowWidget rightWindow = getRightWindow();

        if (aWindow == leftWindow) {
            placeWindow(leftWindow, WindowPlacement.FRONT);
            placeWindow(frontWindow, WindowPlacement.LEFT);
        } else if (aWindow == frontWindow) {
            if (rightWindow != null) {
                placeWindow(rightWindow, WindowPlacement.FRONT);
            } else if (leftWindow != null) {
                placeWindow(leftWindow, WindowPlacement.FRONT);
            }
            placeWindow(frontWindow, WindowPlacement.RIGHT);
        }
        updateViews();
    }

    public void moveWindowLeft(@NonNull WindowWidget aWindow) {
        WindowWidget frontWindow = getFrontWindow();
        WindowWidget leftWindow = getLeftWindow();
        WindowWidget rightWindow = getRightWindow();

        if (aWindow == rightWindow) {
            placeWindow(rightWindow, WindowPlacement.FRONT);
            placeWindow(frontWindow, WindowPlacement.RIGHT);
        } else if (aWindow == frontWindow) {
            if (leftWindow != null) {
                placeWindow(leftWindow, WindowPlacement.FRONT);
            } else if (rightWindow != null) {
                placeWindow(rightWindow, WindowPlacement.FRONT);
            }
            placeWindow(frontWindow, WindowPlacement.LEFT);
        }
        updateViews();
    }

    public void focusWindow(@NonNull WindowWidget aWindow) {
        if (aWindow != mFocusedWindow) {
            WindowWidget prev = mFocusedWindow;
            mFocusedWindow = aWindow;
            if (prev != null)
                prev.setActiveWindow(false);
            mFocusedWindow.setActiveWindow(true);
            if (mDelegate != null) {
                mDelegate.onFocusedWindowChanged(mFocusedWindow, prev);
            }
        }
    }

    public void pauseCompositor() {
        for (WindowWidget window: mRegularWindows) {
            window.pauseCompositor();
        }
        for (WindowWidget window: mPrivateWindows) {
            window.pauseCompositor();
        }
    }

    public void resumeCompositor() {
        for (WindowWidget window: mRegularWindows) {
            window.resumeCompositor();
        }
        for (WindowWidget window: mPrivateWindows) {
            window.resumeCompositor();
        }
    }

    public void onStop() {
        saveState();
    }

    public void onDestroy() {
        for (WindowWidget window: mRegularWindows) {
            window.close();
        }
        for (WindowWidget window: mPrivateWindows) {
            window.close();
        }
    }

    public boolean isInPrivateMode() {
        return mPrivateMode;
    }


    public void enterPrivateMode() {
        if (mPrivateMode) {
            return;
        }
        mPrivateMode = true;
        updateCurvedMode();
        for (WindowWidget window: mRegularWindows) {
            setWindowVisible(window, false);
        }
        for (WindowWidget window: mPrivateWindows) {
            setWindowVisible(window, true);
        }

        if (mPrivateWindows.size() == 0) {
            WindowWidget window = addWindow();
            if (window != null)
                window.loadHome();

        } else {
            focusWindow(getFrontWindow());
        }
        mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);
    }

    public void exitPrivateMode() {
        if (!mPrivateMode) {
            return;
        }
        mPrivateMode = false;
        updateCurvedMode();
        for (WindowWidget window: mRegularWindows) {
            setWindowVisible(window, true);
        }
        for (WindowWidget window: mPrivateWindows) {
            setWindowVisible(window, false);
        }
        focusWindow(getFrontWindow());
        mWidgetManager.popWorldBrightness(this);
    }

    public boolean handleBack() {
        if (mFocusedWindow == null) {
            return false;
        }
        if (mFocusedWindow.getSessionStack().canGoBack()) {
            mFocusedWindow.getSessionStack().goBack();
            return true;
        } else if (isInPrivateMode()) {
            exitPrivateMode();
            return true;
        }

        return false;
    }

    private void updateMaxWindowScales() {
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

    private void showMaxWindowsMessage() {
        TelemetryWrapper.maxWindowsDialogEvent();
        mFocusedWindow.showMaxWindowsDialog(MAX_WINDOWS);
    }

    private ArrayList<WindowWidget> getCurrentWindows() {
        return mPrivateMode ? mPrivateWindows : mRegularWindows;
    }

    private WindowWidget getWindowWithPlacement(WindowPlacement aPlacement) {
        for (WindowWidget window: getCurrentWindows()) {
            if (window.getWindowPlacement() == aPlacement) {
                return window;
            }
        }
        return null;
    }

    private WindowWidget getFrontWindow() {
        if (mFullscreenWindow != null) {
            return mFullscreenWindow;
        }
        return getWindowWithPlacement(WindowPlacement.FRONT);
    }

    private WindowWidget getLeftWindow() {
        return getWindowWithPlacement(WindowPlacement.LEFT);
    }

    private WindowWidget getRightWindow() {
        return getWindowWithPlacement(WindowPlacement.RIGHT);
    }

    private void restoreWindows() {
        WindowsState windowsState = restoreState();
        if (windowsState != null) {
            for (WindowState windowState : windowsState.regularWindowsState) {
                WindowWidget window = addWindow(windowState);
                window.getSessionStack().restore(windowState.sessionStack, windowState.currentSessionId);
            }
            mPrivateMode = true;
            for (WindowState windowState : windowsState.privateWindowsState) {
                WindowWidget window = addWindow(windowState);
                window.getSessionStack().restore(windowState.sessionStack, windowState.currentSessionId);
            }
            mPrivateMode = false;
            if (windowsState.privateMode) {
                enterPrivateMode();
            }

            WindowWidget windowToFocus = getWindowWithPlacement(windowsState.focusedWindowPlacement);
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

    private void removeWindow(@NonNull WindowWidget aWindow) {
        mWidgetManager.removeWidget(aWindow);
        mRegularWindows.remove(aWindow);
        mPrivateWindows.remove(aWindow);
        aWindow.getTopBar().setVisible(false);
        aWindow.getTopBar().setDelegate((TopBarWidget.Delegate) null);
        aWindow.getSessionStack().removeContentListener(this);
        aWindow.close();
        updateMaxWindowScales();
        updateCurvedMode();

        if (aWindow.getSessionStack().isPrivateMode())
            TelemetryWrapper.openWindowsEvent(mPrivateWindows.size()+1, mPrivateWindows.size(), true);
        else
            TelemetryWrapper.openWindowsEvent(mRegularWindows.size()+1, mRegularWindows.size(), false);
    }

    private void setWindowVisible(@NonNull WindowWidget aWindow, boolean aVisible) {
        aWindow.setVisible(aVisible);
        aWindow.getTopBar().setVisible(aVisible);
    }

    private void placeWindow(@NonNull WindowWidget aWindow, WindowPlacement aPosition) {
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
                if (mCurvedMode) {
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
                if (mCurvedMode) {
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

    public void updateCurvedMode() {
        float density = SettingsStore.getInstance(mContext).getCylinderDensity();
        boolean curved = getCurrentWindows().size() > 1 ||  density > 0;
        if (curved != mCurvedMode) {
            mCurvedMode = curved;
            for (WindowWidget window: getCurrentWindows()) {
                placeWindow(window, window.getWindowPlacement());
            }
            updateViews();
            mWidgetManager.setCylinderDensity(curved ? SettingsStore.CYLINDER_DENSITY_ENABLED_DEFAULT : density);
        }
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

        updateTopBars();
        ArrayList<WindowWidget> windows = getCurrentWindows();
        // Sort windows so frontWindow is the first one. Required for proper native matrix updates.
        windows.sort((o1, o2) -> o1 == frontWindow ? -1 : 0);
        for (WindowWidget window: getCurrentWindows()) {
            mWidgetManager.updateWidget(window);
            mWidgetManager.updateWidget(window.getTopBar());
        }
    }

    private void updateTopBars() {
        ArrayList<WindowWidget> windows = getCurrentWindows();
        WindowWidget leftWindow = getLeftWindow();
        WindowWidget rightWindow = getRightWindow();
        boolean visible = mFullscreenWindow == null && (windows.size() > 1 || isInPrivateMode());
        for (WindowWidget window: windows) {
            window.getTopBar().setVisible(visible);
            if (visible) {
                window.getTopBar().setMoveLeftButtonEnabled(window != leftWindow);
                window.getTopBar().setMoveRightButtonEnabled(window != rightWindow);
            }
        }
        if (isInPrivateMode() && mPrivateWindows.size() == 1) {
            mFocusedWindow.getTopBar().setMoveLeftButtonEnabled(false);
            mFocusedWindow.getTopBar().setMoveRightButtonEnabled(false);
        }
    }

    private WindowWidget createWindow() {
        int newWindowId = sIndex++;
        WindowWidget window = new WindowWidget(mContext, newWindowId, mPrivateMode);
        getCurrentWindows().add(window);
        window.getTopBar().setDelegate(this);
        window.getSessionStack().addContentListener(this);

        if (mPrivateMode)
            TelemetryWrapper.openWindowsEvent(mPrivateWindows.size()-1, mPrivateWindows.size(), true);
        else
            TelemetryWrapper.openWindowsEvent(mRegularWindows.size()-1, mRegularWindows.size(), false);

        return window;
    }


    // Tray Listener
    @Override
    public void onBookmarksClicked() {
        mFocusedWindow.switchBookmarks();
    }

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
        if (window != null)
            window.loadHome();
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
            TelemetryWrapper.windowsMoveEvent();

            moveWindowLeft(window);
        }
    }

    @Override
    public void onMoveRightClicked(TopBarWidget aWidget) {
        WindowWidget window = aWidget.getAttachedWindow();
        if (window != null) {
            TelemetryWrapper.windowsMoveEvent();

            moveWindowRight(window);
        }
    }

    // Content delegate
    @Override
    public void onFullScreen(GeckoSession session, boolean aFullScreen) {
        WindowWidget window = getWindowWithSession(session);
        if (window == null) {
            return;
        }

        if (aFullScreen) {
            mFullscreenWindow = window;
            mPrevWindowPlacement = window.getWindowPlacement();
            placeWindow(window, WindowPlacement.FRONT);
            focusWindow(window);
            for (WindowWidget win: getCurrentWindows()) {
                setWindowVisible(win, win == mFullscreenWindow);
            }
            updateMaxWindowScales();
            updateViews();
        } else if (mFullscreenWindow != null) {
            placeWindow(mFullscreenWindow, mPrevWindowPlacement);
            mFullscreenWindow = null;
            for (WindowWidget win : getCurrentWindows()) {
                setWindowVisible(win, true);
            }
            updateMaxWindowScales();
            updateViews();
        }
    }

    private WindowWidget getWindowWithSession(GeckoSession aSession) {
        for (WindowWidget window: getCurrentWindows()) {
            if (window.getSessionStack().containsSession(aSession)) {
                return window;
            }
        }
        return null;
    }

}
