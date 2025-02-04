package com.igalia.wolvic.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.SessionChangeListener;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.WindowWidget;
import com.igalia.wolvic.utils.StringUtils;
import com.igalia.wolvic.utils.UrlUtils;

import java.util.Objects;

import mozilla.components.browser.icons.IconRequest;

public class TabsBarItem extends RelativeLayout implements WSession.ContentDelegate, WSession.NavigationDelegate,
        SessionChangeListener {

    protected ViewGroup mTabDetailsView;
    protected ImageView mFavicon;
    protected TextView mSubtitle;
    protected TextView mTitle;
    protected Button mCloseButton;
    protected Delegate mDelegate;
    protected Session mSession;
    protected ViewModel mViewModel;

    public interface Delegate {
        void onClick(TabsBarItem aSender);

        void onClose(TabsBarItem aSender);
    }

    public TabsBarItem(Context context) {
        super(context);
    }

    public TabsBarItem(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TabsBarItem(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mTabDetailsView = findViewById(R.id.tab_details);

        mCloseButton = findViewById(R.id.tab_close_button);
        mCloseButton.setOnClickListener(v -> {
            v.requestFocusFromTouch();
            if (mDelegate != null) {
                mDelegate.onClose(this);
            }
        });

        mFavicon = findViewById(R.id.tab_favicon);
        mTitle = findViewById(R.id.tab_title);
        mSubtitle = findViewById(R.id.tab_subtitle);

        this.setOnClickListener(mClickListener);
    }

    private final OnClickListener mClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mDelegate != null) {
                mDelegate.onClick(TabsBarItem.this);
            }
        }
    };

    public void attachToSession(@Nullable Session aSession) {
        if (mSession != null) {
            mSession.removeContentListener(this);
            mSession.removeNavigationListener(this);
            mSession.removeSessionChangeListener(this);
        }

        mSession = aSession;
        if (mSession != null) {
            mSession.addContentListener(this);
            mSession.addNavigationListener(this);
            mSession.addSessionChangeListener(this);

            String title = mSession.getCurrentTitle();
            String uri = mSession.getCurrentUri();
            if (StringUtils.isEmpty(title)) {
                title = UrlUtils.stripCommonSubdomains(UrlUtils.getHost(uri));
            }
            mTitle.setText(title);
            mSubtitle.setText(UrlUtils.stripProtocol(uri));

            SessionStore.get().getBrowserIcons().loadIntoView(
                    mFavicon, mSession.getCurrentUri(), IconRequest.Size.DEFAULT);

            if (getContext() instanceof WidgetManagerDelegate) {
                WidgetManagerDelegate widgetManager = (WidgetManagerDelegate) getContext();
                WindowWidget focusedWindow = widgetManager.getFocusedWindow();
                if (focusedWindow != null && focusedWindow.getSession() != null) {
                    setActive(mSession.isActive() && Objects.equals(mSession.getId(), focusedWindow.getSession().getId()));
                }
            } else {
                setActive(mSession.isActive());
            }
        } else {
            // Null session
            mTitle.setText(null);
            mSubtitle.setText(null);
            mFavicon.setImageDrawable(null);
        }
    }

    public Session getSession() {
        return mSession;
    }

    public void setDelegate(Delegate aDelegate) {
        mDelegate = aDelegate;
    }

    @Override
    public void onTitleChange(@NonNull WSession session, @Nullable String title) {
        if (mSession == null || mSession.getWSession() != session) {
            return;
        }
        mTitle.setText(title);
    }

    @Override
    public void onLocationChange(@NonNull WSession session, @Nullable String url) {
        if (mSession == null || mSession.getWSession() != session) {
            return;
        }

        if (url == null) {
            mSubtitle.setText(null);
            mFavicon.setImageDrawable(null);
        } else {
            mSubtitle.setText(UrlUtils.stripProtocol(mSession.getCurrentUri()));
            SessionStore.get().getBrowserIcons().loadIntoView(
                    mFavicon, mSession.getCurrentUri(), IconRequest.Size.DEFAULT);
        }
    }

    @Override
    public void onSessionStateChanged(Session aSession, boolean aActive) {
        // TODO this should only apply to the session in the focused window
        if (Objects.equals(mSession, aSession)) {
            setActive(aActive);
        }
    }

    @Override
    public void onCloseRequest(@NonNull WSession aSession) {
        if (mSession.getWSession() == aSession) {
            mDelegate.onClose(this);
        }
    }

    public void setActive(boolean isActive) {
        setSelected(isActive);
    }
}
