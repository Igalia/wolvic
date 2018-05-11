package org.mozilla.vrbrowser.ui;


import android.content.Context;
import android.support.design.widget.TabLayout;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.SessionStore;
import org.mozilla.vrbrowser.Widget;
import org.mozilla.vrbrowser.WidgetManagerDelegate;
import org.mozilla.vrbrowser.WidgetPlacement;

import java.util.List;

public class BrowserHeaderWidget extends UIWidget
        implements TabLayout.OnTabSelectedListener, CustomTabLayout.Delegate,
        SessionStore.SessionChangeListener, GeckoSession.ContentDelegate, GeckoSession.ProgressDelegate,
        MoreMenuWidget.Delegate, TabOverflowWidget.Delegate {
    private Context mContext;
    private CustomTabLayout mTabContainer;
    private ImageButton mAddTabButton;
    private ImageButton mTabsScrollLeftButton;
    private LinearLayout mTruncateContainer;
    private NavigationBar mNavigationBar;
    private boolean mIsTruncatingTabs = false;
    private int mHeaderButtonMargin;
    private int mTabLayoutAddMargin;
    private MoreMenuWidget mMoreMenu;
    private TabOverflowWidget mTabOverflowMenu;
    private boolean mIsPrivateBrowsing = false;
    private boolean mAnimateTabs = true;

    public BrowserHeaderWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public BrowserHeaderWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public BrowserHeaderWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        mContext = aContext;
        inflate(aContext, R.layout.browser_header, this);

        mTruncateContainer = findViewById(R.id.truncateContainer);
        mNavigationBar = findViewById(R.id.navigationBar2D);
        mHeaderButtonMargin = getResources().getDimensionPixelSize(R.dimen.header_button_margin);
        mTabLayoutAddMargin = getResources().getDimensionPixelSize(R.dimen.tab_layout_add_margin);

        mAddTabButton = findViewById(R.id.addTabButton);
        mAddTabButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                addTabClick();
            }
        });

        ImageButton mTruncateAddTabButton = findViewById(R.id.tabTruncateAddTabButton);
        mTruncateAddTabButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                addTabClick();
            }
        });


        mTabsScrollLeftButton = findViewById(R.id.tabScrollLeftButton);
        mTabsScrollLeftButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mTabContainer.scrollLeft(true);
            }
        });


        ImageButton tabsScrollRightButton = findViewById(R.id.tabScrollRightButton);
        tabsScrollRightButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mTabContainer.scrollRight(true);
            }
        });

        ImageButton tabListAllButton = findViewById(R.id.tabListAllButton);
        tabListAllButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showTabOverflow();
            }
        });

        ImageButton mMoreMenuButton = findViewById(R.id.moreMenuButton);
        mMoreMenuButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showMoreMenu();
            }
        });

        ImageButton mCloseWindowButton = findViewById(R.id.closeWindowButton);
        mCloseWindowButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        mTabContainer = findViewById(R.id.tabContainer);
        mTabContainer.addOnTabSelectedListener(this);
        mTabContainer.setDelegate(this);

        SessionStore.get().addSessionChangeListener(this);
        SessionStore.get().addContentListener(this);
        SessionStore.get().addProgressListener(this);

        fillTabs();
    }

    private void fillTabs() {
        mTabContainer.removeAllTabs();

        int currentSessionId = SessionStore.get().getCurrentSessionId();
        List<Integer> sessionIds = SessionStore.get().getSessionsByPrivateMode(mIsPrivateBrowsing);
        for (int sessionId: sessionIds) {
            createTab(sessionId, currentSessionId == sessionId, false);
        }
        for (Integer sessionId: sessionIds) {
            SessionStore.get().dumpAllState(sessionId);
        }
    }

    private void showMoreMenu() {
        if (mMoreMenu == null) {
            WidgetPlacement placement = new WidgetPlacement();
            placement.widgetType = Widget.MoreMenu;
            placement.parentHandle = getHandle();
            placement.width = 300;
            placement.height = 100;
            placement.parentAnchorX = 1.0f;
            placement.parentAnchorY = 1.0f;
            placement.anchorX = (placement.width - 46.0f)/placement.width;
            placement.anchorY = 0.0f;
            placement.translationY = 6.0f;

            mWidgetManager.addWidget(placement, true, new WidgetManagerDelegate.WidgetAddCallback() {
                @Override
                public void onWidgetAdd(Widget aWidget) {
                    mMoreMenu = (MoreMenuWidget) aWidget;
                    mMoreMenu.setDelegate(BrowserHeaderWidget.this);
                    mMoreMenu.updatePrivateBrowsing(mIsPrivateBrowsing);
                }
            });
        }
        else {
            mWidgetManager.updateWidget(mMoreMenu.getHandle(), true, null);
        }
        hideTabOverflow();
    }

    private void hideMoreMenu() {
        if (mMoreMenu != null && mMoreMenu.getVisibility() == View.VISIBLE) {
            mWidgetManager.updateWidget(mMoreMenu.getHandle(), false, null);
        }
    }

    private void addTabClick() {
        SessionStore.SessionSettings settings = new SessionStore.SessionSettings();
        settings.privateMode = mIsPrivateBrowsing;
        int sessionId = SessionStore.get().createSession(settings);
        SessionStore.get().setCurrentSession(sessionId);
        SessionStore.get().loadUri(SessionStore.DEFAULT_URL);
    }

    private TabLayout.Tab createTab(int aSessionId, boolean aSelected, boolean aAnimated) {
        TabLayout.Tab tab = mTabContainer.newTab();
        TabView tabView = new TabView(mContext);
        tabView.setSessionId(aSessionId);
        tabView.setTabCloseCallback(new TabView.TabCloseCallback() {
            @Override
            public void onTabClose(TabView aTab) {
                closeTab(aTab);
            }
        });
        tabView.setIsPrivate(mIsPrivateBrowsing);
        if (aAnimated) {
            tabView.animateAdd();
        }
        tab.setCustomView(tabView);
        mTabContainer.addTab(tab, aSelected);
        return tab;
    }

    private void showTabOverflow() {
        if (mTabOverflowMenu == null) {
            WidgetPlacement placement = new WidgetPlacement();
            placement.widgetType = Widget.TabOverflowMenu;
            placement.parentHandle = getHandle();
            placement.width = 350;
            placement.height = 275;
            placement.parentAnchorX = 1.0f;
            placement.parentAnchorY = 0.0f;
            placement.anchorX = 1.0f;
            placement.anchorY = 1.0f;
            placement.translationX = -10.0f;
            placement.translationY = -120.0f;
            placement.translationZ = 2.0f;

            mWidgetManager.addWidget(placement, true, new WidgetManagerDelegate.WidgetAddCallback() {
                @Override
                public void onWidgetAdd(Widget aWidget) {
                    mTabOverflowMenu = (TabOverflowWidget) aWidget;
                    mTabOverflowMenu.setDelegate(BrowserHeaderWidget.this);
                    mTabOverflowMenu.updatePrivateBrowsing(mIsPrivateBrowsing);
                }
            });
        } else if (mTabOverflowMenu.getVisibility() == View.VISIBLE) {
            // Hide the tab overflow menu if we click the button while it's already opened
            hideTabOverflow();

        } else {
            mTabOverflowMenu.onShow();
            mWidgetManager.updateWidget(mTabOverflowMenu.getHandle(), true, null);
        }
        hideMoreMenu();
    }

    private void hideTabOverflow() {
        if (mTabOverflowMenu != null && mTabOverflowMenu.getVisibility() == View.VISIBLE) {
            mTabOverflowMenu.onHide();
            mWidgetManager.updateWidget(mTabOverflowMenu.getHandle(), false, null);
        }
    }

    @Override
    public void releaseWidget() {
        if (mTabContainer != null) {
            mTabContainer.removeOnTabSelectedListener(this);
            mTabContainer.setDelegate(null);
        }
        SessionStore.get().removeSessionChangeListener(this);
        SessionStore.get().removeContentListener(this);
        SessionStore.get().removeProgressListener(this);
        super.releaseWidget();
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        TabView tabView = (TabView) tab.getCustomView();
        if (tabView != null) {
            tabView.setIsSelected(true);
            if (SessionStore.get().getCurrentSessionId() != tabView.getSessionId()) {
                SessionStore.get().setCurrentSession(tabView.getSessionId());
            }
        }
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {
        TabView tabView = (TabView) tab.getCustomView();
        if (tabView != null) {
            tabView.setIsSelected(false);
        }
    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {
        TabView tabView = (TabView) tab.getCustomView();
        if (tabView != null) {
            tabView.setIsSelected(true);
        }
    }

    private void closeTab(TabView aTabToRemove) {
        int sessionToRemove = aTabToRemove.getSessionId();
        TabLayout.Tab tab = findTab(sessionToRemove);
        if (tab == null) {
            return;
        }
        int tabIndex = tab.getPosition();
        int count = mTabContainer.getTabCount();
        if (count > tabIndex + 1) {
            // Select next Tab/Session if available
            if (aTabToRemove.isSelected()) {
                mTabContainer.getTabAt(tabIndex + 1).select();
            }
        }
        else if (tabIndex > 0) {
            // Select prev Tab/Session
            if (aTabToRemove.isSelected()) {
                mTabContainer.getTabAt(tabIndex - 1).select();
            }
        }
        else {
            // Delete the only tab
            // Create a new empty session and make it current
            SessionStore.get().removeSessionChangeListener(this);
            int sessionId = SessionStore.get().createSession();
            aTabToRemove.setSessionId(sessionId);
            SessionStore.get().setCurrentSession(sessionId);
            SessionStore.get().loadUri(SessionStore.DEFAULT_URL);
            SessionStore.get().addSessionChangeListener(this);
        }
        // Remove session
        SessionStore.get().removeSession(sessionToRemove);
    }


    private TabLayout.Tab findTab(int aSessionId) {
        for (int i = 0; i <  mTabContainer.getTabCount(); ++i) {
            TabLayout.Tab tab = mTabContainer.getTabAt(i);
            TabView view = (TabView) tab.getCustomView();
            if (view != null && view.getSessionId() == aSessionId) {
                return tab;
            }
        }
        return null;
    }

    private TabView findTabView(int aSessionId) {
        for (int i = 0; i <  mTabContainer.getTabCount(); ++i) {
            TabLayout.Tab tab = mTabContainer.getTabAt(i);
            TabView view = (TabView) tab.getCustomView();
            if (view != null && view.getSessionId() == aSessionId) {
                return view;
            }
        }
        return null;
    }

    private TabView findTabView(GeckoSession aSession) {
        Integer id = SessionStore.get().getSessionId(aSession);
        if (id != null) {
            return findTabView(id);
        }
        return null;
    }

    private void togglePrivateBrowsing() {
        mIsPrivateBrowsing = !mIsPrivateBrowsing;
        boolean prevAnimateTabs = mAnimateTabs;
        mAnimateTabs = false;

        List<Integer> sessionIds = SessionStore.get().getSessionsByPrivateMode(mIsPrivateBrowsing);
        if (sessionIds.size() == 0) {
            // The new set must have at least one session
            SessionStore.SessionSettings settings = new SessionStore.SessionSettings();
            settings.privateMode = mIsPrivateBrowsing;
            int sessionId = SessionStore.get().createSession(settings);
            SessionStore.get().setCurrentSession(sessionId);
            SessionStore.get().loadUri(SessionStore.DEFAULT_URL);
        } else {
            // Make the first session of the new set current
            SessionStore.get().setCurrentSession(sessionIds.get(0));
        }

        mNavigationBar.setIsPrivate(mIsPrivateBrowsing);
        fillTabs();
        if (mMoreMenu != null) {
            mMoreMenu.updatePrivateBrowsing(mIsPrivateBrowsing);
        }
        if (mTabOverflowMenu != null) {
            mTabOverflowMenu.updatePrivateBrowsing(mIsPrivateBrowsing);
        }
        mAnimateTabs = prevAnimateTabs;
    }

    // SessionStore.SessionChangeListener
    @Override
    public void onNewSession(GeckoSession aSession, int aId) {
        aSession.getSettings().setBoolean(GeckoSessionSettings.USE_PRIVATE_MODE, mIsPrivateBrowsing);
        TabLayout.Tab tab = createTab(aId, false, mAnimateTabs);
        if (aSession == SessionStore.get().getCurrentSession()) {
            mTabContainer.scrollToTab(tab, false);
            tab.select();
        }
    }

    @Override
    public void onRemoveSession(GeckoSession aSession, int aId) {
        TabLayout.Tab tab = findTab(aId);
        if (tab != null) {
            mTabContainer.removeTabAnimated(tab);
        }
    }

    @Override
    public void onCurrentSessionChange(GeckoSession aSession, int aId) {
        TabLayout.Tab tab = findTab(aId);
        if (tab != null) {
            mTabContainer.scrollToTab(tab, false);
            tab.select();
        }
    }


    // GeckoSession.ProgressDelegate
    @Override
    public void onPageStart(GeckoSession aSession, String aURL) {
        TabView view = findTabView(aSession);
        if (view != null && !aURL.contains("file://")) {
            view.setIsLoading(true);
        }
    }

    @Override
    public void onPageStop(GeckoSession aSession, boolean b) {
        TabView view = findTabView(aSession);
        if (view != null) {
            view.setIsLoading(false);
        }
    }

    @Override
    public void onSecurityChange(GeckoSession aSession, SecurityInformation securityInformation) {

    }

    // GeckoSession.ContentDelegate
    @Override
    public void onTitleChange(GeckoSession aSession, String aTitle) {
        TabView view = findTabView(aSession);
        if (view != null) {
            view.setTitle(aTitle);
        }
    }

    @Override
    public void onFocusRequest(GeckoSession aSession) {

    }

    @Override
    public void onCloseRequest(GeckoSession aSession) {

    }

    @Override
    public void onFullScreen(GeckoSession aSession, boolean b) {

    }

    @Override
    public void onContextMenu(GeckoSession aSession, int i, int i1, String s, int i2, String s1) {

    }

    @Override
    public void onExternalResponse(GeckoSession session, GeckoSession.WebResponseInfo response) {

    }

    // CustomTabLayout.TruncateDelegate
    @Override
    public void onTruncateChange(boolean truncate) {
        if (mIsTruncatingTabs == truncate) {
            return;
        }

        mIsTruncatingTabs = truncate;
        if (truncate) {
            mTruncateContainer.setVisibility(View.VISIBLE);
            mTabsScrollLeftButton.setVisibility(View.VISIBLE);
            mAddTabButton.setVisibility(View.GONE);
        } else {
            mTruncateContainer.setVisibility(View.GONE);
            mTabsScrollLeftButton.setVisibility(View.GONE);
            mAddTabButton.setVisibility(View.VISIBLE);
        }

        // Update margin for add button
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)mTabContainer.getLayoutParams();
        params.setMargins(params.leftMargin, params.topMargin, truncate ? 0 : mTabLayoutAddMargin, params.bottomMargin);
        mTabContainer.setLayoutParams(params);
    }

    @Override
    public void onTabUsedSpaceChange(int width) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)mAddTabButton.getLayoutParams();
        int prev = params.leftMargin;
        params.setMargins(mHeaderButtonMargin + width, params.topMargin, params.rightMargin, params.bottomMargin);
        mAddTabButton.setLayoutParams(params);

        int diff = params.leftMargin - prev;
        if (!mIsTruncatingTabs & diff != 0 && mAnimateTabs) {
            // Animate layout change for adding & removing tabs
            Animation anim = new TranslateAnimation(
                    TranslateAnimation.RELATIVE_TO_SELF, -diff/ mAddTabButton.getMeasuredWidth(),
                    TranslateAnimation.RELATIVE_TO_SELF, 0.0f,
                    TranslateAnimation.RELATIVE_TO_SELF,0.0f,
                    TranslateAnimation.RELATIVE_TO_SELF,0.0f);
            anim.setFillAfter(false);
            anim.setDuration(TabView.TAB_ANIMATION_DURATION);
            mAddTabButton.startAnimation(anim);
        }
    }

    // MoreMenu Delegate
    @Override
    public void onTogglePrivateBrowsing() {
        togglePrivateBrowsing();
        mWidgetManager.updateWidget(mMoreMenu.getHandle(), false, null);
    }

    @Override
    public void onFocusModeClick() {
        mWidgetManager.updateWidget(mMoreMenu.getHandle(), false, null);
    }

    @Override
    public void onMenuCloseClick() {
        mWidgetManager.updateWidget(mMoreMenu.getHandle(), false, null);
    }

    // TabOverFlow Delegate

    @Override
    public void onTabOverflowClick(int aSessionId) {
        if (SessionStore.get().getCurrentSessionId() != aSessionId) {
            SessionStore.get().setCurrentSession(aSessionId);
        }
        hideTabOverflow();
    }

}
