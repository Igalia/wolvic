// Copyright 2024 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package com.igalia.wolvic.browser.api.impl;

import android.content.Context;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.api.WSession;

import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.browser.GestureListenerManager;
import org.chromium.content_public.browser.SelectionPopupController;
import org.chromium.ui.base.Clipboard;
import org.chromium.ui.touch_selection.SelectionEventType;

import java.util.ArrayList;
import java.util.Collection;

public class SelectionPopupControllerDelegate implements SelectionPopupController.Delegate,
        WSession.SelectionActionDelegate.Selection {
    static final String TAG = "SelectionPopupControllerDelegate";

    // Selection rectangle in DIP.
    private final RectF mSelectionRect = new RectF();

    private boolean mEditable;
    private boolean mIsPasswordType;
    private boolean mCanSelectAll;
    private boolean mCanEditRichly;

    // Tracks whether a touch selection is currently active.
    private boolean mHasSelection;

    // If we are currently processing a Select All request from the menu. Used to
    // dismiss the old menu so that it won't be preserved and redrawn at a new anchor.
    private boolean mIsProcessingSelectAll;

    private boolean mIsPastePopupShowing;
    private boolean mWasPastePopupShowingOnInsertionDragStart;

    private String mLastSelectedText;

    private WebContents mWebContents;
    private final SelectionPopupController.DelegateEventHandler mHandler;
    private @NonNull SessionImpl mSession;

    public SelectionPopupControllerDelegate(WebContents webContents,
                                            SelectionPopupController.DelegateEventHandler handler,
                                            @NonNull SessionImpl session) {
        mWebContents = webContents;
        mSession = session;
        mHandler = handler;
        mLastSelectedText = "";
    }

    @Override
    public int flags() {
        return isFocusedNodeEditable() ?
                WSession.SelectionActionDelegate.FLAG_IS_EDITABLE :
                WSession.SelectionActionDelegate.FLAG_IS_COLLAPSED;
    }

    @NonNull
    @Override
    public String text() {
        return mLastSelectedText;
    }

    @Nullable
    @Override
    public RectF clientRect() {
        Context context = mWebContents.getTopLevelNativeWindow().getContext().get();
        float density = SettingsStore.getInstance(context).getDisplayDensity();
        return new RectF(mSelectionRect.left * density, mSelectionRect.top * density,
                mSelectionRect.right * density, mSelectionRect.bottom * density);
    }

    @NonNull
    @Override
    public Collection<String> availableActions() {
        Collection<String> sessionSelectionActions = new ArrayList<String>();

        sessionSelectionActions.add(WSession.SelectionActionDelegate.ACTION_HIDE);
        if (canSelectAll()) {
            sessionSelectionActions.add(WSession.SelectionActionDelegate.ACTION_SELECT_ALL);
        }

        if (hasSelection()) {
            // Action mode
            assert !mIsPastePopupShowing;
            sessionSelectionActions.add(WSession.SelectionActionDelegate.ACTION_UNSELECT);

            if (canCut()) {
                sessionSelectionActions.add(WSession.SelectionActionDelegate.ACTION_CUT);
            }
            if (canCopy()) {
                sessionSelectionActions.add(WSession.SelectionActionDelegate.ACTION_COPY);
            }
            if (canPaste()) {
                sessionSelectionActions.add(WSession.SelectionActionDelegate.ACTION_PASTE);
            }
            if (canPasteAsPlainText()) {
                sessionSelectionActions.add(WSession.SelectionActionDelegate.ACTION_PASTE_AS_PLAIN_TEXT);
            }
            if (canDelete()) {
                sessionSelectionActions.add(WSession.SelectionActionDelegate.ACTION_DELETE);
            }
        } else {
            // Paste mode
            assert mIsPastePopupShowing;
            if (canPaste()) {
                sessionSelectionActions.add(WSession.SelectionActionDelegate.ACTION_PASTE);
            }
            if (canPasteAsPlainText()) {
                sessionSelectionActions.add(WSession.SelectionActionDelegate.ACTION_PASTE_AS_PLAIN_TEXT);
            }
        }


        return sessionSelectionActions;
    }

    @Override
    public boolean isActionAvailable(@NonNull String action) {
        switch (action) {
            case WSession.SelectionActionDelegate.ACTION_HIDE:
                return true;
            case WSession.SelectionActionDelegate.ACTION_CUT:
                return canCut();
            case WSession.SelectionActionDelegate.ACTION_COPY:
                return canCopy();
            case WSession.SelectionActionDelegate.ACTION_DELETE:
                return canDelete();
            case WSession.SelectionActionDelegate.ACTION_PASTE:
                return canPaste();
            case WSession.SelectionActionDelegate.ACTION_PASTE_AS_PLAIN_TEXT:
                return canPasteAsPlainText();
            case WSession.SelectionActionDelegate.ACTION_SELECT_ALL:
                return canSelectAll();
            case WSession.SelectionActionDelegate.ACTION_UNSELECT:
                // This action will be handled by chromium side.
                return false;
            case WSession.SelectionActionDelegate.ACTION_COLLAPSE_TO_START:
            case WSession.SelectionActionDelegate.ACTION_COLLAPSE_TO_END:
                // not implemented
                return false;
            default:
                Log.w(TAG, "Unhandled action: " + action);
        }
        return false;
    }


    @Override
    public void execute(@NonNull String action) {
        if (isActionAvailable(action)) {
            if (action == WSession.SelectionActionDelegate.ACTION_UNSELECT) {
                // Make sure to do this action inside Chromium since it affects the selection
                // behavior in the renderer.
                return;
            } else if (action == WSession.SelectionActionDelegate.ACTION_SELECT_ALL) {
                mIsProcessingSelectAll = true;
            }
            mHandler.onExecute(toChromium(action));
        }
    }

    private @SelectionPopupController.ActionType int toChromium(@NonNull String action) {
        switch (action) {
            case WSession.SelectionActionDelegate.ACTION_HIDE:
                return SelectionPopupController.ActionType.HIDE;
            case WSession.SelectionActionDelegate.ACTION_CUT:
                return SelectionPopupController.ActionType.CUT;
            case WSession.SelectionActionDelegate.ACTION_COPY:
                return SelectionPopupController.ActionType.COPY;
            case WSession.SelectionActionDelegate.ACTION_DELETE:
                return SelectionPopupController.ActionType.DELETE;
            case WSession.SelectionActionDelegate.ACTION_PASTE:
                return SelectionPopupController.ActionType.PASTE;
            case WSession.SelectionActionDelegate.ACTION_PASTE_AS_PLAIN_TEXT:
                return SelectionPopupController.ActionType.PASTE_AS_PLAIN_TEXT;
            case WSession.SelectionActionDelegate.ACTION_SELECT_ALL:
                return SelectionPopupController.ActionType.SELECT_ALL;
            case WSession.SelectionActionDelegate.ACTION_UNSELECT:
                return SelectionPopupController.ActionType.UNSELECT;
            case WSession.SelectionActionDelegate.ACTION_COLLAPSE_TO_START:
                return SelectionPopupController.ActionType.COLLAPSE_TO_START;
            case WSession.SelectionActionDelegate.ACTION_COLLAPSE_TO_END:
                return SelectionPopupController.ActionType.COLLAPSE_TO_END;
            default:
                Log.w(TAG, "Unhandled action: " + action);
        }
        return SelectionPopupController.ActionType.HIDE;
    }

    private void showSelectionMenuInternal() {
        mIsPastePopupShowing = !hasSelection();
        WSession.SelectionActionDelegate delegate= mSession.getSelectionActionDelegate();
        if (delegate != null) {
            delegate.onShowActionRequest(mSession, this);
        }
    }

    private void hideSelectionMenuInternal(@WSession.SelectionActionDelegateHideReason int reason) {
        mIsPastePopupShowing = false;
        WSession.SelectionActionDelegate delegate= mSession.getSelectionActionDelegate();
        if (delegate != null) {
            delegate.onHideAction(mSession, reason);
        }
    }

    private void showPastePopup() {
        showSelectionMenuInternal();
    }

    public void destroyPastePopup() {
        if (isPastePopupShowing()) {
            hideSelectionMenuInternal(WSession.SelectionActionDelegate.HIDE_REASON_ACTIVE_SELECTION);
        }
    }

    @Override
    public void showSelectionMenu(int left, int top, int right, int bottom,
                                  int handleHeight, boolean isEditable, boolean isPasswordType,
                                  String selectionText,  int selectionStartOffset,
                                  boolean canSelectAll, boolean canRichlyEdit) {
        mSelectionRect.set(left, top, right, bottom);
        mEditable = isEditable;
        mLastSelectedText = selectionText;
        mCanSelectAll = canSelectAll;
        mHasSelection = selectionText.length() != 0;
        mIsPasswordType = isPasswordType;
        mCanEditRichly = canRichlyEdit;

        showSelectionMenuInternal();
    }

    public boolean hasSelection() {
        return mHasSelection;
    }

    private boolean canCopy() {
        return hasSelection() && !isSelectionPassword() && Clipboard.getInstance().canCopy();
    }

    private boolean canCut() {
        return hasSelection() && isFocusedNodeEditable() && !isSelectionPassword()
                && Clipboard.getInstance().canCopy();
    }

    private boolean canDelete() {
        return hasSelection() && isFocusedNodeEditable();
    }

    private boolean canPaste() {
        return isFocusedNodeEditable() && Clipboard.getInstance().canPaste();
    }

    public boolean canSelectAll() {
        return mCanSelectAll;
    }

    /**
     * Check if there is a need to show "paste as plain text" option.
     * "paste as plain text" option needs clipboard content is rich text, and editor supports rich
     * text as well.
     */
    private boolean canPasteAsPlainText() {
        if (!canPaste()) return false;

        // String resource "paste_as_plain_text" only exist in O+.
        // Also this is an O feature, we need to make it consistent with TextView.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false;

        if (!mCanEditRichly) return false;

        // We need to show "paste as plain text" when Clipboard contains the HTML text. In addition
        // to that, on Android, Spanned could be copied to Clipboard as plain_text MIME type, but in
        // some cases, Spanned could have text format, we need to show "paste as plain text" when
        // that happens as well.
        return Clipboard.getInstance().hasHTMLOrStyledText();
    }

    private boolean isSelectionPassword() {
        return mIsPasswordType;
    }

    public boolean isFocusedNodeEditable() {
        return mEditable;
    }

    public boolean isPastePopupShowing() {
        return mIsPastePopupShowing;
    }

    public void restoreSelectionPopupsIfNecessary() {
        if (hasSelection() && !isPastePopupShowing()) {
            showSelectionMenuInternal();
        }
    }

    @Override
    public void onSelectionEvent(
            @SelectionEventType int eventType, int left, int top, int right, int bottom) {
        // Ensure the provided selection coordinates form a non-empty rect, as required by
        // the selection action mode.
        // NOTE: the native side ensures the rectangle is not empty, but that's done using floating
        // point, which means it's entirely possible for this code to receive an empty rect.
        if (left == right) ++right;
        if (top == bottom) ++bottom;

        switch (eventType) {
            case SelectionEventType.SELECTION_HANDLES_SHOWN:
            case SelectionEventType.INSERTION_HANDLE_SHOWN:
                mSelectionRect.set(left, top, right, bottom);
                break;

            case SelectionEventType.SELECTION_HANDLES_MOVED:
                mSelectionRect.set(left, top, right, bottom);

                // Hide & Show again
                hideSelectionMenuInternal(WSession.SelectionActionDelegate.HIDE_REASON_ACTIVE_SELECTION);
                showSelectionMenuInternal();
                break;

            case SelectionEventType.SELECTION_HANDLES_CLEARED:
                mLastSelectedText = "";
                mHasSelection = false;
                mSelectionRect.setEmpty();

                hideSelectionMenuInternal(WSession.SelectionActionDelegate.HIDE_REASON_NO_SELECTION);
                break;

            case SelectionEventType.SELECTION_HANDLE_DRAG_STARTED:
                hideSelectionMenuInternal(WSession.SelectionActionDelegate.HIDE_REASON_ACTIVE_SCROLL);
                break;

            case SelectionEventType.SELECTION_HANDLE_DRAG_STOPPED:
                showContextMenuAtTouchHandle(left, bottom);
                break;

            case SelectionEventType.INSERTION_HANDLE_MOVED:
                mSelectionRect.set(left, top, right, bottom);
                if (!getGestureListenerManager().isScrollInProgress() && isPastePopupShowing()) {
                    showPastePopup();
                } else {
                    destroyPastePopup();
                }
                break;

            case SelectionEventType.INSERTION_HANDLE_TAPPED:
                if (mWasPastePopupShowingOnInsertionDragStart) {
                    destroyPastePopup();
                } else {
                    showContextMenuAtTouchHandle((int)mSelectionRect.left, (int)mSelectionRect.bottom);
                }
                mWasPastePopupShowingOnInsertionDragStart = false;
                break;

            case SelectionEventType.INSERTION_HANDLE_CLEARED:
                destroyPastePopup();
                if (!hasSelection()) mSelectionRect.setEmpty();
                break;

            case SelectionEventType.INSERTION_HANDLE_DRAG_STARTED:
                mWasPastePopupShowingOnInsertionDragStart = isPastePopupShowing();
                destroyPastePopup();
                break;

            case SelectionEventType.INSERTION_HANDLE_DRAG_STOPPED:
                if (mWasPastePopupShowingOnInsertionDragStart) {
                    showContextMenuAtTouchHandle((int)mSelectionRect.left, (int)mSelectionRect.bottom);
                }
                mWasPastePopupShowingOnInsertionDragStart = false;
                break;

            default:
                assert false : "Invalid selection event type.";
        }
    }

    private GestureListenerManager getGestureListenerManager() {
        return GestureListenerManager.fromWebContents(mWebContents);
    }

    @Override
    public void onSelectionChanged(String text) {
        final boolean unSelected = TextUtils.isEmpty(text) && hasSelection();
        if (unSelected || mIsProcessingSelectAll) {
            hideSelectionMenuInternal(WSession.SelectionActionDelegate.HIDE_REASON_ACTIVE_SELECTION);
        }

        mLastSelectedText = text;
        mIsProcessingSelectAll = false;
    }

    public void hidePopupsAndPreserveSelection() {
        hideSelectionMenuInternal(WSession.SelectionActionDelegate.HIDE_REASON_ACTIVE_SELECTION);
    }

    public void nativeSelectionPopupControllerDestroyed() {
        hideSelectionMenuInternal(WSession.SelectionActionDelegate.HIDE_REASON_NO_SELECTION);
    }

    private void showContextMenuAtTouchHandle(int left, int bottom) {
        mHandler.onExecute(SelectionPopupController.ActionType.SHOW_CONTEXT_MENU, left, bottom);
    }
}
