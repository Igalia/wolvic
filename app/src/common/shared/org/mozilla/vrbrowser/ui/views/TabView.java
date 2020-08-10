package org.mozilla.vrbrowser.ui.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.AnimationHelper;
import org.mozilla.vrbrowser.utils.BitmapCache;
import org.mozilla.vrbrowser.utils.SystemUtils;
import org.mozilla.vrbrowser.utils.UrlUtils;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

public class TabView extends RelativeLayout implements GeckoSession.ContentDelegate, Session.BitmapChangedListener {

    private static final String LOGTAG = SystemUtils.createLogtag(TabView.class);

    protected RelativeLayout mTabCardView;
    protected RelativeLayout mTabAddView;
    protected View mTabOverlay;
    protected ImageView mPreview;
    protected TextView mURL;
    protected TextView mTitle;
    protected UIButton mCloseButton;
    protected UIButton mSendTabButton;
    protected ImageView mSelectionImage;
    protected ImageView mUnselectImage;
    protected Delegate mDelegate;
    protected Session mSession;
    protected ImageView mTabAddIcon;
    protected View mTabShadow;
    protected boolean mShowAddTab;
    protected boolean mSelecting;
    protected boolean mActive;
    protected int mMinIconPadding;
    protected int mMaxIconPadding;
    protected boolean mPressed;
    protected CompletableFuture<Bitmap> mBitmapFuture;
    protected boolean mUsingPlaceholder;
    private boolean mSendTabEnabled;
    private static final int ICON_ANIMATION_DURATION = 100;

    public interface Delegate {
        void onClose(TabView aSender);
        void onClick(TabView aSender);
        void onAdd(TabView aSender);
        void onSend(TabView aSender);
    }

    public TabView(Context context) {
        super(context);
    }

    public TabView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TabView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mTabAddView = findViewById(R.id.tabAddView);
        mTabCardView = findViewById(R.id.tabCardView);

        mSelectionImage = findViewById(R.id.tabViewSelected);
        mSelectionImage.setVisibility(View.GONE);

        mUnselectImage = findViewById(R.id.tabViewUnselect);
        mUnselectImage.setVisibility(View.GONE);

        mSendTabButton = findViewById(R.id.tabViewSendButton);
        mSendTabButton.setVisibility(View.GONE);
        mSendTabButton.setOnClickListener(v -> {
            v.requestFocusFromTouch();
            if (mDelegate != null) {
                mDelegate.onSend(this);
            }
        });
        mSendTabButton.setOnHoverListener(mIconHoverListener);

        mCloseButton = findViewById(R.id.tabViewCloseButton);
        mCloseButton.setVisibility(View.GONE);
        mCloseButton.setOnClickListener(v -> {
            v.requestFocusFromTouch();
            if (mDelegate != null) {
                mDelegate.onClose(this);
            }
        });
        mCloseButton.setOnHoverListener(mIconHoverListener);

        mPreview = findViewById(R.id.tabViewPreview);
        mURL = findViewById(R.id.tabViewUrl);
        mTitle = findViewById(R.id.tabViewTitle);
        mTitle.setVisibility(View.GONE);
        mTabAddIcon = findViewById(R.id.tabAddIcon);
        mTabOverlay = findViewById(R.id.tabOverlay);
        mTabShadow = findViewById(R.id.tabShadow);

        mMinIconPadding = WidgetPlacement.pixelDimension(getContext(), R.dimen.tabs_icon_padding_min);
        mMaxIconPadding = WidgetPlacement.pixelDimension(getContext(), R.dimen.tabs_icon_padding_max);

