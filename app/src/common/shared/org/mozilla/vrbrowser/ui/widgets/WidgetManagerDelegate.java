package org.mozilla.vrbrowser.ui.widgets;

import android.view.View;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.VRBrowserActivity;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

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

    @IntDef(value = { CPU_LEVEL_NORMAL, CPU_LEVEL_HIGH})
    @interface CPULevelFlags {}
    int CPU_LEVEL_NORMAL = 0;
    int CPU_LEVEL_HIGH = 1;

    int newWidgetHandle();
    void addWidget(@NonNull Widget aWidget);
    void updateWidget(@NonNull Widget aWidget);
    void removeWidget(@NonNull Widget aWidget);
    void startWidgetResize(@NonNull Widget aWidget);
    void finishWidgetResize(@NonNull Widget aWidget);
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
    void setCPULevel(@VRBrowserActivity.CPULevelFlags int aCPULevel);
    void addFocusChangeListener(@NonNull FocusChangeListener aListener);
    void removeFocusChangeListener(@NonNull FocusChangeListener aListener);
    void addPermissionListener(PermissionListener aListener);
    void removePermissionListener(PermissionListener aListener);
    void addWorldClickListener(WorldClickListener aListener);
    void removeWorldClickListener(WorldClickListener aListener);
    boolean isPermissionGranted(@NonNull String permission);
    void requestPermission(String uri, @NonNull String permission, GeckoSession.PermissionDelegate.Callback aCallback);

}
