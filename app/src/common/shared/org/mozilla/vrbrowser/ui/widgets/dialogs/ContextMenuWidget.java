/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.SessionStore;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.ui.callbacks.ContextMenuClickCallback;
import org.mozilla.vrbrowser.ui.views.ContextMenu;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;

public class ContextMenuWidget extends UIWidget implements WidgetManagerDelegate.FocusChangeListener {

    private GeckoSession.ContentDelegate.ContextElement mContextElement;
    private ContextMenu mContextMenu;
    private int mMaxHeight;
    private Point mMousePos;

    public ContextMenuWidget(Context aContext) {
        super(aContext);

        mContextMenu = new ContextMenu(aContext);
        initialize();
    }

    public ContextMenuWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);

        mContextMenu = new ContextMenu(aContext, aAttrs);
        initialize();
    }

    public ContextMenuWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);

        mContextMenu = new ContextMenu(aContext, aAttrs, aDefStyle);
        initialize();
    }

    private void initialize() {
        addView(mContextMenu);
        mContextMenu.setContextMenuClickCallback(mContextMenuClickCallback);
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.pixelDimension(getContext(), R.dimen.browser_width_pixels)/2;
        mMaxHeight = WidgetPlacement.dpDimension(getContext(), R.dimen.prompt_height);
        aPlacement.height = mMaxHeight;
        aPlacement.parentAnchorX = 0.0f;
        aPlacement.parentAnchorY = 1.0f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.opaque = false;
        aPlacement.cylinder = true;
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.context_menu_z_distance);
    }

    @Override
    public void show() {
        mWidgetManager.addFocusChangeListener(ContextMenuWidget.this);

        mContextMenu.measure(View.MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        mWidgetPlacement.width = (int)(mContextMenu.getMeasuredWidth()/mWidgetPlacement.density);
        mWidgetPlacement.height = (int)(mContextMenu.getMeasuredHeight()/mWidgetPlacement.density);
        super.show();

        ViewTreeObserver viewTreeObserver = mContextMenu.getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
            viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mContextMenu.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    PointF anchor = anchorForCurrentMousePosition();
                    mWidgetPlacement.anchorX = anchor.x;
                    mWidgetPlacement.anchorY = anchor.y;
                    mWidgetPlacement.translationX = mMousePos.x * WidgetPlacement.worldToWindowRatio();
                    mWidgetPlacement.translationY = -(mMousePos.y * WidgetPlacement.worldToWindowRatio());
                    mWidgetPlacement.width = (int)(mContextMenu.getWidth()/mWidgetPlacement.density);
                    mWidgetPlacement.height = (int)(mContextMenu.getHeight()/mWidgetPlacement.density);
                    mWidgetManager.updateWidget(ContextMenuWidget.this);
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

    private final ContextMenuClickCallback mContextMenuClickCallback = contextMenuNode -> {
        SessionStore.get().loadUri(mContextElement.linkUri);
        hide(REMOVE_WIDGET);
    };

    public void setContextElement(Point mousePos, GeckoSession.ContentDelegate.ContextElement element) {
        mMousePos = mousePos;
        mContextElement = element;

        switch (mContextElement.type) {
            case GeckoSession.ContentDelegate.ContextElement.TYPE_AUDIO:
                mContextMenu.createAudioContextMenu();
                break;

            case GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE:
                mContextMenu.createImageContextMenu();
                break;

            case GeckoSession.ContentDelegate.ContextElement.TYPE_NONE:
                mContextMenu.createLinkContextMenu();
                break;

            case GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO:
                mContextMenu.createVideoContextMenu();
                break;
        }
    }

    private PointF anchorForCurrentMousePosition() {
        float browserWindowWidth = SettingsStore.getInstance(getContext()).getWindowWidth();
        float browserWindowHeight = SettingsStore.getInstance(getContext()).getWindowHeight();
        float halfWidth = WidgetPlacement.convertPixelsToDp(getContext(), getWidth());
        float halfHeight = WidgetPlacement.convertPixelsToDp(getContext(), getHeight());
        if (mMousePos.x > (browserWindowWidth - halfWidth)) {
            if (mMousePos.y < halfHeight)
                // Top Right
                return new PointF(1.0f, 1.0f);
            else
                // Middle/Bottom Right
                return new PointF(1.0f, 0.0f);

        } else if (mMousePos.x < (browserWindowWidth + halfWidth)) {
            if (mMousePos.y < halfHeight)
                // Top Left
                return new PointF(0.0f, 1.0f);
            else
                // Middle/Bottom Left
                new PointF(0.0f, 0.0f);

        } else {
            if (mMousePos.y < halfHeight)
                // Top Middle
                return new PointF(1.0f, 1.0f);
            else if (mMousePos.y > (browserWindowHeight - halfHeight))
                // Bottom Middle
                return new PointF(0.0f, 0.0f);
        }

        return new PointF(0.0f, 0.0f);
    }

    // WidgetManagerDelegate.FocusChangeListener

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (oldFocus == this && isVisible()) {
            onDismiss();
        }
    }

}
