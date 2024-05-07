/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.views;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ObservableBoolean;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.VRBrowserApplication;
import com.igalia.wolvic.audio.AudioEngine;
import com.igalia.wolvic.browser.BookmarksStore;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.NavigationUrlBinding;
import com.igalia.wolvic.findinpage.FindInPageInteractor;
import com.igalia.wolvic.telemetry.TelemetryService;
import com.igalia.wolvic.ui.viewmodel.SettingsViewModel;
import com.igalia.wolvic.ui.viewmodel.WindowViewModel;
import com.igalia.wolvic.ui.widgets.UIWidget;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.WindowWidget;
import com.igalia.wolvic.ui.widgets.dialogs.SelectionActionWidget;
import com.igalia.wolvic.utils.StringUtils;
import com.igalia.wolvic.utils.SystemUtils;
import com.igalia.wolvic.utils.UrlUtils;
import com.igalia.wolvic.utils.ViewUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.Executor;

import kotlin.coroutines.Continuation;
import kotlin.Unit;
import kotlin.coroutines.CoroutineContext;
import mozilla.components.concept.toolbar.AutocompleteResult;
import mozilla.components.browser.domains.autocomplete.ShippedDomainsProvider;
import mozilla.components.ui.autocomplete.InlineAutocompleteEditText;

public class NavigationURLBar extends FrameLayout {

    private static final String LOGTAG = SystemUtils.createLogtag(NavigationURLBar.class);

    private FindInPageInteractor mFindInPage;
    private WindowViewModel mViewModel;
    private WidgetManagerDelegate mWidgetManager;
    private Runnable mFindInPageBackHandler;
    private SettingsViewModel mSettingsViewModel;
    private NavigationUrlBinding mBinding;
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
        if (!SettingsStore.getInstance(getContext()).isAutocompleteEnabled()) {
            return Unit.INSTANCE;
        }

