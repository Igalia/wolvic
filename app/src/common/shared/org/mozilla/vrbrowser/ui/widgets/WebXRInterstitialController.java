package org.mozilla.vrbrowser.ui.widgets;

import android.content.Context;
import android.view.LayoutInflater;

import androidx.annotation.IntDef;
import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserActivity;
import org.mozilla.vrbrowser.databinding.WebxrInterstitialControllerBinding;
import org.mozilla.vrbrowser.utils.DeviceType;

public class WebXRInterstitialController extends UIWidget {
    private WebxrInterstitialControllerBinding mBinding;

    @IntDef(value = { HAND_NONE, HAND_LEFT, HAND_RIGHT })
    public @interface Hand {}
    public static final int HAND_NONE = -1;
    public static final int HAND_LEFT = 0;
    public static final int HAND_RIGHT = 1;

    public WebXRInterstitialController(Context aContext, int aModel, @Hand int aHand ) {
        super(aContext);
        initialize(aModel, aHand);
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.scene = WidgetPlacement.SCENE_WEBXR_INTERSTITIAL;
        aPlacement.visible = false;
        aPlacement.cylinder = false;
    }

    private void updatePlacement() {
        mWidgetPlacement.setSizeFromMeasure(getContext(), this);
        mWidgetPlacement.worldWidth = mWidgetPlacement.width * WidgetPlacement.worldToDpRatio(getContext()) * 1.15f;
        if (mBinding.getHand() == HAND_LEFT) {
            mWidgetPlacement.anchorX = 1.0f;
            mWidgetPlacement.anchorY = 0.5f;
            mWidgetPlacement.parentAnchorX = 0.0f;
            mWidgetPlacement.parentAnchorY = 0.5f;
            mWidgetPlacement.translationX = -WidgetPlacement.dpDimension(getContext(), R.dimen.webxr_interstitial_controller_margin_h);
        } else if (mBinding.getHand() == HAND_RIGHT) {
            mWidgetPlacement.anchorX = 0.0f;
            mWidgetPlacement.anchorY = 0.5f;
            mWidgetPlacement.parentAnchorX = 1.0f;
            mWidgetPlacement.parentAnchorY = 0.5f;
            mWidgetPlacement.translationX = WidgetPlacement.dpDimension(getContext(), R.dimen.webxr_interstitial_controller_margin_h);
        } else {
            mWidgetPlacement.anchorX = 0.5f;
            mWidgetPlacement.anchorY = 1.0f;
            mWidgetPlacement.parentAnchorX = 0.5f;
            mWidgetPlacement.parentAnchorY = 0.0f;
            mWidgetPlacement.translationY = 0;//-WidgetPlacement.dpDimension(getContext(), R.dimen.webxr_interstitial_controller_margin_v);
        }
    }

    private void initialize(int aModel, @Hand int aHand) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        mBinding = DataBindingUtil.inflate(inflater, R.layout.webxr_interstitial_controller, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity)getContext());
        mBinding.setModel(aModel);
        mBinding.setHand(aHand);
        mBinding.executePendingBindings();
        updatePlacement();
    }


}
