package org.mozilla.vrbrowser.ui.widgets;

import android.content.Context;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.view.LayoutInflater;

import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserActivity;
import org.mozilla.vrbrowser.databinding.WebxrInterstitialBinding;
import org.mozilla.vrbrowser.utils.DeviceType;
import org.mozilla.vrbrowser.utils.ViewUtils;

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
    }

    private void initialize() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        mBinding = DataBindingUtil.inflate(inflater, R.layout.webxr_interstitial, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity)getContext());
        setHowToVisible(true);

        // AnimatedVectorDrawable doesn't work with a Hardware Accelerated canvas, we disable it for this view.
        setIsHardwareAccelerationEnabled(false);

        mSpinnerAnimation = (AnimatedVectorDrawable) mBinding.webxrSpinner.getDrawable();
        if (DeviceType.isPicoVR()) {
            ViewUtils.forceAnimationOnUI(mSpinnerAnimation);
        }
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
        } else if (deviceType == DeviceType.OculusQuest) {
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
