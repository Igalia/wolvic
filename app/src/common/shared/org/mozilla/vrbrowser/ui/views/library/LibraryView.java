package org.mozilla.vrbrowser.ui.views.library;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserActivity;
import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.ui.callbacks.LibraryContextMenuCallback;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.ui.widgets.WindowWidget;
import org.mozilla.vrbrowser.ui.widgets.menus.library.LibraryContextMenuWidget;

import java.util.concurrent.Executor;

public abstract class LibraryView extends FrameLayout {

    protected WidgetManagerDelegate mWidgetManager;
    protected LibraryContextMenuWidget mContextMenu;
    protected Executor mUIThreadExecutor;

    public LibraryView(@NonNull Context context) {
        super(context);
    }

    public LibraryView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public LibraryView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LibraryView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    protected void initialize() {
        mWidgetManager = ((VRBrowserActivity) getContext());
        mUIThreadExecutor = ((VRBrowserApplication)getContext().getApplicationContext()).getExecutors().mainThread();
    }

    public void updateUI() {};

    public void onDestroy() {};

    public void onShow() {}

    public void onHide() {}

    protected abstract void updateLayout();

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
        getDrawingRect(offsetViewBounds);
        offsetDescendantRectToMyCoords(view, offsetViewBounds);

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
