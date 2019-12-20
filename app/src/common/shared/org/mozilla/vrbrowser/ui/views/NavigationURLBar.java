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
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.databinding.DataBindingUtil;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.BookmarksStore;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.NavigationUrlBinding;
import org.mozilla.vrbrowser.search.SearchEngineWrapper;
import org.mozilla.vrbrowser.telemetry.GleanMetricsService;
import org.mozilla.vrbrowser.telemetry.TelemetryWrapper;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.dialogs.SelectionActionWidget;
import org.mozilla.vrbrowser.utils.StringUtils;
import org.mozilla.vrbrowser.utils.SystemUtils;
import org.mozilla.vrbrowser.utils.UrlUtils;
import org.mozilla.vrbrowser.utils.ViewUtils;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.Executor;

import kotlin.Unit;
import mozilla.components.browser.domains.autocomplete.DomainAutocompleteResult;
import mozilla.components.browser.domains.autocomplete.ShippedDomainsProvider;
import mozilla.components.ui.autocomplete.InlineAutocompleteEditText;

public class NavigationURLBar extends FrameLayout {

    private static final String LOGTAG = SystemUtils.createLogtag(NavigationURLBar.class);

    private NavigationUrlBinding mBinding;
    private Animation mLoadingAnimation;
    private int mURLProtocolColor;
    private int mURLWebsiteColor;
    private NavigationURLBarDelegate mDelegate;
    private ShippedDomainsProvider mAutocompleteProvider;
    private AudioEngine mAudio;
    private Executor mUIThreadExecutor;
    private Session mSession;
    private SelectionActionWidget mSelectionMenu;
    private boolean mWasFocusedWhenTouchBegan = false;
    private boolean mLongPressed = false;
    private int lastTouchDownOffset = 0;

    private Unit domainAutocompleteFilter(String text) {
        if (mBinding.urlEditText != null) {
            DomainAutocompleteResult result = mAutocompleteProvider.getAutocompleteSuggestion(text);
            if (result != null) {
                mBinding.urlEditText.applyAutocompleteResult(new InlineAutocompleteEditText.AutocompleteResult(
                        result.getText(),
                        result.getSource(),
                        result.getTotalItems(),
                        null));
            } else {
                mBinding.urlEditText.noAutocompleteResult();
            }
        }
        return Unit.INSTANCE;
    }

    public interface NavigationURLBarDelegate {
        void onVoiceSearchClicked();
        void onShowAwesomeBar();
        void onHideAwesomeBar();
        void onURLSelectionAction(EditText aURLEdit, float centerX, SelectionActionWidget actionMenu);
        void onPopUpButtonClicked();
    }

    public NavigationURLBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initialize(Context aContext) {
        mAudio = AudioEngine.fromContext(aContext);

        mUIThreadExecutor = ((VRBrowserApplication)getContext().getApplicationContext()).getExecutors().mainThread();

        mSession = SessionStore.get().getActiveSession();

        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.navigation_url, this, true);

        // Use Domain autocomplete provider from components
        mAutocompleteProvider = new ShippedDomainsProvider();
        mAutocompleteProvider.initialize(aContext);