        this.setOnClickListener(mCardClickListener);
    }

    private OnClickListener mCardClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mDelegate != null) {
                if (!mShowAddTab && mSession != null) {
                    mDelegate.onClick(TabView.this);
                } else {
                    mDelegate.onAdd(TabView.this);
                }
            }
        }
    };

    public void attachToSession(@Nullable Session aSession, @NonNull BitmapCache aBitmapCache) {
        if (mSession != null) {
            mSession.removeContentListener(this);
            mSession.removeBitmapChangedListener(this);
        }
        if (mBitmapFuture != null) {
            mBitmapFuture.cancel(false);
            mBitmapFuture = null;
        }
        setAddTabMode(false);
        mSession = aSession;
        mSession.addContentListener(this);
        mSession.addBitmapChangedListener(this);
        mShowAddTab = false;
        mBitmapFuture = aBitmapCache.getBitmap(mSession.getId());
        mPreview.setImageResource(R.drawable.ic_icon_tabs_placeholder);
        mUsingPlaceholder = true;
        mBitmapFuture.thenAccept(bitmap -> {
            mBitmapFuture = null;
            if (bitmap != null) {
                mPreview.setImageBitmap(bitmap);
                mUsingPlaceholder = false;
                updateState();
            }

        }).exceptionally(throwable -> {
            Log.d(LOGTAG, "Error getting the bitmap: " + throwable.getLocalizedMessage());
            throwable.printStackTrace();
            return null;
        });

        mURL.setText(UrlUtils.stripProtocol(aSession.getCurrentUri()));
        if (!mShowAddTab) {
            if (mSession.getCurrentUri().equals(mSession.getHomeUri())) {
                mTitle.setText(getResources().getString(R.string.url_home_title, getResources().getString(R.string.app_name)));
            } else {
                mTitle.setText(aSession.getCurrentTitle());
            }
        }
        updateState();
    }

    public Session getSession() {
        return mSession;
    }

    public void setDelegate(Delegate aDelegate) {
        mDelegate = aDelegate;
    }

    public void setAddTabMode(boolean aShow) {
        if (mShowAddTab == aShow) {
            return;
        }
        mShowAddTab = aShow;
        if (mShowAddTab) {
            mTabCardView.setVisibility(View.GONE);
            mURL.setVisibility(View.INVISIBLE);
            mTabAddView.setVisibility(View.VISIBLE);
        } else {
            mTabCardView.setVisibility(View.VISIBLE);
            mTabAddView.setVisibility(View.GONE);
            mURL.setVisibility(View.VISIBLE);
        }
    }

    public void setSelecting(boolean aSelecting) {
        mSelecting = aSelecting;
        if (!mSelecting) {
            setSelected(false);
        }
        if (mShowAddTab) {
            setOnClickListener(mSelecting ? null : mCardClickListener);
            mTabAddView.setDuplicateParentStateEnabled(!mSelecting);
        }
    }

    public void setActive(boolean aActive) {
        if (mActive != aActive) {
            mActive = aActive;
            updateState();
        }
    }

    public void setSendTabEnabled(boolean enabled) {
        mSendTabEnabled = enabled;
    }

    public void reset() {
        mSendTabButton.setHovered(false);
        mCloseButton.setHovered(false);
        updateState();
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        updateState();
        if (selected) {
            mSelectionImage.setVisibility(View.VISIBLE);
            mUnselectImage.setVisibility(View.GONE);
        }
    }

    @Override
    public void onHoverChanged(boolean aHovered) {
        super.onHoverChanged(aHovered);
        updateState();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean result = super.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mPressed = true;
            updateState();
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            mPressed = false;
            updateState();
        }
        return result;
    }

    private void updateState() {
        boolean hovered = isHovered() || mCloseButton.isHovered() || mSendTabButton.isHovered();
        boolean interacted = hovered || mPressed;
        boolean selected = isSelected();

        mCloseButton.setVisibility(interacted && !selected && !mSelecting ? View.VISIBLE : View.GONE);
        mSendTabButton.setVisibility(mSendTabEnabled && interacted && !selected && !mSelecting ? View.VISIBLE : View.GONE);
        mTitle.setVisibility(interacted && !selected ? View.VISIBLE : View.GONE);
        mTabOverlay.setPressed(mPressed);
        if (mSelecting) {
            mTabOverlay.setBackgroundResource(selected ? R.drawable.tab_overlay_checked : R.drawable.tab_overlay_unchecked);
        } else if (mActive) {
            mTabOverlay.setBackgroundResource(R.drawable.tab_overlay_active);
        } else {
            mTabOverlay.setBackgroundResource(mUsingPlaceholder ? R.drawable.tab_overlay_placeholder : R.drawable.tab_overlay);
        }
        mTabShadow.setVisibility(interacted && mSelecting && !mShowAddTab ? View.VISIBLE : View.GONE);

        mSelectionImage.setVisibility(selected && !interacted ? View.VISIBLE : View.GONE);
        mUnselectImage.setVisibility(selected && interacted ? View.VISIBLE : View.GONE);
        if (mShowAddTab) {
            mTabAddView.setPressed(mPressed && !mSelecting);
            mTabAddIcon.setPressed(mTabAddView.isPressed());
            if (mTabAddIcon.isHovered() && mTabAddIcon.getPaddingLeft() != mMinIconPadding) {
                AnimationHelper.animateViewPadding(mTabAddIcon, mTabAddIcon.getPaddingLeft(), mMinIconPadding, ICON_ANIMATION_DURATION);
            } else if (!mTabAddIcon.isHovered() && mTabAddIcon.getPaddingLeft() != mMaxIconPadding) {
                AnimationHelper.animateViewPadding(mTabAddIcon, mTabAddIcon.getPaddingLeft(), mMaxIconPadding, ICON_ANIMATION_DURATION);
            }
        }
    }

    @Override
    public void onTitleChange(@NonNull GeckoSession session, @Nullable String title) {
        if (!mShowAddTab) {
            mTitle.setText(title);
        }
    }

    @Override
    public void onCloseRequest(@NonNull GeckoSession geckoSession) {
        if (mSession.getGeckoSession() == geckoSession) {
            mDelegate.onClose(this);
        }
    }

    @Override
    public void onBitmapChanged(Session aSession, Bitmap aBitmap) {
        if (aSession != mSession) {
            return;
        }
        if (aBitmap != null) {
            mPreview.setImageBitmap(aBitmap);
            mUsingPlaceholder = false;
        } else {
            mPreview.setImageResource(R.drawable.ic_icon_tabs_placeholder);
            mUsingPlaceholder = true;
        }

        updateState();
    }

    private View.OnHoverListener mIconHoverListener = (view, motionEvent) -> {
        int ev = motionEvent.getActionMasked();
        switch (ev) {
            case MotionEvent.ACTION_HOVER_ENTER:
                AnimationHelper.animateViewPadding(view,
                        mMaxIconPadding,
                        mMinIconPadding,
                        ICON_ANIMATION_DURATION,
                        this::updateState);
                post(() -> setHovered(true));
                return false;

            case MotionEvent.ACTION_HOVER_EXIT:
                AnimationHelper.animateViewPadding(view,
                        mMinIconPadding,
                        mMaxIconPadding,
                        ICON_ANIMATION_DURATION,
                        this::updateState);
                setHovered(false);
                return false;
        }

        return false;
    };
}
