package org.mozilla.vrbrowser.ui.widgets.menus.library;

import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.ui.callbacks.LibraryContextMenuCallback;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.ui.widgets.menus.MenuWidget;
import org.mozilla.vrbrowser.utils.AnimationHelper;

import java.util.ArrayList;
import java.util.Optional;

public abstract class LibraryContextMenuWidget extends MenuWidget {

    public static class LibraryContextMenuItem {

        private String url;
        private String title;

        public LibraryContextMenuItem(@NonNull String url, String title) {
            this.url = url;
            this.title = title;
        }

        public String getUrl() {
            return url;
        }

        public String getTitle() {
            return title;
        }

    }

    ArrayList<MenuItem> mItems;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    Optional<LibraryContextMenuCallback> mItemDelegate;
    LibraryContextMenuItem mItem;

    public LibraryContextMenuWidget(Context aContext, LibraryContextMenuItem item, boolean canOpenWindows, boolean isBookmarked) {
        super(aContext, R.layout.library_menu);
        initialize();

        mItem = item;
        createMenuItems(canOpenWindows, isBookmarked);
    }

    private void initialize() {
        updateUI();
    }

    @Override
    public void updateUI() {
        super.updateUI();

        mAdapter.updateBackgrounds(
                R.drawable.library_context_menu_item_background_top,
                R.drawable.library_context_menu_item_background_bottom,
                R.drawable.library_context_menu_item_background_middle,
                R.drawable.library_context_menu_item_background_single);
        mAdapter.updateLayoutId(R.layout.library_menu_item);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        updateUI();
    }

    @Override
    public void show(int aShowFlags) {
        super.show(aShowFlags);

        AnimationHelper.scaleIn(findViewById(R.id.menuContainer), 100, 0, null);
    }

    @Override
    public void hide(int aHideFlags) {
        AnimationHelper.scaleOut(findViewById(R.id.menuContainer), 100, 0, () -> LibraryContextMenuWidget.super.hide(aHideFlags));
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.library_menu_width);
        aPlacement.parentAnchorX = 0.0f;
        aPlacement.parentAnchorY = 1.0f;
        aPlacement.anchorX = 1.0f;
        aPlacement.anchorY = 1.0f;
        aPlacement.opaque = false;
        aPlacement.cylinder = true;
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.context_menu_z_distance);
    }

    public void setItemDelegate(LibraryContextMenuCallback delegate) {
        mItemDelegate = Optional.ofNullable(delegate);;
    }

    private void createMenuItems(boolean canOpenWindows, boolean isBookmarked) {
        mItems = new ArrayList<>();

        if (canOpenWindows) {
            mItems.add(new MenuItem(getContext().getString(
                    R.string.history_context_menu_new_window),
                    R.drawable.ic_icon_library_new_window,
                    () -> mItemDelegate.ifPresent((present -> mItemDelegate.get().onOpenInNewWindowClick(mItem)))));
        }

        mItems.add(new MenuItem(getContext().getString(
                R.string.history_context_menu_new_tab),
                R.drawable.ic_icon_newtab,
                () -> mItemDelegate.ifPresent((present -> mItemDelegate.get().onOpenInNewTabClick(mItem)))));

        setupCustomMenuItems(canOpenWindows, isBookmarked);

        super.updateMenuItems(mItems);

        mWidgetPlacement.height = mItems.size() * WidgetPlacement.dpDimension(getContext(), R.dimen.library_menu_item_height);
        mWidgetPlacement.height += mBorderWidth * 2;
    }

    protected void setupCustomMenuItems(boolean canOpenWindows, boolean isBookmarked) {}

}