        mBinding.urlEditText.clearFocus();
        mBinding.urlEditText.setShowSoftInputOnFocus(false);
        mBinding.urlEditText.setOnEditorActionListener((aTextView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEND) {
                handleURLEdit(aTextView.getText().toString());
                return true;
            }
            return false;
        });

        mBinding.urlEditText.setOnFocusChangeListener((view, focused) -> {
            boolean isUrlEmpty = mBinding.urlEditText.getText().length() == 0;
            setMicrophoneEnabled(!focused || isUrlEmpty);
            mBinding.setIsFocused(focused);
            mBinding.setIsUrlEmpty(isUrlEmpty);
            if (!focused) {
                hideSelectionMenu();
            } else {
                mBinding.urlEditText.selectAll();
            }
        });

        final GestureDetector gd = new GestureDetector(getContext(), new UrlGestureListener());
        gd.setOnDoubleTapListener(mUrlDoubleTapListener);
        mBinding.urlEditText.setOnTouchListener((view, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                mWasFocusedWhenTouchBegan = view.isFocused();
                lastTouchDownOffset = ViewUtils.getCursorOffset(mBinding.urlEditText, motionEvent.getX());

            } else if (mLongPressed && motionEvent.getAction() == MotionEvent.ACTION_MOVE) {
                // Selection gesture while long pressing
                ViewUtils.placeSelection(mBinding.urlEditText, lastTouchDownOffset, ViewUtils.getCursorOffset(mBinding.urlEditText, motionEvent.getX()));

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

        mBinding.urlEditText.setOnClickListener(v -> {
            if (mWasFocusedWhenTouchBegan) {
                hideSelectionMenu();
            }
        });

        mBinding.urlEditText.setOnLongClickListener(v -> {
            if (!v.isFocused()) {
                mBinding.urlEditText.requestFocus();
                mBinding.urlEditText.selectAll();

            } else if (!mBinding.urlEditText.hasSelection()) {
                // Place the cursor in the long pressed position.
                if (lastTouchDownOffset >= 0) {
                    mBinding.urlEditText.setSelection(lastTouchDownOffset);
                }
                mLongPressed = true;
            }

            // Add some delay so selection ranges are ready
            postDelayed(this::showSelectionMenu, 10);
            return true;
        });

        mBinding.urlEditText.addTextChangedListener(mURLTextWatcher);

        mBinding.urlEditText.setOnSelectionChangedCallback((start, end) -> {
            if (mSelectionMenu != null) {
                boolean hasCopy = mSelectionMenu.hasAction(GeckoSession.SelectionActionDelegate.ACTION_COPY);
                boolean showCopy = end > start;
                if (hasCopy != showCopy) {
                    showSelectionMenu();

                } else {
                    mDelegate.onURLSelectionAction(mBinding.urlEditText, getSelectionCenterX(), mSelectionMenu);
                    mSelectionMenu.updateWidget();
                }
            }
        });

        mBinding.urlEditText.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (mLongPressed) {
                hideSelectionMenu();
            }
        });

        // Set a filter to provide domain autocomplete results
        mBinding.urlEditText.setOnFilterListener(this::domainAutocompleteFilter);

        mBinding.microphoneButton.setTag(R.string.view_id_tag, R.id.microphoneButton);
        mBinding.microphoneButton.setOnClickListener(mMicrophoneListener);

        mBinding.clearButton.setTag(R.string.view_id_tag, R.id.clearButton);
        mBinding.clearButton.setOnClickListener(mClearListener);

        mBinding.popup.setOnClickListener(mPopUpListener);

        mLoadingAnimation = AnimationUtils.loadAnimation(aContext, R.anim.loading);

        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = aContext.getTheme();
        theme.resolveAttribute(R.attr.urlProtocolColor, typedValue, true);
        mURLProtocolColor = typedValue.data;
        theme.resolveAttribute(R.attr.urlWebsiteColor, typedValue, true);
        mURLWebsiteColor = typedValue.data;

        // Bookmarks
        mBinding.bookmarkButton.setOnClickListener(v -> {
            v.requestFocusFromTouch();
            handleBookmarkClick();
        });

        // Initialize bindings
        mBinding.setIsLibraryVisible(false);
        mBinding.setIsLoading(false);
        mBinding.setIsInsecure(false);
        mBinding.setIsMicrophoneEnabled(true);
        mBinding.setIsFocused(false);
        mBinding.setIsSpecialUrl(false);
        mBinding.setIsUrlEmpty(true);
        mBinding.setIsPopUpAvailable(false);
        mBinding.executePendingBindings();

        clearFocus();
    }

    public void setSession(Session session) {
        mSession = session;
    }

    public void onPause() {
        if (mBinding.getIsLoading()) {
            mBinding.loadingView.clearAnimation();
        }
    }

    public void onResume() {
        if (mBinding.getIsLoading()) {
            mBinding.loadingView.startAnimation(mLoadingAnimation);
        }
    }

    public void setDelegate(NavigationURLBarDelegate delegate) {
        mDelegate = delegate;
    }

    private void handleBookmarkClick() {
        if (mAudio != null) {
            mAudio.playSound(AudioEngine.Sound.CLICK);
        }

        String url = mSession.getCurrentUri();
        if (StringUtils.isEmpty(url)) {
            return;
        }
        BookmarksStore bookmarkStore = SessionStore.get().getBookmarkStore();
        bookmarkStore.isBookmarked(url).thenAcceptAsync(bookmarked -> {
            if (!bookmarked) {
                bookmarkStore.addBookmark(url, mSession.getCurrentTitle());
                setIsBookmarked(true);
            } else {
                // Delete
                bookmarkStore.deleteBookmarkByURL(url);
                setIsBookmarked(false);
            }
        }, mUIThreadExecutor).exceptionally(throwable -> {
            Log.d(LOGTAG, "Error checking bookmark: " + throwable.getLocalizedMessage());
            throwable.printStackTrace();
            return null;
        });

    }

    public void setHint(@StringRes int aHint) {
        mBinding.urlEditText.setHint(aHint);
    }

    public void setURL(String aURL) {
        if (mBinding.getIsLibraryVisible()) {
            return;
        }
        mBinding.urlEditText.removeTextChangedListener(mURLTextWatcher);
        if (StringUtils.isEmpty(aURL)) {
            setIsBookmarked(false);
        } else {
            SessionStore.get().getBookmarkStore().isBookmarked(aURL).thenAcceptAsync(this::setIsBookmarked, mUIThreadExecutor).exceptionally(throwable -> {
                Log.d(LOGTAG, "Error getting the bookmarked status: " + throwable.getLocalizedMessage());
                throwable.printStackTrace();
                return null;
            });
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

            } else if (aURL.startsWith("resource:") || mSession.isHomeUri(aURL)) {
                aURL = "";

            } else if (aURL.startsWith("data:") && mSession.isPrivateMode()) {
                aURL = "";

            } else if (aURL.startsWith(getContext().getString(R.string.about_blank))) {
                aURL = "";

            } else {
                index = aURL.indexOf("://");
            }

            // Update the URL bar only if the URL is different than the current one and
            // the URL bar is not focused to avoid override user input
            if (!mBinding.urlEditText.getText().toString().equalsIgnoreCase(aURL) && !mBinding.urlEditText.isFocused()) {
                mBinding.urlEditText.setText(aURL);
                if (index > 0) {
                    SpannableString spannable = new SpannableString(aURL);
                    ForegroundColorSpan color1 = new ForegroundColorSpan(mURLProtocolColor);
                    ForegroundColorSpan color2 = new ForegroundColorSpan(mURLWebsiteColor);
                    spannable.setSpan(color1, 0, index + 3, 0);
                    spannable.setSpan(color2, index + 3, aURL.length(), 0);
                    mBinding.urlEditText.setText(spannable);

                } else {
                    mBinding.urlEditText.setText(aURL);
                }
            }

            mBinding.setIsSpecialUrl(aURL.isEmpty());
        }

        mBinding.urlEditText.addTextChangedListener(mURLTextWatcher);
    }

    private boolean isEmptyUrl(@NonNull String aURL) {
        return aURL.length() == 0 || aURL.startsWith("about://");
    }

    public String getText() {
        return mBinding.urlEditText.getText().toString();
    }

    public String getOriginalText() {
        try {
            return mBinding.urlEditText.getOriginalText();

        } catch (IndexOutOfBoundsException e) {
            return mBinding.urlEditText.getNonAutocompleteText();
        }
    }

    public void setIsLibraryVisible(boolean isLibraryVisible) {
        mBinding.setIsLibraryVisible(isLibraryVisible);
    }

    public void setIsInsecure(boolean aIsInsecure) {
        mBinding.setIsInsecure(aIsInsecure);
    }

    public void setIsLoading(boolean aIsLoading) {
        mBinding.setIsLoading(aIsLoading);
        if (aIsLoading) {
            mBinding.loadingView.startAnimation(mLoadingAnimation);
        } else {
            mBinding.loadingView.clearAnimation();
        }
    }

    public void setMicrophoneEnabled(boolean enabled) {
        mBinding.setIsMicrophoneEnabled(enabled);
    }

    private void setIsBookmarked(boolean aValue) {
        mBinding.setIsBookmarked(aValue);
        mBinding.bookmarkButton.clearFocus();
    }

    public void setPrivateMode(boolean isEnabled) {
        mBinding.bookmarkButton.setPrivateMode(isEnabled);
        mBinding.microphoneButton.setPrivateMode(isEnabled);
        mBinding.clearButton.setPrivateMode(isEnabled);

        mBinding.setIsPrivateMode(isEnabled);
    }

    public void setIsPopUpAvailable(boolean isAvailable) {
        mBinding.setIsPopUpAvailable(isAvailable);
    }

    public UIButton getPopUpButton() {
        return mBinding.popup;
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
            GleanMetricsService.urlBarEvent(true);
        } else if (text.startsWith("about:") || text.startsWith("resource://")) {
            url = text;
        } else {
            url = SearchEngineWrapper.get(getContext()).getSearchURL(text);

            // Doing search in the URL bar, so sending "aIsURL: false" to telemetry.
            TelemetryWrapper.urlBarEvent(false);
            GleanMetricsService.urlBarEvent(false);
        }

        if (mSession.getCurrentUri() != url) {
            mSession.loadUri(url);

            if (mDelegate != null) {
                mDelegate.onHideAwesomeBar();
            }
        }

        setMicrophoneEnabled(!text.isEmpty());
        clearFocus();
    }

    @Override
    public void setClickable(boolean clickable) {
        super.setClickable(clickable);
        mBinding.urlEditText.setEnabled(clickable);
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
        GleanMetricsService.voiceInputEvent();
    };

    private OnClickListener mClearListener = view -> {
        if (mAudio != null) {
            mAudio.playSound(AudioEngine.Sound.CLICK);
        }

        mBinding.urlEditText.getText().clear();
    };

    private OnClickListener mPopUpListener = view -> {
        if (mAudio != null) {
            mAudio.playSound(AudioEngine.Sound.CLICK);
        }

        view.requestFocusFromTouch();
        if (mDelegate != null) {
            mDelegate.onPopUpButtonClicked();
        }
    };

    private TextWatcher mURLTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            String aURL = mBinding.urlEditText.getText().toString();
            boolean empty = isEmptyUrl(aURL);
            mBinding.setIsUrlEmpty(empty);
            setMicrophoneEnabled(empty);
        }

        @Override
        public void afterTextChanged(Editable editable) {
            if (mDelegate != null && mBinding.urlEditText.isFocused()) {
                mDelegate.onShowAwesomeBar();
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
            mBinding.urlEditText.selectAll();
            showSelectionMenu();
            return true;
        }
    };

    private void showSelectionMenu() {
        Collection<String> actions = new HashSet<>();
        if (mBinding.urlEditText.getSelectionEnd() > mBinding.urlEditText.getSelectionStart()) {
            actions.add(GeckoSession.SelectionActionDelegate.ACTION_CUT);
            actions.add(GeckoSession.SelectionActionDelegate.ACTION_COPY);
        }
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard.hasPrimaryClip()) {
            actions.add(GeckoSession.SelectionActionDelegate.ACTION_PASTE);
        }
        if (!StringUtils.isEmpty(mBinding.urlEditText.getText().toString()) &&
                (mBinding.urlEditText.getSelectionStart() != 0 || mBinding.urlEditText.getSelectionEnd() != mBinding.urlEditText.getText().toString().length())) {
            actions.add(GeckoSession.SelectionActionDelegate.ACTION_SELECT_ALL);
        }

        if (actions.size() == 0) {
            hideSelectionMenu();
            return;
        }

        if (mSelectionMenu != null && !mSelectionMenu.hasSameActions(actions)) {
            // Release current selection menu to recreate it with different actions.
            hideSelectionMenu();
        }

        if (mSelectionMenu == null) {
            mSelectionMenu = new SelectionActionWidget(getContext());
            mSelectionMenu.setActions(actions);
            mSelectionMenu.setDelegate(new SelectionActionWidget.Delegate() {
                @Override
                public void onAction(String action) {
                    int startSelection = mBinding.urlEditText.getSelectionStart();
                    int endSelection = mBinding.urlEditText.getSelectionEnd();
                    boolean selectionValid = endSelection > startSelection;

                    if (action.equals(GeckoSession.SelectionActionDelegate.ACTION_CUT) && selectionValid) {
                        String selectedText = mBinding.urlEditText.getText().toString().substring(startSelection, endSelection);
                        clipboard.setPrimaryClip(ClipData.newPlainText("text", selectedText));
                        mBinding.urlEditText.setText(StringUtils.removeRange(mBinding.urlEditText.getText().toString(), startSelection, endSelection));
                    } else if (action.equals(GeckoSession.SelectionActionDelegate.ACTION_COPY) && selectionValid) {
                        String selectedText = mBinding.urlEditText.getText().toString().substring(startSelection, endSelection);
                        clipboard.setPrimaryClip(ClipData.newPlainText("text", selectedText));
                        mBinding.urlEditText.setSelection(endSelection);
                    } else if (action.equals(GeckoSession.SelectionActionDelegate.ACTION_PASTE) && clipboard.hasPrimaryClip()) {
                        ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                        if (selectionValid) {
                            mBinding.urlEditText.setText(StringUtils.removeRange(mBinding.urlEditText.getText().toString(), startSelection, endSelection));
                            mBinding.urlEditText.setSelection(startSelection);
                        }
                        if (item != null && item.getText() != null) {
                            mBinding.urlEditText.getText().insert(mBinding.urlEditText.getSelectionStart(), item.getText());
                        } else if (item != null && item.getUri() != null) {
                            mBinding.urlEditText.getText().insert(mBinding.urlEditText.getSelectionStart(), item.getUri().toString());
                        }
                    } else if (action.equals(GeckoSession.SelectionActionDelegate.ACTION_SELECT_ALL)) {
                        mBinding.urlEditText.selectAll();
                        showSelectionMenu();
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
            mDelegate.onURLSelectionAction(mBinding.urlEditText, getSelectionCenterX(), mSelectionMenu);
        }

        mSelectionMenu.show(UIWidget.KEEP_FOCUS);
    }


    private float getSelectionCenterX() {
        float start = 0;
        if (mBinding.urlEditText.getSelectionStart() >= 0) {
            start = ViewUtils.GetLetterPositionX(mBinding.urlEditText, mBinding.urlEditText.getSelectionStart(), true);
        }
        float end = start;
        if (mBinding.urlEditText.getSelectionEnd() > mBinding.urlEditText.getSelectionStart()) {
            end = ViewUtils.GetLetterPositionX(mBinding.urlEditText, mBinding.urlEditText.getSelectionEnd(), true);
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
