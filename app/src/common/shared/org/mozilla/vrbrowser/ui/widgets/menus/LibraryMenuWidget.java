package org.mozilla.vrbrowser.ui.widgets.menus;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.ui.callbacks.LibraryItemContextMenuClickCallback;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.AnimationHelper;
import org.mozilla.vrbrowser.utils.ViewUtils;

import java.util.ArrayList;
import java.util.Optional;

public class LibraryMenuWidget extends MenuWidget implements WidgetManagerDelegate.FocusChangeListener {

    public enum LibraryItemType {
        BOOKMARKS,
        HISTORY
    }

    public static class LibraryContextMenuItem {

        private String url;
        private String title;
        private LibraryItemType type;

        public LibraryContextMenuItem(@NonNull String url, String title, LibraryItemType type) {
            this.url = url;
            this.title = title;
            this.type = type;
        }

        public String getUrl() {
            return url;
        }

        public String getTitle() {
            return title;
        }

        public LibraryItemType getType() {
            return type;
        }

    }

    ArrayList<MenuItem> mItems;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    Optional<LibraryItemContextMenuClickCallback> mItemDelegate;
    LibraryContextMenuItem mItem;

    public LibraryMenuWidget(Context aContext, LibraryContextMenuItem item, boolean canOpenWindows, boolean isBookmarked) {
        super(aContext, R.layout.library_menu);
        initialize();

        mItem = item;
        createMenuItems(canOpenWindows, isBookmarked);
    }

    private void initialize() {
        mAdapter.updateBackgrounds(
                R.drawable.library_context_menu_item_background_top,
                R.drawable.library_context_menu_item_background_bottom,
                R.drawable.library_context_menu_item_background_middle,
                R.drawable.library_context_menu_item_background_single);
        mAdapter.updateLayoutId(R.layout.library_menu_item);
    }

    @Override
    public void show(int aShowFlags) {
        super.show(aShowFlags);

        AnimationHelper.scaleIn(findViewById(R.id.menuContainer), 100, 0, null);
        mWidgetManager.addFocusChangeListener(this);
    }

    @Override
    public void hide(int aHideFlags) {
        AnimationHelper.scaleOut(findViewById(R.id.menuContainer), 100, 0, () -> LibraryMenuWidget.super.hide(aHideFlags));
        mWidgetManager.removeFocusChangeListener(this);
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

    public void setItemDelegate(LibraryItemContextMenuClickCallback delegate) {
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

        if (mItem.type == LibraryItemType.HISTORY) {
            mItems.add(new MenuItem(getContext().getString(
                    isBookmarked ? R.string.history_context_remove_bookmarks : R.string.history_context_add_bookmarks),
                    isBookmarked ? R.drawable.ic_icon_bookmarked_active : R.drawable.ic_icon_bookmarked,
                    () -> mItemDelegate.ifPresent((present -> {
                        if (isBookmarked) {
                            mItemDelegate.get().onRemoveFromBookmarks(mItem);

                        } else {
                            mItemDelegate.get().onAddToBookmarks(mItem);
                        }
                    }))));
        }

        super.updateMenuItems(mItems);

        mWidgetPlacement.height = mItems.size() * WidgetPlacement.dpDimension(getContext(), R.dimen.library_menu_item_height);
        mWidgetPlacement.height += mBorderWidth * 2;
    }

    // FocusChangeListener

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (!ViewUtils.isEqualOrChildrenOf(this, newFocus)) {
            onDismiss();
        }
    }
}
