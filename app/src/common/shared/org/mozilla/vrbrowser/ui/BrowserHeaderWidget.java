package org.mozilla.vrbrowser.ui;


import android.content.Context;
import android.support.design.widget.TabLayout;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.SessionStore;


public class BrowserHeaderWidget extends UIWidget
        implements TabLayout.OnTabSelectedListener, CustomTabLayout.Delegate,
        SessionStore.SessionChangeListener, GeckoSession.ContentDelegate, GeckoSession.ProgressDelegate {
    private Context mContext;
    private CustomTabLayout mTabContainer;
    private ImageButton mAddTabButton;
    private ImageButton mTabsScrollLeftButton;
    private LinearLayout mTruncateContainer;
    private boolean mIsTruncatingTabs = false;
    private int mHeaderButtonMargin;
    private int mTabLayoutAddMargin;

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
                mTabContainer.scrollLeft();
            }
        });

        ImageButton tabsScrollRightButton = findViewById(R.id.tabScrollRightButton);
        tabsScrollRightButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mTabContainer.scrollRight();
            }
        });

        ImageButton tabListAllButton = findViewById(R.id.tabListAllButton);
        tabListAllButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        ImageButton mFocusWindowButton = findViewById(R.id.focusWindowButton);
        mFocusWindowButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

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

        int currentSessionId = SessionStore.get().getCurrentSessionId();
        for (int sessionId: SessionStore.get().getSessions()) {
            createTab(sessionId, currentSessionId == sessionId);
        }

        SessionStore.get().addSessionChangeListener(this);
        SessionStore.get().addContentListener(this);
        SessionStore.get().addProgressListener(this);
    }

    private void addTabClick() {
        int sessionId = SessionStore.get().createSession();
        SessionStore.get().setCurrentSession(sessionId, mContext);
        SessionStore.get().loadUri(SessionStore.DEFAULT_URL);
    }

    private void createTab(int aSessionId, boolean aSelected) {
        TabLayout.Tab tab = mTabContainer.newTab();
        TabView tabView = new TabView(mContext);
        tabView.setSessionId(aSessionId);
        tab.setCustomView(tabView);
        mTabContainer.addTab(tab, aSelected);
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
                SessionStore.get().setCurrentSession(tabView.getSessionId(), mContext);
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

    // SessionStore.SessionChangeListener
    @Override
    public void onNewSession(GeckoSession aSession, int aId) {
        createTab(aId, false);
    }

    @Override
    public void onRemoveSession(GeckoSession aSession, int aId) {
        TabLayout.Tab tab = findTab(aId);
        if (tab != null) {
            mTabContainer.removeTab(tab);
            // Create a new session to prevent windows with 0 tabs
            if (mTabContainer.getTabCount() <= 0) {
                int id = SessionStore.get().createSession();
                SessionStore.get().setCurrentSession(id, mContext);
                SessionStore.get().loadUri(SessionStore.DEFAULT_URL);
            }
        }
    }

    @Override
    public void onCurrentSessionChange(GeckoSession aSession, int aId) {
        TabLayout.Tab tab = findTab(aId);
        if (tab != null) {
            mTabContainer.scrollToTab(tab);
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
    public void onContextMenu(GeckoSession aSession, int i, int i1, String s, String s1) {

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
        params.setMargins(mHeaderButtonMargin + width, params.topMargin, params.rightMargin, params.bottomMargin);
        mAddTabButton.setLayoutParams(params);
    }
}
