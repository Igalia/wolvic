package org.mozilla.vrbrowser.ui.widgets;

import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.ui.widgets.menus.VideoProjectionMenuWidget;
import org.mozilla.vrbrowser.utils.ConnectivityReceiver;

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

    float DEFAULT_DIM_BRIGHTNESS = 0.25f;
    float DEFAULT_NO_DIM_BRIGHTNESS = 1.0f;


    @IntDef(value = { WIDGET_MOVE_BEHAVIOUR_GENERAL, WIDGET_MOVE_BEHAVIOUR_KEYBOARD})
    public @interface WidgetMoveBehaviourFlags {}
    public static final int WIDGET_MOVE_BEHAVIOUR_GENERAL = 0;
    public static final int WIDGET_MOVE_BEHAVIOUR_KEYBOARD = 1;

    @IntDef(value = { CPU_LEVEL_NORMAL, CPU_LEVEL_HIGH})
    @interface CPULevelFlags {}
    int CPU_LEVEL_NORMAL = 0;
    int CPU_LEVEL_HIGH = 1;

    int newWidgetHandle();
    void addWidget(@NonNull Widget aWidget);
    void updateWidget(@NonNull Widget aWidget);
    void removeWidget(@NonNull Widget aWidget);
    void updateVisibleWidgets();
    void startWidgetResize(@NonNull Widget aWidget, float maxWidth, float maxHeight, float minWidth, float minHeight);
    void finishWidgetResize(@NonNull Widget aWidget);
    void startWidgetMove(@NonNull Widget aWidget, @WidgetMoveBehaviourFlags int aMoveBehaviour);
    void finishWidgetMove();
    void addUpdateListener(@NonNull UpdateListener aUpdateListener);
    void removeUpdateListener(@NonNull UpdateListener aUpdateListener);
    void pushBackHandler(@NonNull Runnable aRunnable);
    void popBackHandler(@NonNull Runnable aRunnable);
    void pushWorldBrightness(Object aKey, float aBrightness);
    void setWorldBrightness(Object aKey, float aBrightness);
    void popWorldBrightness(Object aKey);
    void setTrayVisible(boolean visible);
    void setControllersVisible(boolean visible);
    void setWindowSize(float targetWidth, float targetHeight);
    void setIsServoSession(boolean aIsServo);
    void keyboardDismissed();
    void updateEnvironment();
    void updateFoveatedLevel();
    void updatePointerColor();
    void showVRVideo(int aWindowHandle, @VideoProjectionMenuWidget.VideoProjectionFlags int aVideoProjection);
    void hideVRVideo();
    void resetUIYaw();
    void setCylinderDensity(float aDensity);
    float getCylinderDensity();
    void addFocusChangeListener(@NonNull FocusChangeListener aListener);
    void removeFocusChangeListener(@NonNull FocusChangeListener aListener);
    void addPermissionListener(PermissionListener aListener);
    void removePermissionListener(PermissionListener aListener);
    void addWorldClickListener(WorldClickListener aListener);
    void removeWorldClickListener(WorldClickListener aListener);
    boolean isPermissionGranted(@NonNull String permission);
    void requestPermission(String uri, @NonNull String permission, GeckoSession.PermissionDelegate.Callback aCallback);
    boolean canOpenNewWindow();
    void openNewWindow(@NonNull String uri);
    void openNewTab(@NonNull String uri);
    void openNewTabForeground(@NonNull String uri);
    WindowWidget getFocusedWindow();
    TrayWidget getTray();
    void addConnectivityListener(ConnectivityReceiver.Delegate aListener);
    void removeConnectivityListener(ConnectivityReceiver.Delegate aListener);
    void saveState();
}