        Continuation<? super AutocompleteResult> completion = new Continuation<AutocompleteResult>() {
            @NonNull
            @Override
            public CoroutineContext getContext() {
                return getContext();
            }

            @Override
            public void resumeWith(@NonNull Object o) {

            }
        };
        AutocompleteResult result = (AutocompleteResult) mAutocompleteProvider.getAutocompleteSuggestion(text, completion);
        if (result != null) {
            mBinding.urlEditText.applyAutocompleteResult(new InlineAutocompleteEditText.AutocompleteResult(
                    result.getText(),
                    result.getSource(),
                    result.getTotalItems()));
        } else {
            mBinding.urlEditText.noAutocompleteResult();
        }
        return Unit.INSTANCE;
    }

    public interface NavigationURLBarDelegate {
        void onVoiceSearchClicked();
        void onShowAwesomeBar();
        void onHideAwesomeBar();
        void onURLSelectionAction(EditText aURLEdit, float centerX, SelectionActionWidget actionMenu);
        void onPopUpButtonClicked();
        void onWebXRButtonClicked();
        void onTrackingButtonClicked();
        void onDrmButtonClicked();
        void onWebAppButtonClicked();
        boolean onHandleExternalRequest(@NonNull String uri);
    }

    public NavigationURLBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initialize(Context aContext) {
        mSettingsViewModel = new ViewModelProvider(
                (VRBrowserActivity)getContext(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(((VRBrowserActivity) getContext()).getApplication()))
                .get(SettingsViewModel.class);

        mWidgetManager = (WidgetManagerDelegate) getContext();
        mAudio = AudioEngine.fromContext(aContext);

        mUIThreadExecutor = ((VRBrowserApplication)getContext().getApplicationContext()).getExecutors().mainThread();

        mSession = SessionStore.get().getActiveSession();

        mFindInPageBackHandler = () -> mViewModel.setIsFindInPage(false);

        // Layout setup
        mBinding = DataBindingUtil.inflate(LayoutInflater.from(getContext()), R.layout.navigation_url, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity)getContext());
        mBinding.setSettingsViewmodel(mSettingsViewModel);

        // Use Domain autocomplete provider from components
        mAutocompleteProvider = new ShippedDomainsProvider();
        mAutocompleteProvider.initialize(getContext());

        mBinding.urlEditText.clearFocus();
        mBinding.urlEditText.setShowSoftInputOnFocus(false);
        mBinding.urlEditText.setOnEditorActionListener((aTextView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEND) {
                String textContent = aTextView.getText().toString();
                ((Activity)getContext()).runOnUiThread(() -> {
                    handleURLEdit(textContent);
                });
                return true;
            }
            return false;
        });

        mBinding.urlEditText.setOnFocusChangeListener((view, focused) -> {
            mViewModel.setIsFocused(focused);
            mViewModel.setIsUrlEmpty(mBinding.urlEditText.getText().toString().isEmpty());
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
                boolean hasCopy = mSelectionMenu.hasAction(WSession.SelectionActionDelegate.ACTION_COPY);
                boolean showCopy = end != start;
                if (hasCopy != showCopy) {
                    showSelectionMenu();

                } else {
                    mDelegate.onURLSelectionAction(mBinding.urlEditText, getSelectionCenterX(), mSelectionMenu);
                    mSelectionMenu.updateWidget();
                }
            }
        });

        // Set a filter to provide domain autocomplete results
        mBinding.urlEditText.setOnFilterListener(this::domainAutocompleteFilter);

        mBinding.microphoneButton.setTag(R.string.view_id_tag, R.id.microphoneButton);
        mBinding.microphoneButton.setOnClickListener(mMicrophoneListener);

        mBinding.clearButton.setTag(R.string.view_id_tag, R.id.clearButton);
        mBinding.clearButton.setOnClickListener(mClearListener);

        mBinding.popup.setOnClickListener(mPopUpListener);
        mBinding.webxr.setOnClickListener(mWebXRButtonClick);
        mBinding.tracking.setOnClickListener(mTrackingButtonClick);
        mBinding.drm.setOnClickListener(mDrmButtonClick);

        // Bookmarks
        mBinding.bookmarkButton.setOnClickListener(v -> handleBookmarkClick());

        // Web app
        mBinding.webAppButton.setOnClickListener(mWebAppButtonClick);

        mFindInPage = new FindInPageInteractor(
                mBinding.findInPage,
                () -> {
                    mViewModel.setIsFindInPage(false);
                    return null;
                }
        );
        bindFindInPageSession();

        clearFocus();
    }

    private void bindFindInPageSession() {
        if (mSession == null) { return; }
        WSession.SessionFinder finder = mSession.getWSession().getSessionFinder();
        // FIXME: finder should be NonNull but we haven't implemented it for Chromium yet.
        if (finder == null) { return; };
        mFindInPage.bind(finder);
        mFindInPage.start();
    }

    public void detachFromWindow() {
        if (mViewModel != null) {
            mViewModel.setIsFocused(false);
            mViewModel.getIsBookmarked().removeObserver(mIsBookmarkedObserver);
            mViewModel.getIsFindInPage().removeObserver(mIsFindInPageObserver);
            mViewModel = null;
        }
    }

    public void attachToWindow(@NonNull WindowWidget aWindow) {
        mViewModel = new ViewModelProvider(
                (VRBrowserActivity)getContext(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(((VRBrowserActivity) getContext()).getApplication()))
                .get(String.valueOf(aWindow.hashCode()), WindowViewModel.class);

        mBinding.setViewmodel(mViewModel);

        mViewModel.getIsBookmarked().observe((VRBrowserActivity)getContext(), mIsBookmarkedObserver);
        mViewModel.getIsFindInPage().observe((VRBrowserActivity)getContext(), mIsFindInPageObserver);
    }

    public void setSession(Session session) {
        mFindInPage.stop();
        mFindInPage.unbind();
        mSession = session;
        bindFindInPageSession();
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
                mViewModel.setIsBookmarked(true);

            } else {
                // Delete
                bookmarkStore.deleteBookmarkByURL(url);
                mViewModel.setIsBookmarked(false);
            }
        }, mUIThreadExecutor).exceptionally(throwable -> {
            Log.d(LOGTAG, "Error checking bookmark: " + throwable.getLocalizedMessage());
            throwable.printStackTrace();
            return null;
        });

    }

    private Observer<ObservableBoolean> mIsBookmarkedObserver = aBoolean -> mBinding.bookmarkButton.clearFocus();

    private Observer<ObservableBoolean> mIsFindInPageObserver = aBoolean -> {
        if (aBoolean.get()) {
            mBinding.findInPage.focus();
            mWidgetManager.pushBackHandler(mFindInPageBackHandler);
        } else {
            mBinding.findInPage.clear();
            mWidgetManager.popBackHandler(mFindInPageBackHandler);
        }
    };

    public String getText() {
        return mBinding.urlEditText.getText().toString();
    }

    public String getNonAutocompleteText() {
        return mBinding.urlEditText.getNonAutocompleteText();
    }

    public UIButton getPopUpButton() {
        return mBinding.popup;
    }

    public UIButton getWebXRButton() {
        return mBinding.webxr;
    }

    public UIButton getTrackingButton() {
        return mBinding.tracking;
    }

    public UIButton getDrmButton() {
        return mBinding.drm;
    }

    public  void handleURLEdit(String text) {
        if (!mDelegate.onHandleExternalRequest(text)) {
            String url = UrlUtils.urlForText(getContext(), text.trim(), mSession.getWSession().getUrlUtilsVisitor());
            mViewModel.setUrl(url);
            mSession.loadUri(url);
        }

        if (mDelegate != null) {
            mDelegate.onHideAwesomeBar();
        }

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

        if (mDelegate != null) {
            mDelegate.onVoiceSearchClicked();
        }

        TelemetryService.voiceInputEvent();
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

        if (mDelegate != null) {
            mDelegate.onPopUpButtonClicked();
        }
    };

    private OnClickListener mWebXRButtonClick = view -> {
        if (mAudio != null) {
            mAudio.playSound(AudioEngine.Sound.CLICK);
        }

        if (mDelegate != null) {
            mDelegate.onWebXRButtonClicked();
        }
    };

    private OnClickListener mTrackingButtonClick = view -> {
        if (mAudio != null) {
            mAudio.playSound(AudioEngine.Sound.CLICK);
        }

        if (mDelegate != null) {
            mDelegate.onTrackingButtonClicked();
        }
    };

    private OnClickListener mDrmButtonClick = view -> {
        if (mAudio != null) {
            mAudio.playSound(AudioEngine.Sound.CLICK);
        }

        if (mDelegate != null) {
            mDelegate.onDrmButtonClicked();
        }
    };

    private OnClickListener mWebAppButtonClick = view -> {
        if (mAudio != null) {
            mAudio.playSound(AudioEngine.Sound.CLICK);
        }

        if (mDelegate != null) {
            mDelegate.onWebAppButtonClicked();
        }
    };

    private TextWatcher mURLTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            mViewModel.setIsUrlEmpty(mBinding.urlEditText.getText().toString().isEmpty());
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
        if (mBinding.urlEditText.getSelectionEnd() != mBinding.urlEditText.getSelectionStart()) {
            actions.add(WSession.SelectionActionDelegate.ACTION_CUT);
            actions.add(WSession.SelectionActionDelegate.ACTION_COPY);
        }
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard.hasPrimaryClip()) {
            actions.add(WSession.SelectionActionDelegate.ACTION_PASTE);
        }
        if (!StringUtils.isEmpty(mBinding.urlEditText.getText().toString()) &&
                (mBinding.urlEditText.getSelectionStart() != 0 || mBinding.urlEditText.getSelectionEnd() != mBinding.urlEditText.getText().toString().length())) {
            actions.add(WSession.SelectionActionDelegate.ACTION_SELECT_ALL);
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
                    boolean selectionValid = endSelection != startSelection;
                    if (startSelection > endSelection) {
                        int tmp = endSelection;
                        endSelection = startSelection;
                        startSelection = tmp;
                    }

                    if (action.equals(WSession.SelectionActionDelegate.ACTION_CUT) && selectionValid) {
                        String selectedText = mBinding.urlEditText.getText().toString().substring(startSelection, endSelection);
                        clipboard.setPrimaryClip(ClipData.newPlainText("text", selectedText));
                        mBinding.urlEditText.setText(StringUtils.removeRange(mBinding.urlEditText.getText().toString(), startSelection, endSelection));
                        mBinding.urlEditText.setSelection(startSelection);

                    } else if (action.equals(WSession.SelectionActionDelegate.ACTION_COPY) && selectionValid) {
                        String selectedText = mBinding.urlEditText.getText().toString().substring(startSelection, endSelection);
                        clipboard.setPrimaryClip(ClipData.newPlainText("text", selectedText));
                        mBinding.urlEditText.setSelection(endSelection);
                    } else if (action.equals(WSession.SelectionActionDelegate.ACTION_PASTE) && clipboard.hasPrimaryClip()) {
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
                    } else if (action.equals(WSession.SelectionActionDelegate.ACTION_SELECT_ALL)) {
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
        if (mBinding.urlEditText.getSelectionEnd() >= 0) {
            end = ViewUtils.GetLetterPositionX(mBinding.urlEditText, mBinding.urlEditText.getSelectionEnd(), true);
        }
        if (end < start) {
            float tmp = end;
            end = start;
            start = tmp;
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
