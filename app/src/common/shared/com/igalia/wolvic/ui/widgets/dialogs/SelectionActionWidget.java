package com.igalia.wolvic.ui.widgets.dialogs;

import static android.view.Gravity.CENTER_VERTICAL;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.search.SearchEngineWrapper;
import com.igalia.wolvic.ui.views.UITextButton;
import com.igalia.wolvic.ui.widgets.UIWidget;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.ui.widgets.WindowWidget;
import com.igalia.wolvic.utils.UrlUtils;
import com.igalia.wolvic.utils.ViewUtils;

import java.util.ArrayList;
import java.util.Collection;

public class SelectionActionWidget extends UIWidget implements WidgetManagerDelegate.FocusChangeListener {
    public interface Delegate {
        void onAction(String action);
        void onDismiss();
    }

    public static final String ACTION_WEB_SEARCH = "ACTION_WEB_SEARCH";

    private Delegate mDelegate;
    private Point mPosition;
    private LinearLayout mContainer;
    private int mMinButtonWidth;
    private int mMaxButtonWidth;
    private Collection<String> mActions;
    private String mSelectionText;

    public SelectionActionWidget(Context aContext) {
        super(aContext);
        initialize();
    }

    private void initialize() {
        updateUI();

        mMinButtonWidth = WidgetPlacement.pixelDimension(getContext(), R.dimen.selection_action_item_min_width);
        mMaxButtonWidth = WidgetPlacement.pixelDimension(getContext(), R.dimen.selection_action_item_max_width);
        mBackHandler = this::onDismiss;
    }

    public void updateUI() {
        removeAllViews();

        inflate(getContext(), R.layout.selection_action_menu, this);
        mContainer = findViewById(R.id.selectionMenuContainer);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        updateUI();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.context_menu_row_width);
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.context_menu_row_height);
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.5f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.translationX = 0.0f;
        aPlacement.translationY = 0.0f;
        aPlacement.translationZ = 1.0f;
        aPlacement.visible = false;
    }

    public void setDelegate(Delegate aDelegate) {
        mDelegate = aDelegate;
    }

    @Override
    public void show(@ShowFlags int aShowFlags) {
        mWidgetManager.addFocusChangeListener(this);
        mWidgetPlacement.setSizeFromMeasure(getContext(), this);
        if (mPosition != null) {
            mWidgetPlacement.parentAnchorX = 0.0f;
            mWidgetPlacement.parentAnchorY = 1.0f;
            mWidgetPlacement.translationX = mPosition.x;
            mWidgetPlacement.translationY = -mPosition.y;
            mWidgetPlacement.translationY += mWidgetPlacement.height * 0.5f;
        }
        super.show(aShowFlags);
    }

    @Override
    public void hide(@HideFlags int aHideFlags) {
        super.hide(aHideFlags);
        mWidgetManager.removeFocusChangeListener(this);
    }

    @Override
    protected void onDismiss() {
        if (mDelegate != null) {
            mDelegate.onDismiss();
        }
    }

    public void setSelectionRect(@Nullable RectF aRect) {
        if (aRect != null) {
            mPosition = new Point((int) aRect.centerX(), (int) aRect.top);
        } else {
            mPosition = null;
        }
    }

    public void setSelectionText(String text) {
        mSelectionText = text;
    }

    public void setActions(@NonNull Collection<String> aActions) {
        mActions = aActions;
        mContainer.removeAllViews();
        ArrayList<UITextButton> buttons = new ArrayList<>();

        if (aActions.contains(WSession.SelectionActionDelegate.ACTION_CUT)) {
            buttons.add(createButton(
                    getContext().getString(R.string.context_menu_cut_text),
                    WSession.SelectionActionDelegate.ACTION_CUT, this::handleAction));
        }
        if (aActions.contains(WSession.SelectionActionDelegate.ACTION_COPY)) {
            buttons.add(createButton(
                    getContext().getString(R.string.context_menu_copy_text),
                    WSession.SelectionActionDelegate.ACTION_COPY, this::handleAction));
        }
        if (aActions.contains(WSession.SelectionActionDelegate.ACTION_PASTE)) {
            buttons.add(createButton(
                    getContext().getString(R.string.context_menu_paste_text),
                    WSession.SelectionActionDelegate.ACTION_PASTE, this::handleAction));
        }
        if (aActions.contains(WSession.SelectionActionDelegate.ACTION_SELECT_ALL)) {
            buttons.add(createButton(
                    getContext().getString(R.string.context_menu_select_all_text),
                    WSession.SelectionActionDelegate.ACTION_SELECT_ALL, this::handleAction));
        }

        // Contextual search is disabled in kiosk mode.
        if (!mWidgetManager.getWindows().getFocusedWindow().isKioskMode() &&
                mSelectionText != null && !mSelectionText.trim().isEmpty()) {
            buttons.add(createButton(
                    getContext().getString(
                            R.string.context_menu_web_search,
                            SearchEngineWrapper.get(getContext()).resolveCurrentSearchEngine().getName()),
                    ACTION_WEB_SEARCH,
                    this::handleAction));
        }

        for (int i = 0; i < buttons.size(); ++i) {
            mContainer.addView(buttons.get(i));
            if (i < buttons.size() - 1) {
                mContainer.addView(createSeparator());
            }

            int backgroundId = R.drawable.selection_menu_button;
            if (buttons.size() == 1) {
                backgroundId = R.drawable.selection_menu_button_single;
            } else if (i == 0) {
                backgroundId = R.drawable.selection_menu_button_first;
            } else if (i == buttons.size() - 1) {
                backgroundId = R.drawable.selection_menu_button_last;
            }
            buttons.get(i).setBackgroundDrawable(getContext().getDrawable(backgroundId));
        }
    }

    public boolean hasAction(String aAction) {
        return mActions != null && mActions.contains(aAction);
    }

    public boolean hasSameActions(@NonNull Collection<String> aActions) {
        return (mActions != null) && mActions.containsAll(aActions) && (mActions.size() == aActions.size());
    }

    private UITextButton createButton(String aString, String aAction, OnClickListener aHandler) {
        UITextButton button = new UITextButton(getContext(), null, R.attr.selectionActionButtonStyle);
        button.setBackground(getContext().getDrawable(R.drawable.autocompletion_item_background));
        if (aHandler != null) {
            button.setOnClickListener(aHandler);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        button.setMinWidth(mMinButtonWidth);
        button.setMaxWidth(mMaxButtonWidth);
        params.gravity = CENTER_VERTICAL;
        button.setLayoutParams(params);
        button.setTag(aAction);
        button.setText(aString);

        return button;
    }

    private View createSeparator() {
        View view = new View(getContext());
        float density = getContext().getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams((int)(1.5f * density), (int)(40.0f * density));
        params.gravity = CENTER_VERTICAL;
        view.setLayoutParams(params);
        view.setBackground(getContext().getDrawable(R.drawable.separator_background));
        return view;
    }

    private void handleAction(View sender) {
        String action = (String)sender.getTag();
        if (action.equals(ACTION_WEB_SEARCH) && mSelectionText != null) {
            WindowWidget focusedWindow = mWidgetManager.getWindows().getFocusedWindow();
            mWidgetManager.getWindows().addTab(
                    focusedWindow,
                    UrlUtils.urlForText(getContext(), mSelectionText, focusedWindow.getSession().getWSession().getUrlUtilsVisitor()));
        }

        if (mDelegate != null) {
            mDelegate.onAction(action);
        }
    }

    // WidgetManagerDelegate.FocusChangeListener

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (!ViewUtils.isEqualOrChildrenOf(this, newFocus)) {
            onDismiss();
        }
    }
}