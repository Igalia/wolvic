package com.igalia.wolvic.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.utils.SystemUtils;
import com.igalia.wolvic.utils.UrlUtils;

import mozilla.components.browser.icons.IconRequest;

public class TabsBarItem extends RelativeLayout implements WSession.ContentDelegate, WSession.NavigationDelegate {

    private static final String LOGTAG = SystemUtils.createLogtag(TabsBarItem.class);

    public enum Mode {ADD_TAB, TAB_DETAILS}

    protected Mode mMode = Mode.TAB_DETAILS;
    protected ViewGroup mTabDetailsView;
    protected ViewGroup mAddTabView;
    protected ImageView mFavicon;
    protected TextView mSubtitle;
    protected TextView mTitle;
    protected UIButton mCloseButton;
    protected Delegate mDelegate;
    protected Session mSession;
    protected ViewModel mViewModel;

    public interface Delegate {
        void onAdd(TabsBarItem aSender);

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
        mAddTabView = findViewById(R.id.add_tab);

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

        setMode(mMode);
    }

    private final OnClickListener mClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mDelegate != null) {
                if (mMode == Mode.ADD_TAB) {
                    mDelegate.onAdd(TabsBarItem.this);
                } else {
                    mDelegate.onClick(TabsBarItem.this);
                }
            }
        }
    };

    public void attachToSession(@Nullable Session aSession) {
        if (mSession != null) {
            mSession.removeContentListener(this);
        }

        mSession = aSession;
        if (mSession != null) {
            mSession.addContentListener(this);

            Log.e(LOGTAG, "attachToSession: " + mSession.getCurrentTitle() + " " + mSession.getCurrentUri());

            mTitle.setText(mSession.getCurrentTitle());
            mSubtitle.setText(UrlUtils.stripProtocol(mSession.getCurrentUri()));
            SessionStore.get().getBrowserIcons().loadIntoView(
                    mFavicon, mSession.getCurrentUri(), IconRequest.Size.DEFAULT);

            setActive(mSession.isActive());
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

    public void reset() {
        mCloseButton.setHovered(false);
        setMode(Mode.TAB_DETAILS);
    }

    public void setMode(Mode mode) {
        mMode = mode;
        mAddTabView.setVisibility((mMode == Mode.ADD_TAB) ? VISIBLE : GONE);
        mTabDetailsView.setVisibility((mMode == Mode.TAB_DETAILS) ? VISIBLE : GONE);
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
    public void onCloseRequest(@NonNull WSession aSession) {
        if (mSession.getWSession() == aSession) {
            mDelegate.onClose(this);
        }
    }

    public void setActive(boolean isActive) {
        setSelected(isActive);
    }
}
