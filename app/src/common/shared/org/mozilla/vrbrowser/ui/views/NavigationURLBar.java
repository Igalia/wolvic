/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.views;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Resources;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.mozilla.gecko.util.ThreadUtils;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.BookmarksStore;
import org.mozilla.vrbrowser.browser.engine.SessionStack;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.search.SearchEngineWrapper;
import org.mozilla.vrbrowser.telemetry.TelemetryWrapper;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.ui.widgets.dialogs.SelectionActionWidget;
import org.mozilla.vrbrowser.utils.StringUtils;
import org.mozilla.vrbrowser.utils.SystemUtils;
import org.mozilla.vrbrowser.utils.UIThreadExecutor;
import org.mozilla.vrbrowser.utils.UrlUtils;
import org.mozilla.vrbrowser.utils.ViewUtils;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;

import kotlin.Unit;
import mozilla.components.browser.domains.autocomplete.DomainAutocompleteResult;
import mozilla.components.browser.domains.autocomplete.ShippedDomainsProvider;
import mozilla.components.ui.autocomplete.InlineAutocompleteEditText;

public class NavigationURLBar extends FrameLayout {

    private static final String LOGTAG = SystemUtils.createLogtag(NavigationURLBar.class);

    private CustomInlineAutocompleteEditText mURL;
    private UIButton mMicrophoneButton;
    private UIButton mUAModeButton;
    private ImageView mInsecureIcon;
    private ImageView mLoadingView;
    private Animation mLoadingAnimation;
    private RelativeLayout mURLLeftContainer;
    private View mHintFading;
    private boolean mIsLoading = false;
    private boolean mIsInsecure = false;
    private boolean mIsPrivateMode = false;
    private int mDefaultURLLeftPadding = 0;
    private int mURLProtocolColor;
    private int mURLWebsiteColor;
    private NavigationURLBarDelegate mDelegate;
    private ShippedDomainsProvider mAutocompleteProvider;
    private UIButton mBookmarkButton;
    private AudioEngine mAudio;
    private boolean mIsContentMode;
    private boolean mBookmarkEnabled = true;
    private boolean mIsContextButtonsEnabled = true;
    private UIThreadExecutor mUIThreadExecutor = new UIThreadExecutor();
    private SessionStack mSessionStack;
    private SelectionActionWidget mSelectionMenu;
    private boolean mWasFocusedWhenTouchBegan = false;
    private boolean mLongPressed = false;
    private int lastTouchDownOffset = 0;

    private Unit domainAutocompleteFilter(String text) {
        if (mURL != null) {
            DomainAutocompleteResult result = mAutocompleteProvider.getAutocompleteSuggestion(text);
            if (result != null) {
                mURL.applyAutocompleteResult(new InlineAutocompleteEditText.AutocompleteResult(
                        result.getText(),
                        result.getSource(),
                        result.getTotalItems(),
                        null));
            } else {
                mURL.noAutocompleteResult();
            }
        }
        return Unit.INSTANCE;
    }

    public interface NavigationURLBarDelegate {
        void onVoiceSearchClicked();
        void onShowSearchPopup();
        void onHideSearchPopup();
        void onLongPress(float centerX, SelectionActionWidget actionMenu);
    }

    public NavigationURLBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initialize(Context aContext) {
        mAudio = AudioEngine.fromContext(aContext);

        mSessionStack = SessionStore.get().getActiveStore();

        // Inflate this data binding layout
        inflate(aContext, R.layout.navigation_url, this);

        // Use Domain autocomplete provider from components
        mAutocompleteProvider = new ShippedDomainsProvider();
        mAutocompleteProvider.initialize(aContext);

        mURL = findViewById(R.id.urlEditText);
        mURL.setShowSoftInputOnFocus(false);
        mURL.setOnEditorActionListener((aTextView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEND) {
                handleURLEdit(aTextView.getText().toString());
                return true;
            }
            return false;
        });

        mURL.setOnFocusChangeListener((view, focused) -> {
            showVoiceSearch(!focused || (mURL.getText().length() == 0));
            showContextButtons(!focused && mIsContextButtonsEnabled);
            updateHintFading();

            mURL.setSelection(mURL.getText().length(), 0);
            if (focused) {
                mURL.selectAll();
            } else {
                hideSelectionMenu();
            }

        });

