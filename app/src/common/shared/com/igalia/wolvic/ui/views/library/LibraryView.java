package com.igalia.wolvic.ui.views.library;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.VRBrowserApplication;
import com.igalia.wolvic.ui.callbacks.LibraryContextMenuCallback;
import com.igalia.wolvic.ui.widgets.UIWidget;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.ui.widgets.WindowWidget;
import com.igalia.wolvic.ui.widgets.menus.library.LibraryContextMenuWidget;
import com.igalia.wolvic.utils.ViewUtils;

import java.util.concurrent.Executor;

public abstract class LibraryView extends FrameLayout {
    protected String mSearchFilter = "";

    protected WidgetManagerDelegate mWidgetManager;
    protected LibraryContextMenuWidget mContextMenu;
    protected Executor mUIThreadExecutor;
    protected LibraryPanel mRootPanel;

    public LibraryView(@NonNull Context context) {
        super(context);
    }

    public LibraryView(@NonNull Context context, @NonNull LibraryPanel delegate) {
        super(context);

        mRootPanel = delegate;
    }

    protected void initialize() {
        mWidgetManager = ((VRBrowserActivity) getContext());
        mUIThreadExecutor = ((VRBrowserApplication)getContext().getApplicationContext()).getExecutors().mainThread();
    }

    public void updateUI() {};

    public void updateSearchFilter(String s) {
        mSearchFilter = s;
    };

    public void onDestroy() {};

    public void onShow() {}

    public void onHide() {}

    public boolean onBack() { return false; }

    public boolean canGoBack() { return false; }

    protected void updateLayout() {}

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        updateLayout();
    }

    protected RecyclerView.OnScrollListener mScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);

            if (recyclerView.getScrollState() != RecyclerView.SCROLL_STATE_SETTLING) {
                recyclerView.requestFocus();
            }
        }
    };

    protected void showContextMenu(@NonNull View view,
                                 @NonNull LibraryContextMenuWidget menu,
                                 @NonNull LibraryContextMenuCallback delegate,
                                 boolean isLastVisibleItem) {
        view.requestFocusFromTouch();

        hideContextMenu();

        WindowWidget window = mWidgetManager.getFocusedWindow();

        float ratio = WidgetPlacement.viewToWidgetRatio(getContext(), window);

        Rect offsetViewBounds = new Rect();
        UIWidget parent = ViewUtils.getParentWidget(view);
        assert parent != null;
        parent.getDrawingRect(offsetViewBounds);
        parent.offsetDescendantRectToMyCoords(view, offsetViewBounds);

        mContextMenu = menu;
        mContextMenu.getPlacement().parentHandle = window.getHandle();

        PointF position;
        if (isLastVisibleItem) {
            mContextMenu.getPlacement().anchorY = 0.0f;
            position = new PointF(
                    (offsetViewBounds.left + view.getWidth()) * ratio,
                    -(offsetViewBounds.top) * ratio);

        } else {
            mContextMenu.getPlacement().anchorY = 1.0f;
            position = new PointF(
                    (offsetViewBounds.left + view.getWidth()) * ratio,
                    -(offsetViewBounds.top + view.getHeight()) * ratio);
        }
        mContextMenu.getPlacement().translationX = position.x - (mContextMenu.getWidth()/mContextMenu.getPlacement().density);
        mContextMenu.getPlacement().translationY = position.y + getResources().getDimension(R.dimen.library_menu_top_margin)/mContextMenu.getPlacement().density;

        mContextMenu.setItemDelegate(delegate);
        mContextMenu.show(UIWidget.REQUEST_FOCUS);
    }

    protected void hideContextMenu() {
        if (mContextMenu != null && !mContextMenu.isReleased()
                && mContextMenu.isVisible()) {
            mContextMenu.hide(UIWidget.REMOVE_WIDGET);
        }
    }
}
