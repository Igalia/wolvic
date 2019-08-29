/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.content.Context;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.ui.callbacks.LibraryItemContextMenuClickCallback;
import org.mozilla.vrbrowser.ui.views.LibraryItemContextMenu;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.ViewUtils;

public class LibraryItemContextMenuWidget extends UIWidget implements WidgetManagerDelegate.FocusChangeListener {

    private LibraryItemContextMenu mContextMenu;
    private int mMaxHeight;
    private PointF mTranslation;
    private int height;

    public LibraryItemContextMenuWidget(Context aContext) {
        super(aContext);

        mContextMenu = new LibraryItemContextMenu(aContext);
        initialize();
    }

    public LibraryItemContextMenuWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);

        mContextMenu = new LibraryItemContextMenu(aContext, aAttrs);
        initialize();
    }

    public LibraryItemContextMenuWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);

        mContextMenu = new LibraryItemContextMenu(aContext, aAttrs, aDefStyle);
        initialize();
    }

    private void initialize() {
        addView(mContextMenu);
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.pixelDimension(getContext(), R.dimen.browser_width_pixels)/2;
        mMaxHeight = WidgetPlacement.dpDimension(getContext(), R.dimen.prompt_height);
        aPlacement.height = mMaxHeight;
        aPlacement.parentAnchorX = 0.0f;
        aPlacement.parentAnchorY = 1.0f;
        aPlacement.anchorX = 0.0f;
        aPlacement.anchorY = 1.0f;
        aPlacement.opaque = false;
        aPlacement.cylinder = true;
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.library_context_menu_z_distance);
    }

    @Override
    public void show(@ShowFlags int aShowFlags) {
        mWidgetManager.addFocusChangeListener(LibraryItemContextMenuWidget.this);

        measure(View.MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        mWidgetPlacement.width = (int)(getMeasuredWidth()/mWidgetPlacement.density);
        mWidgetPlacement.height = (int)(getMeasuredHeight()/mWidgetPlacement.density);
        super.show(aShowFlags);

        ViewTreeObserver viewTreeObserver = mContextMenu.getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
            viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    getViewTreeObserver().removeOnGlobalLayoutListener(this);

                    mWidgetPlacement.translationX = mTranslation.x - (getWidth()/mWidgetPlacement.density);
                    mWidgetPlacement.translationY = mTranslation.y + getResources().getDimension(R.dimen.library_context_menu_top_margin)/mWidgetPlacement.density;

                    mWidgetPlacement.width = (int)((getWidth() + 5)/mWidgetPlacement.density);
                    mWidgetPlacement.height = mContextMenu.getMenuHeight() + 5;
                    mWidgetManager.updateWidget(LibraryItemContextMenuWidget.this);
                }
            });
        }
    }

    @Override
    public void hide(@HideFlags int aHideFlags) {
        super.hide(aHideFlags);

        mWidgetManager.removeFocusChangeListener(this);
    }

    @Override
    protected void onDismiss() {
        hide(REMOVE_WIDGET);
    }

    public void setHistoryContextMenuItemCallback(LibraryItemContextMenuClickCallback callback) {
        mContextMenu.setContextMenuClickCallback(callback);
    }

    public void setItem(@NonNull LibraryItemContextMenu.LibraryContextMenuItem item) {
        mContextMenu.setItem(item);
    }

    public void setPosition(@NonNull PointF position) {
        mTranslation = position;
    }

    // WidgetManagerDelegate.FocusChangeListener

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (!ViewUtils.isChildrenOf(mContextMenu, newFocus)) {
            onDismiss();
        }
    }

}
