package com.igalia.wolvic.ui.widgets.menus;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.adapter.ComponentsAdapter;
import com.igalia.wolvic.browser.api.WSessionSettings;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.HamburgerMenuBinding;
import com.igalia.wolvic.ui.adapters.HamburgerMenuAdapter;
import com.igalia.wolvic.ui.widgets.UIWidget;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.utils.AnimationHelper;
import com.igalia.wolvic.utils.ViewUtils;

import java.util.ArrayList;
import java.util.List;

import mozilla.components.browser.state.state.BrowserState;
import mozilla.components.browser.state.state.SessionState;
import mozilla.components.browser.state.state.WebExtensionState;
import mozilla.components.concept.engine.webextension.Action;

public class HamburgerMenuWidget extends UIWidget implements
        WidgetManagerDelegate.FocusChangeListener,
        ComponentsAdapter.StoreUpdatesListener {

    private boolean mProxify = SettingsStore.getInstance(getContext()).getLayersEnabled();

    public interface MenuDelegate {
        void onSendTab();
        void onResize();
        void onFindInPage();
        void onSwitchMode();
        void onAddons();
        void onSaveWebApp();
        void onPassthrough();
        boolean isPassthroughEnabled();
    }

    public static final int SWITCH_ITEM_ID = 0;

    private HamburgerMenuAdapter mAdapter;
    boolean mSendTabEnabled = false;
    private ArrayList<HamburgerMenuAdapter.MenuItem> mItems;
    private MenuDelegate mDelegate;
    private int mCurrentUAMode;

    public HamburgerMenuWidget(@NonNull Context aContext) {
        super(aContext);

        mItems = new ArrayList<>();
        mCurrentUAMode = SettingsStore.getInstance(aContext).getUaMode();

        updateUI();
    }

    @SuppressLint("ClickableViewAccessibility")
    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        HamburgerMenuBinding binding = DataBindingUtil.inflate(inflater, R.layout.hamburger_menu, this, true);
        binding.setLifecycleOwner((VRBrowserActivity) getContext());
        mAdapter = new HamburgerMenuAdapter(getContext());
        binding.list.setAdapter(mAdapter);
        binding.list.setVerticalScrollBarEnabled(false);
        binding.list.setOnTouchListener((v, event) -> {
            v.requestFocusFromTouch();
            return false;
        });
        binding.list.addOnScrollListener(mScrollListener);
        binding.list.setHasFixedSize(true);
        binding.list.setItemViewCacheSize(20);
        // Drawing Cache is deprecated in API level 28: https://developer.android.com/reference/android/view/View#getDrawingCache().
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            binding.list.setDrawingCacheEnabled(true);
            binding.list.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
        }

        updateItems();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        updateUI();
    }

    @Override
    public void show(int aShowFlags) {
        mWidgetPlacement.proxifyLayer = mProxify;
        super.show(aShowFlags);

        if (mWidgetManager != null) {
            mWidgetManager.addFocusChangeListener(this);
        }

        ComponentsAdapter.get().addStoreUpdatesListener(this);

        AnimationHelper.scaleIn(findViewById(R.id.menuContainer), 100, 0, null);
    }

    @Override
    public void hide(int aHideFlags) {
        hide(aHideFlags, true);
    }

    public void hide(int aHideFlags, boolean anim) {
        if (anim) {
            AnimationHelper.scaleOut(findViewById(R.id.menuContainer), 100, 0, () -> HamburgerMenuWidget.super.hide(aHideFlags));

        } else {
            HamburgerMenuWidget.super.hide(aHideFlags);
        }

        mWidgetPlacement.proxifyLayer = false;

        if (mWidgetManager != null) {
            mWidgetManager.removeFocusChangeListener(this);
        }

        ComponentsAdapter.get().removeStoreUpdatesListener(this);
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.hamburger_menu_width);
        aPlacement.parentAnchorX = 1.0f;
        aPlacement.parentAnchorY = 1.0f;
        aPlacement.anchorX = 1.0f;
        aPlacement.anchorY = 0.0f;
        aPlacement.translationX = 20;
        aPlacement.translationY = 10;
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.context_menu_z_distance);
    }

    public void setUAMode(int uaMode) {
        mCurrentUAMode = uaMode;
        HamburgerMenuAdapter.MenuItem item = getSwitchModeIndex();
        if (item != null) {
            switch (uaMode) {
                case WSessionSettings.USER_AGENT_MODE_DESKTOP: {
                    item.setIcon(R.drawable.ic_icon_ua_desktop);
                }
                break;

                case WSessionSettings.USER_AGENT_MODE_MOBILE:
                case WSessionSettings.USER_AGENT_MODE_VR: {
                    item.setIcon(R.drawable.ic_icon_ua_default);
                }
                break;

            }

            mAdapter.notifyItemChanged(mItems.indexOf(item));
        }
    }

    public void setMenuDelegate(@Nullable MenuDelegate delegate) {
        mDelegate = delegate;
    }

    private void updateItems() {
        mItems = new ArrayList<>();

        // In kiosk mode, only resize, find in page and passthrough are available.
        if (!mWidgetManager.getFocusedWindow().isKioskMode()) {
            mItems.add(new HamburgerMenuAdapter.MenuItem.Builder(
                    HamburgerMenuAdapter.MenuItem.TYPE_ADDONS_SETTINGS,
                    (menuItem) -> {
                        if (mDelegate != null) {
                            mDelegate.onAddons();
                        }
                        return null;
                    }).build());

            final Session activeSession = SessionStore.get().getActiveSession();
            String url = activeSession.getCurrentUri();
            boolean showAddons = (URLUtil.isHttpsUrl(url) || URLUtil.isHttpUrl(url)) && !mWidgetManager.getFocusedWindow().isAddonsVisible();
            final SessionState tab = ComponentsAdapter.get().getSessionStateForSession(activeSession);
            if (tab != null && showAddons) {
                final List<WebExtensionState> extensions = ComponentsAdapter.get().getSortedEnabledExtensions();
                extensions.forEach((extension) -> {
                    if (!extension.getAllowedInPrivateBrowsing() && activeSession.isPrivateMode()) {
                        return;
                    }

                    final WebExtensionState tabExtensionState = tab.getExtensionState().get(extension.getId());
                    if (extension.getBrowserAction() != null) {
                        addOrUpdateAddonMenuItem(
                                extension,
                                extension.getBrowserAction(),
                                tabExtensionState != null ? tabExtensionState.getBrowserAction() : null);
                    }
                    if (extension.getPageAction() != null) {
                        addOrUpdateAddonMenuItem(
                                extension,
                                extension.getPageAction(),
                                tabExtensionState != null ? tabExtensionState.getPageAction() : null);
                    }
                });
            }

            if (activeSession.getWebAppManifest() != null) {
                mItems.add(new HamburgerMenuAdapter.MenuItem.Builder(
                        HamburgerMenuAdapter.MenuItem.TYPE_DEFAULT,
                        (menuItem) -> {
                            if (mDelegate != null) {
                                mDelegate.onSaveWebApp();
                            }
                            return null;
                        })
                        .withTitle(getContext().getString(R.string.hamburger_menu_save_web_app))
                        .withIcon(R.drawable.ic_web_app_registration)
                        .build());
            }

            if (mSendTabEnabled) {
                mItems.add(new HamburgerMenuAdapter.MenuItem.Builder(
                        HamburgerMenuAdapter.MenuItem.TYPE_DEFAULT,
                        (menuItem) -> {
                            if (mDelegate != null) {
                                mDelegate.onSendTab();
                            }
                            return null;
                        })
                        .withTitle(getContext().getString(R.string.hamburger_menu_send_tab))
                        .withIcon(R.drawable.ic_icon_tabs_sendtodevice)
                        .build());
            }

            HamburgerMenuAdapter.MenuItem item = new HamburgerMenuAdapter.MenuItem.Builder(
                    HamburgerMenuAdapter.MenuItem.TYPE_DEFAULT,
                    (menuItem) -> {
                        if (mDelegate != null) {
                            mDelegate.onSwitchMode();
                        }
                        return null;
                    })
                    .withId(SWITCH_ITEM_ID)
                    .withTitle(getContext().getString(R.string.hamburger_menu_switch_to_desktop))
                    .build();
            switch (mCurrentUAMode) {
                case WSessionSettings.USER_AGENT_MODE_DESKTOP: {
                    item.setIcon(R.drawable.ic_icon_ua_desktop);
                }
                break;

                case WSessionSettings.USER_AGENT_MODE_MOBILE:
                case WSessionSettings.USER_AGENT_MODE_VR: {
                    item.setIcon(R.drawable.ic_icon_ua_default);
                }
                break;
            }
            mItems.add(item);
        }

        mItems.add(new HamburgerMenuAdapter.MenuItem.Builder(
                HamburgerMenuAdapter.MenuItem.TYPE_DEFAULT,
                (menuItem) -> {
                    if (mDelegate != null) {
                        mDelegate.onFindInPage();
                    }
                    return null;
                })
                .withTitle(getContext().getString(R.string.hamburger_menu_find_in_page))
                .withIcon(R.drawable.ic_icon_search)
                .build());

        mItems.add(new HamburgerMenuAdapter.MenuItem.Builder(
                HamburgerMenuAdapter.MenuItem.TYPE_DEFAULT,
                (menuItem) -> {
                    if (mDelegate != null) {
                        mDelegate.onResize();
                    }
                    return null;
                })
                .withTitle(getContext().getString(R.string.hamburger_menu_resize))
                .withIcon(R.drawable.ic_icon_resize)
                .build());

        if (mWidgetManager != null && mWidgetManager.isPassthroughSupported()) {
            mItems.add(new HamburgerMenuAdapter.MenuItem.Builder(
                    HamburgerMenuAdapter.MenuItem.TYPE_DEFAULT,
                    (menuItem) -> {
                        if (mDelegate != null) {
                            mDelegate.onPassthrough();
                        }
                        return null;
                    })
                    .withTitle(getContext().getString(R.string.hamburger_menu_toggle_passthrough))
                    .withIcon(mDelegate != null && mDelegate.isPassthroughEnabled() ? R.drawable.baseline_visibility_24 : R.drawable.baseline_visibility_off_24)
                    .build());
        }

        mAdapter.setItems(mItems);
        mAdapter.notifyDataSetChanged();

        mWidgetPlacement.height = mItems.size() * WidgetPlacement.dpDimension(getContext(), R.dimen.hamburger_menu_item_height);
        mWidgetPlacement.height += mBorderWidth * 2;
        mWidgetPlacement.height += WidgetPlacement.dpDimension(getContext(), R.dimen.hamburger_menu_triangle_height);

        updateWidget();
    }

    private void addOrUpdateAddonMenuItem(final WebExtensionState extension,
                                          final @NonNull Action globalAction,
                                          final @Nullable Action tabAction
    ) {
        HamburgerMenuAdapter.MenuItem menuItem = mItems.stream().filter(item -> item.getAddonId().equals(extension.getId())).findFirst().orElse(null);
        if (menuItem == null) {
            menuItem = new HamburgerMenuAdapter.MenuItem.Builder(
                    HamburgerMenuAdapter.MenuItem.TYPE_ADDON,
                    (item) -> {
                        globalAction.getOnClick().invoke();
                        onDismiss();
                        return null;
                    })
                    .withAddonId(extension.getId())
                    .withTitle(extension.getName())
                    .withIcon(R.drawable.ic_icon_addons)
                    .withAction(globalAction)
            .build();
            mItems.add(menuItem);
        }
        if (tabAction != null) {
            menuItem.setAction(globalAction.copyWithOverride(tabAction));
        }
    }

    private HamburgerMenuAdapter.MenuItem getSwitchModeIndex() {
        return mItems.stream().filter(item -> item.getId() == SWITCH_ITEM_ID).findFirst().orElse(null);
    }

    public void setSendTabEnabled(boolean value) {
        mSendTabEnabled = value;
        updateItems();
    }

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (!ViewUtils.isEqualOrChildrenOf(this, newFocus) && isVisible()) {
            onDismiss();
        }
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

    @Override
    public void onTabSelected(@NonNull BrowserState state, @Nullable mozilla.components.browser.state.state.SessionState tab) {
        updateItems();
    }

}
