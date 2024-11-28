package com.igalia.wolvic.ui.widgets;

import android.content.Context;
import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.ui.widgets.menus.VideoProjectionMenuWidget;

public interface WidgetManagerDelegate {

    interface UpdateListener {
        void onWidgetUpdate(Widget aWidget);
    }

    interface PermissionListener {
        void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults);
    }

    interface FocusChangeListener {
        void onGlobalFocusChanged(View oldFocus, View newFocus);
    }

    interface WorldClickListener {
        // Indicates that the user has clicked on the world, outside of any UI widgets
        void onWorldClick();
    }

    interface WebXRListener {
        void onEnterWebXR();
        void onExitWebXR();
        void onDismissWebXRInterstitial();
        void onWebXRRenderStateChange(boolean aRendering);
    }

    interface EyeTrackingCallback {
        void onEyeTrackingPermissionRequest(boolean aPermissionGranted);
    }

    float DEFAULT_DIM_BRIGHTNESS = 0.25f;
    float DEFAULT_NO_DIM_BRIGHTNESS = 1.0f;


    @IntDef(value = { WIDGET_MOVE_BEHAVIOUR_GENERAL, WIDGET_MOVE_BEHAVIOUR_KEYBOARD, WIDGET_MOVE_BEHAVIOUR_WINDOW})
    public @interface WidgetMoveBehaviourFlags {}
    public static final int WIDGET_MOVE_BEHAVIOUR_GENERAL = 0;
    public static final int WIDGET_MOVE_BEHAVIOUR_KEYBOARD = 1;
    public static final int WIDGET_MOVE_BEHAVIOUR_WINDOW = 2;

    @IntDef(value = { CPU_LEVEL_NORMAL, CPU_LEVEL_HIGH})
    @interface CPULevelFlags {}
    int CPU_LEVEL_NORMAL = 0;
    int CPU_LEVEL_HIGH = 1;

    @IntDef(value = { WEBXR_INTERSTITIAL_FORCED, WEBXR_INTERSTITIAL_ALLOW_DISMISS, WEBXR_INTERSTITIAL_HIDDEN})
    @interface WebXRInterstitialState {}
    int WEBXR_INTERSTITIAL_FORCED = 0;
    int WEBXR_INTERSTITIAL_ALLOW_DISMISS = 1;
    int WEBXR_INTERSTITIAL_HIDDEN = 2;

    @IntDef(value = { YAW_TARGET_ALL, YAW_TARGET_WIDGETS})
    @interface YawTarget {}
    int YAW_TARGET_ALL = 0; // Targets widgets and VR videos.
    int YAW_TARGET_WIDGETS = 1; // Targets widgets only.

    // Keep in sync with DeviceDelegate.h
    @IntDef(value = { TRACKED_POINTER, TRACKED_EYE })
    @interface PointerMode {}
    int TRACKED_POINTER = 0;
    int TRACKED_EYE = 1;

    @IntDef(value = { NO_LOCK, HEAD_LOCK, CONTROLLER_LOCK})
    @interface LockMode {}
    int NO_LOCK = 0;
    int HEAD_LOCK = 1;
    int CONTROLLER_LOCK = 2;

    enum OriginatorType {WEBSITE, APPLICATION}

    int newWidgetHandle();
    void addWidget(Widget aWidget);
    void updateWidget(Widget aWidget);
    void removeWidget(Widget aWidget);
    void updateWidgetsPlacementTranslationZ();
    void updateVisibleWidgets();
    void recreateWidgetSurface(Widget aWidget);
    void startWidgetResize(WindowWidget aWidget);
    void finishWidgetResize(WindowWidget aWidget);
    void startWidgetMove(Widget aWidget, @WidgetMoveBehaviourFlags int aMoveBehaviour);
    void finishWidgetMove();
    void addUpdateListener(@NonNull UpdateListener aUpdateListener);
    void removeUpdateListener(@NonNull UpdateListener aUpdateListener);
    void pushBackHandler(@NonNull Runnable aRunnable);
    void popBackHandler(@NonNull Runnable aRunnable);
    void pushWorldBrightness(Object aKey, float aBrightness);
    void setWorldBrightness(Object aKey, float aBrightness);
    void popWorldBrightness(Object aKey);
    void triggerHapticFeedback(int deviceID);
    void setControllersVisible(boolean visible);
    void keyboardDismissed();
    void updateEnvironment();
    void updatePointerColor();
    void updateKeyboardDictionary();
    void showVRVideo(int aWindowHandle, @VideoProjectionMenuWidget.VideoProjectionFlags int aVideoProjection);
    void hideVRVideo();
    void togglePassthrough();
    boolean isPassthroughEnabled();
    boolean isPassthroughSupported();
    boolean isPageZoomEnabled();
    void setLockMode(@LockMode int lockMode);
    void recenterUIYaw(@YawTarget int target);
    void setCylinderDensity(float aDensity);
    void setCylinderDensityForce(float aDensity);
    void setCenterWindows(boolean isCenterWindows);
    float getCylinderDensity();
    void addFocusChangeListener(@NonNull FocusChangeListener aListener);
    void removeFocusChangeListener(@NonNull FocusChangeListener aListener);
    void addPermissionListener(PermissionListener aListener);
    void removePermissionListener(PermissionListener aListener);
    void addWorldClickListener(WorldClickListener aListener);
    void removeWorldClickListener(WorldClickListener aListener);
    void addWebXRListener(WebXRListener aListener);
    void removeWebXRListener(WebXRListener aListener);
    void setWebXRIntersitialState(@WebXRInterstitialState int aState);
    boolean isWebXRIntersitialHidden();
    boolean isWebXRPresenting();
    boolean isPermissionGranted(@NonNull String permission);
    boolean isLaunchImmersive();
    void requestPermission(String originator, @NonNull String permission, OriginatorType originatorType, WSession.PermissionDelegate.Callback aCallback);
    boolean canOpenNewWindow();
    void openNewWindow(String uri);
    void openNewTab(@NonNull String uri);
    void openNewTabForeground(@NonNull String uri);
    void openNewPageNoInterrupt(@NonNull String uri);
    WindowWidget getFocusedWindow();
    TrayWidget getTray();
    NavigationBarWidget getNavigationBar();
    Windows getWindows();
    void saveState();
    void updateLocale(@NonNull Context context);
    @NonNull
    AppServicesProvider getServicesProvider();
    KeyboardWidget getKeyboard();
    void setPointerMode(@PointerMode int mode);
    void setHandTrackingEnabled(boolean value);
    boolean isHandTrackingEnabled();
    void checkEyeTrackingPermissions(@NonNull EyeTrackingCallback callback);
    boolean isEyeTrackingSupported();
    boolean isHandTrackingSupported();
    boolean areControllersAvailable();

}