        final GestureDetector gd = new GestureDetector(getContext(), new UrlGestureListener());
        gd.setOnDoubleTapListener(mUrlDoubleTapListener);
        mURL.setOnTouchListener((view, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                mWasFocusedWhenTouchBegan = view.isFocused();
                lastTouchDownOffset = ViewUtils.getCursorOffset(mURL, motionEvent.getX());
            } else if (mLongPressed && motionEvent.getAction() == MotionEvent.ACTION_MOVE) {
                // Selection gesture while longpressing
                ViewUtils.placeSelection(mURL, lastTouchDownOffset, ViewUtils.getCursorOffset(mURL, motionEvent.getX()));
            } else if (motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                mLongPressed = false;
            }
            if (gd.onTouchEvent(motionEvent)) {
                return true;
            }
            if (mLongPressed) {
                // Do not scroll editable when selecting text after a long press.
                return true;
            }
            return view.onTouchEvent(motionEvent);
        });
        mURL.setOnClickListener(v -> {
            if (mWasFocusedWhenTouchBegan) {
                hideSelectionMenu();
            }
        });
        mURL.setOnLongClickListener(v -> {
            if (!v.isFocused()) {
                mURL.requestFocus();
                mURL.selectAll();
            } else if (!mURL.hasSelection()) {
                // Place the cursor in the longpressed position.
                if (lastTouchDownOffset >= 0) {
                    mURL.setSelection(lastTouchDownOffset);
                }
                mLongPressed = true;
            }
            // Add some delay so selection ranges are ready
            ThreadUtils.postDelayedToUiThread(this::handleLongPress, 10);
            return true;
        });
        mURL.addTextChangedListener(mURLTextWatcher);

        mURL.setOnSelectionChangedCallback((start, end) -> {
            if (mSelectionMenu != null) {
                boolean hasCopy = mSelectionMenu.hasAction(GeckoSession.SelectionActionDelegate.ACTION_COPY);
                boolean showCopy = end > start;
                if (hasCopy != showCopy) {
                    handleLongPress();
                } else {
                    mDelegate.onLongPress(getSelectionCenterX(), mSelectionMenu);
                    mSelectionMenu.updateWidget();
                }
            }
        });

        mURL.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (mLongPressed) {
                hideSelectionMenu();
            }
        });

        // Set a filter to provide domain autocomplete results
        mURL.setOnFilterListener(this::domainAutocompleteFilter);

        mURL.setFocusable(true);
        mURL.setFocusableInTouchMode(true);

        mMicrophoneButton = findViewById(R.id.microphoneButton);
        mMicrophoneButton.setTag(R.string.view_id_tag, R.id.microphoneButton);
        mMicrophoneButton.setOnClickListener(mMicrophoneListener);

        mUAModeButton = findViewById(R.id.uaModeButton);
        mUAModeButton.setTag(R.string.view_id_tag, R.id.uaModeButton);
        mUAModeButton.setOnClickListener(mUAModeListener);
        setUAMode(mSessionStack.getUaMode());

        mHintFading = findViewById(R.id.urlBarHintFadingEdge);
        mURLLeftContainer = findViewById(R.id.urlLeftContainer);
        mInsecureIcon = findViewById(R.id.insecureIcon);
        mLoadingView = findViewById(R.id.loadingView);
        mLoadingAnimation = AnimationUtils.loadAnimation(aContext, R.anim.loading);
        mDefaultURLLeftPadding = mURL.getPaddingLeft();

        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = aContext.getTheme();
        theme.resolveAttribute(R.attr.urlProtocolColor, typedValue, true);
        mURLProtocolColor = typedValue.data;
        theme.resolveAttribute(R.attr.urlWebsiteColor, typedValue, true);
        mURLWebsiteColor = typedValue.data;

        // Bookmarks
        mBookmarkButton = findViewById(R.id.bookmarkButton);
        mBookmarkButton.setOnClickListener(v -> handleBookmarkClick());
        mIsContentMode = false;

        // Prevent the URL TextEdit to get focus when user touches something outside of it
        setFocusable(true);
        setClickable(true);
        syncViews();
        updateHintFading();
    }

    public void setSessionStack(SessionStack sessionStack) {
        mSessionStack = sessionStack;
        setUAMode(mSessionStack.getUaMode());
    }

    public void onPause() {
        if (mIsLoading) {
            mLoadingView.clearAnimation();
        }
    }

    public void onResume() {
        if (mIsLoading) {
            mLoadingView.startAnimation(mLoadingAnimation);
        }
    }

    public void setDelegate(NavigationURLBarDelegate delegate) {
        mDelegate = delegate;
    }

    public void setIsContentMode(boolean isContentMode) {
        if (mIsContentMode == isContentMode) {
            return;
        }
        mIsContentMode = isContentMode;
        if (isContentMode) {
            mMicrophoneButton.setVisibility(GONE);
            mUAModeButton.setVisibility(GONE);
            mBookmarkButton.setVisibility(GONE);

        } else {
            mMicrophoneButton.setVisibility(VISIBLE);
            mUAModeButton.setVisibility(VISIBLE);
            if (mBookmarkEnabled) {
                mBookmarkButton.setVisibility(VISIBLE);
            }
        }
        syncViews();
        updateRightPadding();
    }

    public boolean isInBookmarkMode() {
        return mIsContentMode;
    }

    private void setBookmarkEnabled(boolean aEnabled) {
        if (mBookmarkEnabled != aEnabled) {
            mBookmarkEnabled = aEnabled;
            mBookmarkButton.setVisibility(aEnabled ? View.VISIBLE : View.GONE);
            ViewGroup.LayoutParams params = mMicrophoneButton.getLayoutParams();
            params.width = (int) getResources().getDimension(aEnabled ? R.dimen.url_bar_item_width : R.dimen.url_bar_last_item_width);
            mMicrophoneButton.setLayoutParams(params);
            if (mIsPrivateMode) {
                mMicrophoneButton.setBackgroundResource(aEnabled ? R.drawable.url_button_private : R.drawable.url_button_end_private);
            } else {
                mMicrophoneButton.setBackgroundResource(aEnabled ? R.drawable.url_button : R.drawable.url_button_end);
            }
        }
    }

    private void handleBookmarkClick() {
        if (mAudio != null) {
            mAudio.playSound(AudioEngine.Sound.CLICK);
        }

        String url = mSessionStack.getCurrentUri();
        if (StringUtils.isEmpty(url)) {
            return;
        }
        BookmarksStore bookmarkStore = SessionStore.get().getBookmarkStore();
        bookmarkStore.isBookmarked(url).thenAcceptAsync(bookmarked -> {
            if (!bookmarked) {
                bookmarkStore.addBookmark(url, mSessionStack.getCurrentTitle());
                setBookmarked(true);
            } else {
                // Delete
                bookmarkStore.deleteBookmarkByURL(url);
                setBookmarked(false);
            }
        }, mUIThreadExecutor).exceptionally(th -> {
            Log.d(LOGTAG, "Error getting bookmarks: " + th.getLocalizedMessage());
            return null;
        });

    }

    private void setBookmarked(boolean aValue) {
        if (aValue) {
            mBookmarkButton.setImageDrawable(getContext().getDrawable(R.drawable.ic_icon_bookmarked_active));
        } else {
            mBookmarkButton.setImageDrawable(getContext().getDrawable(R.drawable.ic_icon_bookmarked));
        }
    }

    public void setHint(@StringRes int aHint) {
        mURL.setHint(aHint);
    }

    public void setInsecureVisibility(int visibility) {
        mInsecureIcon.setVisibility(visibility);
    }

    public void setURL(String aURL) {
        if (mIsContentMode) {
            return;
        }
        mURL.removeTextChangedListener(mURLTextWatcher);
        if (StringUtils.isEmpty(aURL)) {
            setBookmarked(false);
        } else {
            SessionStore.get().getBookmarkStore().isBookmarked(aURL).thenAcceptAsync(this::setBookmarked, mUIThreadExecutor);
        }

        int index = -1;
        if (aURL != null) {
            try {
                aURL = URLDecoder.decode(aURL, "UTF-8");

            } catch (UnsupportedEncodingException | IllegalArgumentException e) {
                e.printStackTrace();
                aURL = "";
            }
            if (aURL.startsWith("jar:")) {
                return;

            } else if (aURL.startsWith("resource:") || mSessionStack.isHomeUri(aURL)) {

                aURL = "";
            } else if (aURL.startsWith("data:") && mSessionStack.isPrivateMode()) {
                aURL = "";

            } else if (aURL.startsWith(getContext().getString(R.string.about_blank))) {
                aURL = "";

            } else {
                index = aURL.indexOf("://");
            }

            // Update the URL bar only if the URL is different than the current one and
            // the URL bar is not focused to avoid override user input
            if (!mURL.getText().toString().equalsIgnoreCase(aURL) && !mURL.isFocused()) {
                mURL.setText(aURL);
                if (index > 0) {
                    SpannableString spannable = new SpannableString(aURL);
                    ForegroundColorSpan color1 = new ForegroundColorSpan(mURLProtocolColor);
                    ForegroundColorSpan color2 = new ForegroundColorSpan(mURLWebsiteColor);
                    spannable.setSpan(color1, 0, index + 3, 0);
                    spannable.setSpan(color2, index + 3, aURL.length(), 0);
                    mURL.setText(spannable);

                } else {
                    mURL.setText(aURL);
                }
            }
            mIsContextButtonsEnabled = aURL.length() > 0 && !aURL.startsWith("about://");

            if (!aURL.equals(getResources().getString(R.string.url_bookmarks_title)) &&
                    !aURL.equals(getResources().getString(R.string.url_history_title))) {
                showContextButtons(mIsContextButtonsEnabled);
            }
        }

        mURL.addTextChangedListener(mURLTextWatcher);
    }

    private boolean isEmptyUrl(@NonNull String aURL) {
        return aURL.length() == 0 || aURL.startsWith("about://");
    }

    public String getText() {
        return mURL.getText().toString();
    }

    public String getOriginalText() {
        try {
            return mURL.getOriginalText();

        } catch (IndexOutOfBoundsException e) {
            return mURL.getNonAutocompleteText();
        }
    }

    public void setIsInsecure(boolean aIsInsecure) {
        if (mIsInsecure != aIsInsecure) {
            mIsInsecure = aIsInsecure;
            syncViews();
        }
    }

    public void setIsLoading(boolean aIsLoading) {
        if (mIsLoading != aIsLoading) {
            mIsLoading = aIsLoading;
            if (mIsLoading) {
                mLoadingView.startAnimation(mLoadingAnimation);
            } else {
                mLoadingView.clearAnimation();
            }
            syncViews();
        }
    }

    public void setUAMode(int uaMode) {
        if (uaMode == GeckoSessionSettings.USER_AGENT_MODE_DESKTOP) {
            mUAModeButton.setImageResource(R.drawable.ic_icon_ua_desktop);

        } else {
            mUAModeButton.setImageResource(R.drawable.ic_icon_ua_default);
        }
    }

    private void showContextButtons(boolean aEnabled) {
        if (mBookmarkEnabled != aEnabled) {
            mBookmarkEnabled = aEnabled;
        }

        if (aEnabled) {
            mMicrophoneButton.setBackgroundResource(mIsPrivateMode ? R.drawable.url_button_private : R.drawable.url_button);
            mMicrophoneButton.getLayoutParams().width = (int)getContext().getResources().getDimension(R.dimen.url_bar_item_width);
            mBookmarkButton.setVisibility(VISIBLE);
            mUAModeButton.setVisibility(VISIBLE);

        } else {
            mMicrophoneButton.setBackgroundResource(mIsPrivateMode ? R.drawable.url_button_end_private : R.drawable.url_button_end);
            mMicrophoneButton.getLayoutParams().width = (int)getContext().getResources().getDimension(R.dimen.url_bar_last_item_width);
            mBookmarkButton.setVisibility(GONE);
            mUAModeButton.setVisibility(GONE);
        }
        updateRightPadding();
    }

    public void showVoiceSearch(boolean enabled) {
        if (enabled) {
            mMicrophoneButton.setImageResource(R.drawable.ic_icon_microphone);
            mMicrophoneButton.setTooltip(getResources().getString(R.string.voice_search_tooltip));
            mMicrophoneButton.setOnClickListener(mMicrophoneListener);

        } else if (mURL.hasFocus()){
            mMicrophoneButton.setImageResource(R.drawable.ic_icon_clear);
            mMicrophoneButton.setTooltip(getResources().getString(R.string.clear_tooltip));
            mMicrophoneButton.setOnClickListener(mClearListener);
        }
        updateRightPadding();
    }

    public void updateHintFading() {
        mHintFading.setVisibility(StringUtils.isEmpty(mURL.getText()) ? View.VISIBLE : View.GONE);
        mHintFading.setEnabled(mURL.isFocused());
    }

    private void updateRightPadding() {
        int padding = WidgetPlacement.convertDpToPixel(getContext(), 5);
        boolean anyButtonVisible = false;
        if (mMicrophoneButton.getVisibility() == View.VISIBLE) {
            padding += mMicrophoneButton.getLayoutParams().width;
            anyButtonVisible = true;
        }
        if (mUAModeButton.getVisibility() == View.VISIBLE) {
            padding += mUAModeButton.getLayoutParams().width;
            anyButtonVisible = true;
        }
        if (mBookmarkButton.getVisibility() == View.VISIBLE) {
            padding += mBookmarkButton.getLayoutParams().width;
            anyButtonVisible = true;
        }
        // Min padding of 20 if no icons are visible
        padding = Math.max(padding, WidgetPlacement.convertDpToPixel(getContext(), 20));
        mURL.setPadding(mURL.getPaddingLeft(), mURL.getPaddingTop(), padding, mURL.getPaddingBottom());

        // Update hint fading
        int margin = 0;
        if (anyButtonVisible) {
            mHintFading.setBackgroundResource(mIsPrivateMode ? R.drawable.url_bar_hint_fading_edge_private : R.drawable.url_bar_hint_fading_edge);
        } else {
            mHintFading.setBackgroundResource(mIsPrivateMode ? R.drawable.url_bar_hint_fading_edge_end_private : R.drawable.url_bar_hint_fading_edge_end);
            margin = WidgetPlacement.convertDpToPixel(getContext(), 5);
        }

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)mHintFading.getLayoutParams();
        params.rightMargin = margin;
        mHintFading.setLayoutParams(params);
    }

    private void syncViews() {
        boolean showContainer = (mIsInsecure || mIsLoading) && !mIsContentMode;
        int leftPadding = mDefaultURLLeftPadding;
        if (showContainer) {
            mURLLeftContainer.setVisibility(View.VISIBLE);
            mURLLeftContainer.measure(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            mLoadingView.setVisibility(mIsLoading ? View.VISIBLE : View.GONE);
            if (!mIsContentMode) {
                mInsecureIcon.setVisibility(!mIsLoading && mIsInsecure ? View.VISIBLE : View.GONE);
            }
            leftPadding = mURLLeftContainer.getMeasuredWidth();

        } else {
            mURLLeftContainer.setVisibility(View.GONE);
            mLoadingView.setVisibility(View.GONE);
            mInsecureIcon.setVisibility(View.GONE);
        }

        mURL.setPadding(leftPadding, mURL.getPaddingTop(), mURL.getPaddingRight(), mURL.getPaddingBottom());
    }

    public  void handleURLEdit(String text) {
        text = text.trim();
        URI uri = null;
        try {
            boolean hasProtocol = text.contains("://");
            String urlText = text;
            // Detect when the protocol is missing from the URL.
            // Look for a separated '.' in the text with no white spaces.
            if (!hasProtocol && !urlText.contains(" ") && UrlUtils.isDomain(urlText)) {
                urlText = "https://" + urlText;
                hasProtocol = true;
            }
            if (hasProtocol) {
                URL url = new URL(urlText);
                uri = url.toURI();
            }
        }
        catch (Exception ex) {
        }

        String url;
        if (uri != null) {
            url = uri.toString();
            TelemetryWrapper.urlBarEvent(true);
        } else if (text.startsWith("about:") || text.startsWith("resource://")) {
            url = text;
        } else {
            url = SearchEngineWrapper.get(getContext()).getSearchURL(text);

            // Doing search in the URL bar, so sending "aIsURL: false" to telemetry.
            TelemetryWrapper.urlBarEvent(false);
        }

        if (mSessionStack.getCurrentUri() != url) {
            mSessionStack.loadUri(url);

            if (mDelegate != null) {
                mDelegate.onHideSearchPopup();
            }
        }

        showVoiceSearch(text.isEmpty());
    }

    public void setPrivateMode(boolean isEnabled) {
        mIsPrivateMode = isEnabled;
        if (isEnabled) {
            mURL.setBackground(getContext().getDrawable(R.drawable.url_background_private));

        } else {
            mURL.setBackground(getContext().getDrawable(R.drawable.url_background));
        }


        int background = isEnabled ? R.drawable.url_button_private : R.drawable.url_button;
        int backgroundEnd = isEnabled ? R.drawable.url_button_end_private : R.drawable.url_button_end;

        mBookmarkButton.setBackgroundResource(backgroundEnd);
        mUAModeButton.setBackgroundResource(background);
        if (mBookmarkButton.getVisibility() == View.VISIBLE) {
            mMicrophoneButton.setBackgroundResource(background);
        } else {
            mMicrophoneButton.setBackgroundResource(backgroundEnd);
        }
        updateRightPadding();
    }

    @Override
    public void setClickable(boolean clickable) {
        super.setClickable(clickable);
        mURL.setEnabled(clickable);
    }

    private OnClickListener mMicrophoneListener = view -> {
        if (mAudio != null) {
            mAudio.playSound(AudioEngine.Sound.CLICK);
        }

        view.requestFocusFromTouch();
        if (mDelegate != null) {
            mDelegate.onVoiceSearchClicked();
        }

        TelemetryWrapper.voiceInputEvent();
    };

    private OnClickListener mUAModeListener = view -> {
        if (mAudio != null) {
            mAudio.playSound(AudioEngine.Sound.CLICK);
        }
        view.requestFocusFromTouch();

        int uaMode = mSessionStack.getUaMode();
        if (uaMode == GeckoSessionSettings.USER_AGENT_MODE_DESKTOP) {
            setUAMode(GeckoSessionSettings.USER_AGENT_MODE_VR);
            mSessionStack.setUaMode(GeckoSessionSettings.USER_AGENT_MODE_VR);

        }else {
            setUAMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP);
            mSessionStack.setUaMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP);
        }

        TelemetryWrapper.voiceInputEvent();
    };

    private OnClickListener mClearListener = view -> {
        if (mAudio != null) {
            mAudio.playSound(AudioEngine.Sound.CLICK);
        }

        mURL.getText().clear();
    };

    private TextWatcher mURLTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            String aURL = mURL.getText().toString();
            showVoiceSearch(isEmptyUrl(aURL));
            showContextButtons(isEmptyUrl(aURL) && mIsContextButtonsEnabled);
            updateHintFading();
        }

        @Override
        public void afterTextChanged(Editable editable) {
            if (mDelegate != null) {
                mDelegate.onShowSearchPopup();
            }
            hideSelectionMenu();
        }
    };

    private class UrlGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDoubleTap(MotionEvent event) {
            return true;
        }
    }

    GestureDetector.OnDoubleTapListener mUrlDoubleTapListener = new GestureDetector.OnDoubleTapListener() {
        @Override
        public boolean onSingleTapConfirmed(MotionEvent motionEvent) {
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent motionEvent) {
            return false;
        }

        @Override
        public boolean onDoubleTapEvent(MotionEvent motionEvent) {
            mURL.selectAll();
            return true;
        }
    };

    private void handleLongPress() {
        ArrayList<String> actions = new ArrayList<>();
        if (mURL.getSelectionEnd() > mURL.getSelectionStart()) {
            actions.add(GeckoSession.SelectionActionDelegate.ACTION_CUT);
            actions.add(GeckoSession.SelectionActionDelegate.ACTION_COPY);
        }
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard.hasPrimaryClip()) {
            actions.add(GeckoSession.SelectionActionDelegate.ACTION_PASTE);
        }
        if (!StringUtils.isEmpty(mURL.getText().toString()) &&
                (mURL.getSelectionStart() != 0 || mURL.getSelectionEnd() != mURL.getText().toString().length())) {
            actions.add(GeckoSession.SelectionActionDelegate.ACTION_SELECT_ALL);
        }

        if (actions.size() == 0) {
            hideSelectionMenu();
            return;
        }

        String[] actionsArray = actions.toArray(new String[0]);
        if (mSelectionMenu != null && !mSelectionMenu.hasSameActions(actionsArray)) {
            // Release current selection menu to recreate it with different actions.
            hideSelectionMenu();
        }

        if (mSelectionMenu == null) {
            mSelectionMenu = new SelectionActionWidget(getContext());
            mSelectionMenu.setActions(actionsArray);
            mSelectionMenu.setDelegate(new SelectionActionWidget.Delegate() {
                @Override
                public void onAction(String action) {
                    int startSelection = mURL.getSelectionStart();
                    int endSelection = mURL.getSelectionEnd();
                    boolean selectionValid = endSelection > startSelection;

                    if (action.equals(GeckoSession.SelectionActionDelegate.ACTION_CUT) && selectionValid) {
                        String selectedText = mURL.getText().toString().substring(startSelection, endSelection);
                        clipboard.setPrimaryClip(ClipData.newPlainText("text", selectedText));
                        mURL.setText(StringUtils.removeRange(mURL.getText().toString(), startSelection, endSelection));
                    } else if (action.equals(GeckoSession.SelectionActionDelegate.ACTION_COPY) && selectionValid) {
                        String selectedText = mURL.getText().toString().substring(startSelection, endSelection);
                        clipboard.setPrimaryClip(ClipData.newPlainText("text", selectedText));
                    } else if (action.equals(GeckoSession.SelectionActionDelegate.ACTION_PASTE) && clipboard.hasPrimaryClip()) {
                        ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                        if (selectionValid) {
                            mURL.setText(StringUtils.removeRange(mURL.getText().toString(), startSelection, endSelection));
                        }
                        if (item != null && item.getText() != null) {
                            mURL.getText().insert(mURL.getSelectionStart(), item.getText());
                        } else if (item != null && item.getUri() != null) {
                            mURL.getText().insert(mURL.getSelectionStart(), item.getUri().toString());
                        }
                    } else if (action.equals(GeckoSession.SelectionActionDelegate.ACTION_SELECT_ALL)) {
                        mURL.selectAll();
                        handleLongPress();
                        return;

                    }
                    hideSelectionMenu();
                }

                @Override
                public void onDismiss() {
                    hideSelectionMenu();
                }
            });
        }

        if (mDelegate != null) {
            mDelegate.onLongPress(getSelectionCenterX(), mSelectionMenu);
        }

        mSelectionMenu.show(UIWidget.KEEP_FOCUS);
    }


    private float getSelectionCenterX() {
        float start = 0;
        if (mURL.getSelectionStart() >= 0) {
            start = ViewUtils.GetLetterPositionX(mURL, mURL.getSelectionStart(), true);
        }
        float end = start;
        if (mURL.getSelectionEnd() > mURL.getSelectionStart()) {
            end = ViewUtils.GetLetterPositionX(mURL, mURL.getSelectionEnd(), true);
        }
        if (end < start) {
            end = start;
        }
        return start + (end - start) * 0.5f;
    }

    private void hideSelectionMenu() {
        if (mSelectionMenu != null) {
            mSelectionMenu.setDelegate((SelectionActionWidget.Delegate) null);
            mSelectionMenu.hide(UIWidget.REMOVE_WIDGET);
            mSelectionMenu.releaseWidget();
            mSelectionMenu = null;
        }
    }

}
