package com.igalia.wolvic.ui.widgets.menus;

import android.content.Context;
import android.content.res.Configuration;
import android.view.View;

import com.igalia.wolvic.R;
import com.igalia.wolvic.ui.widgets.UIWidget;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.utils.ViewUtils;

import java.util.ArrayList;

public class BrightnessMenuWidget extends MenuWidget {

    ArrayList<MenuItem> mItems;
    public BrightnessMenuWidget(Context aContext) {
        super(aContext, R.layout.menu);

        updateUI();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.brightness_menu_width);
        aPlacement.parentAnchorX = 0.75f;
        aPlacement.parentAnchorY = 1.0f;
        aPlacement.anchorX = 0.0f;
        aPlacement.anchorY = 0.0f;
        aPlacement.translationY = WidgetPlacement.dpDimension(getContext(), R.dimen.video_projection_menu_translation_y);
        aPlacement.translationZ = 2.0f;
    }

    @Override
    public void updateUI() {
        super.updateUI();

        createMenuItems();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        updateUI();
    }

    public void setParentWidget(UIWidget aParent) {
        mWidgetPlacement.parentHandle = aParent.getHandle();
    }

    private void createMenuItems() {
        mItems = new ArrayList<>();

        final Runnable action = () -> handleClick();

        mItems.add(new MenuItem(getContext().getString(R.string.brightness_mode_normal), 0, action));
        mItems.add(new MenuItem(getContext().getString(R.string.brightness_mode_dark), 0, action));
        mItems.add(new MenuItem(getContext().getString(R.string.brightness_mode_void), 0, action));

        super.updateMenuItems(mItems);
        super.setSelectedItem(1);

        mWidgetPlacement.height = mItems.size() * WidgetPlacement.dpDimension(getContext(), R.dimen.menu_item_height);
        mWidgetPlacement.height += mBorderWidth * 2;
    }

    public float getSelectedBrightness() {
        switch (super.getSelectedItem()) {
            case 0: return 0.5f;
            case 1: return 0.05f;
            case 2: return 0.0f;
        }
        return 1.0f;
    }

    private void handleClick() {
        mWidgetManager.setWorldBrightness(this, getSelectedBrightness());
        mWidgetPlacement.visible = false;
        mWidgetManager.updateWidget(this);
    }

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (!ViewUtils.isEqualOrChildrenOf(this, newFocus) && isVisible()) {
            hide(KEEP_WIDGET);
        }
    }
}
