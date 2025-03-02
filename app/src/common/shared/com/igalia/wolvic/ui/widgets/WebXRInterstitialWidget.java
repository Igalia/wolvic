package com.igalia.wolvic.ui.widgets;

import android.content.Context;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.view.LayoutInflater;

import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.PlatformActivity;
import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.databinding.WebxrInterstitialBinding;
import com.igalia.wolvic.utils.DeviceType;
import com.igalia.wolvic.utils.ViewUtils;

import java.util.ArrayList;

public class WebXRInterstitialWidget extends UIWidget implements WidgetManagerDelegate.WebXRListener {
    private WebxrInterstitialBinding mBinding;
    private ArrayList<WebXRInterstitialController> mControllers = new ArrayList<>();
    private boolean firstEnterXR = true;
    private AnimatedVectorDrawable mSpinnerAnimation;
    private boolean mWebXRRendering = false;
    private boolean mInterstitialDismissed = false;

    public WebXRInterstitialWidget(Context aContext) {
        super(aContext);
        initialize();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        Context context = getContext();
        aPlacement.scene = WidgetPlacement.SCENE_WEBXR_INTERSTITIAL;
        aPlacement.width = WidgetPlacement.dpDimension(context, R.dimen.webxr_interstitial_width);
        aPlacement.height = WidgetPlacement.dpDimension(context, R.dimen.webxr_interstitial_height);
        aPlacement.translationY = WidgetPlacement.unitFromMeters(context, R.dimen.webxr_interstitial_world_y);
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.webxr_interstitial_world_z);
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.cylinder = false;
        aPlacement.visible = false;
        // FIXME: we temporarily disable the creation of a layer for this widget in order to
        // limit the amount of layers we create, as Pico's runtime only allows 16 at a given time.
        if (DeviceType.isPicoXR())
            aPlacement.layer = false;
    }

    private void initialize() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        mBinding = DataBindingUtil.inflate(inflater, R.layout.webxr_interstitial, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity)getContext());
        setHowToVisible(true);

        // AnimatedVectorDrawable doesn't work with a Hardware Accelerated canvas, we disable it for this view.
        setIsHardwareAccelerationEnabled(false);

        mSpinnerAnimation = (AnimatedVectorDrawable) mBinding.webxrSpinner.getDrawable();
        mWidgetManager.addWebXRListener(this);

    }

    private void setHowToVisible(boolean aShow) {
        mBinding.setShowHowTo(aShow);
        mBinding.executePendingBindings();
        mWidgetPlacement.setSizeFromMeasure(getContext(), this);
        if (aShow) {
            // Scale the widget a bit to better see the text
            mWidgetPlacement.worldWidth = mWidgetPlacement.width * WidgetPlacement.worldToDpRatio(getContext()) * 0.5f;
        } else {
            // MAke the spinner a bit smaller than the text
            mWidgetPlacement.worldWidth = mWidgetPlacement.width * WidgetPlacement.worldToDpRatio(getContext()) * 0.3f;
        }
    }

    @Override
    public void releaseWidget() {
        mWidgetManager.removeWebXRListener(this);
        super.releaseWidget();
    }

    private void addController(@DeviceType.Type int aDevice, @WebXRInterstitialController.Hand int aHand) {
        mControllers.add(new WebXRInterstitialController(getContext(), aDevice, aHand));
    }

    private void initializeControllers() {
        int deviceType = DeviceType.getType();
        if (deviceType == DeviceType.OculusGo) {
            addController(DeviceType.OculusGo, WebXRInterstitialController.HAND_NONE);
        } else if (deviceType == DeviceType.MetaQuestPro) {
            addController(DeviceType.MetaQuestPro, WebXRInterstitialController.HAND_LEFT);
            addController(DeviceType.MetaQuestPro, WebXRInterstitialController.HAND_RIGHT);
        } else if (deviceType == DeviceType.MetaQuest3) {
            addController(DeviceType.MetaQuest3, WebXRInterstitialController.HAND_LEFT);
            addController(DeviceType.MetaQuest3, WebXRInterstitialController.HAND_RIGHT);
        } else if (deviceType == DeviceType.OculusQuest || deviceType == DeviceType.OculusQuest2) {
            addController(DeviceType.OculusQuest, WebXRInterstitialController.HAND_LEFT);
            addController(DeviceType.OculusQuest, WebXRInterstitialController.HAND_RIGHT);
        } else if (deviceType == DeviceType.PicoNeo2) {
            addController(DeviceType.PicoNeo2, WebXRInterstitialController.HAND_LEFT);
            addController(DeviceType.PicoNeo2, WebXRInterstitialController.HAND_RIGHT);
        } else if (deviceType == DeviceType.PicoG2) {
            addController(DeviceType.PicoG2, WebXRInterstitialController.HAND_NONE);
        } else if (deviceType == DeviceType.ViveFocus) {
            addController(DeviceType.ViveFocus, WebXRInterstitialController.HAND_NONE);
        } else if (deviceType == DeviceType.ViveFocusPlus) {
            addController(DeviceType.ViveFocusPlus, WebXRInterstitialController.HAND_LEFT);
            addController(DeviceType.ViveFocusPlus, WebXRInterstitialController.HAND_RIGHT);
        } else if (deviceType == DeviceType.PicoNeo3) {
            addController(DeviceType.PicoNeo3, WebXRInterstitialController.HAND_LEFT);
            addController(DeviceType.PicoNeo3, WebXRInterstitialController.HAND_RIGHT);
        } else if (deviceType == DeviceType.Pico4x) {
            addController(DeviceType.Pico4x, WebXRInterstitialController.HAND_LEFT);
            addController(DeviceType.Pico4x, WebXRInterstitialController.HAND_RIGHT);
        } else if (deviceType == DeviceType.Pico4U) {
            addController(DeviceType.Pico4U, WebXRInterstitialController.HAND_LEFT);
            addController(DeviceType.Pico4U, WebXRInterstitialController.HAND_RIGHT);
        } else if (DeviceType.isHVRBuild()) {
            if (PlatformActivity.isPositionTrackingSupported()) {
                addController(DeviceType.HVR6DoF, WebXRInterstitialController.HAND_LEFT);
                addController(DeviceType.HVR6DoF, WebXRInterstitialController.HAND_RIGHT);
            } else {
                addController(DeviceType.HVR3DoF, WebXRInterstitialController.HAND_NONE);
            }
        } else if (deviceType == DeviceType.LenovoVRX) {
            addController(DeviceType.LenovoVRX, WebXRInterstitialController.HAND_LEFT);
            addController(DeviceType.LenovoVRX, WebXRInterstitialController.HAND_RIGHT);
        } else if (deviceType == DeviceType.VisionGlass) {
            addController(DeviceType.VisionGlass, WebXRInterstitialController.HAND_NONE);
        } else if (deviceType == DeviceType.PfdmMR) {
            addController(DeviceType.PfdmMR, WebXRInterstitialController.HAND_LEFT);
            addController(DeviceType.PfdmMR, WebXRInterstitialController.HAND_RIGHT);
        } else if (deviceType == DeviceType.PfdmYVR1) {
            addController(DeviceType.PfdmYVR1, WebXRInterstitialController.HAND_LEFT);
            addController(DeviceType.PfdmYVR1, WebXRInterstitialController.HAND_RIGHT);
        } else if (deviceType == DeviceType.PfdmYVR2) {
            addController(DeviceType.PfdmYVR2, WebXRInterstitialController.HAND_LEFT);
            addController(DeviceType.PfdmYVR2, WebXRInterstitialController.HAND_RIGHT);
        }
        for (UIWidget controller: mControllers) {
            controller.getPlacement().parentHandle = getHandle();
            mWidgetManager.addWidget(controller);
        }
    }

    private void showControllers() {
        if (mControllers.size() == 0) {
            initializeControllers();
        }

        for (UIWidget widget: mControllers) {
            widget.show(KEEP_FOCUS);
        }
    }

    private void hideControllers() {
        for (UIWidget widget: mControllers) {
            widget.hide(KEEP_WIDGET);
        }
    }

    private void startAnimation() {
        mSpinnerAnimation.start();
    }

    private void stopAnimation() {
        mSpinnerAnimation.stop();
    }

    @Override
    public void onEnterWebXR() {
        startAnimation();
        if (firstEnterXR) {
            firstEnterXR = false;
            showControllers();
            // Add some delay to duplicated input detection conflicts with the EnterVR button.
            postDelayed(() -> {
                if (mWidgetManager != null && !mWidgetManager.isWebXRIntersitialHidden()) {
                    mWidgetManager.setWebXRIntersitialState(WidgetManagerDelegate.WEBXR_INTERSTITIAL_ALLOW_DISMISS);
                }
            }, 50);
        }
        show(KEEP_FOCUS);
    }

    @Override
    public void onExitWebXR() {
        stopAnimation();
        hideControllers();
        setHowToVisible(false);
        hide(KEEP_WIDGET);
    }

    @Override
    public void onDismissWebXRInterstitial() {
        mInterstitialDismissed = true;
        setHowToVisible(false);
        hideControllers();
        if (!mWebXRRendering) {
            stopAnimation();
        }
        mWidgetManager.updateWidget(this);
    }

    @Override
    public void onWebXRRenderStateChange(boolean aRendering) {
        mWebXRRendering = aRendering;
        if (aRendering && mInterstitialDismissed) {
            stopAnimation();
        } else if (!aRendering) {
            startAnimation();
        }
    }
}
