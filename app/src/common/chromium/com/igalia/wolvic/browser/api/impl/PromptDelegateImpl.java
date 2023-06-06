package com.igalia.wolvic.browser.api.impl;

import android.content.Context;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.chromium.base.Callback;
import org.chromium.content.browser.input.SelectPopup;
import org.chromium.content.browser.input.SelectPopupItem;

import com.igalia.wolvic.browser.api.WSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class PromptDelegateImpl {
    WSession.PromptDelegate mDelegate;
    SessionImpl mSession;

    public class SelectPopupFactory implements SelectPopup.Factory {
        public SelectPopup.Ui create(Context windowContext, Callback<int[]> selectionChangedCallback,
                List<SelectPopupItem> items, boolean multiple, int[] selected) {
            return new ChoicePromptBridge(windowContext, selectionChangedCallback, items, multiple, selected);
        }
    }

    private static class PromptResponseImpl implements WSession.PromptDelegate.PromptResponse {
        public PromptResponseImpl() {
        }
    }

    public PromptDelegateImpl(WSession.PromptDelegate mDelegate, SessionImpl mSession) {
        this.mDelegate = mDelegate;
        this.mSession = mSession;

        SelectPopup.setFactory(new SelectPopupFactory());
    }

    public WSession.PromptDelegate getDelegate() { return this.mDelegate; }

    public class BasePromptImpl implements WSession.PromptDelegate.BasePrompt {
        private WSession.PromptDelegate.PromptInstanceDelegate mDelegate;
        boolean mIsCompleted;

        @Override
        @Nullable
        public String title() { return null; }

        @NonNull
        @Override
        public WSession.PromptDelegate.PromptResponse dismiss() {
            confirm();
            return new PromptResponseImpl();
        }

        @UiThread
        @Override
        public void setDelegate(final @Nullable WSession.PromptDelegate.PromptInstanceDelegate delegate) {
            mDelegate = delegate;
        }

        @UiThread
        @Override
        @Nullable
        public WSession.PromptDelegate.PromptInstanceDelegate getDelegate() {
            return mDelegate;
        }


        @Override
        public boolean isComplete() {
            return mIsCompleted;
        }

        public WSession.PromptDelegate.PromptResponse confirm() {
            mIsCompleted = true;
            return new PromptResponseImpl();
        }
    }

    public class ChoicePrompt extends BasePromptImpl implements  WSession.PromptDelegate.ChoicePrompt {
        public class Choice implements WSession.PromptDelegate.ChoicePrompt.Choice {
            public final boolean disabled;
            public final @Nullable String icon;
            public final @NonNull String id;
            public final @NonNull String label;
            public boolean selected;
            public final boolean separator;
            public @Nullable Choice[] choices;

            /* package */ Choice(SelectPopupItem item, int id, boolean selected) {
                this.disabled = !item.isEnabled();
                this.id =  String.valueOf(id);
                this.icon = null;
                this.label = item.getLabel();
                this.separator = false;
                this.selected = selected;
                this.choices = null;
            }

            @Override
            public boolean disabled() {
                return this.disabled;
            }

            @Nullable
            @Override
            public String icon() {
                return this.icon;
            }

            @NonNull
            @Override
            public String id() {
                return this.id;
            }

            @NonNull
            @Override
            public String label() {
                return this.label;
            }

            @Override
            public boolean selected() {
                return this.selected;
            }

            @Override
            public boolean separator() {
                return this.separator;
            }

            @Override
            @Nullable
            public Choice[] items() { return this.choices; }

        }
        public final @Nullable String message;

        public final @WSession.PromptDelegate.ChoicePrompt.ChoiceType int type;

        public final @Nullable Choice[] choices;

        private final Callback<int[]> mSelectionChangedCallback;

        private Choice createChoice(List<SelectPopupItem> items, int[] position, int[] selected,
                                    int[] selectedIndex) {
            if (items.size() == position[0])
                return null;

            SelectPopupItem item = items.get(position[0]);

            boolean isSelected = false;
            if (selected.length > selectedIndex[0] && selected[selectedIndex[0]] == position[0]) {
                isSelected = true;
                selectedIndex[0]++;
            }

            Choice choice = new Choice(item, position[0], isSelected);
            if (item.isGroupHeader()) {
                ArrayList<Choice> choices = new ArrayList<>();
                int nextPosition = position[0]+1;
                while (items.size() > nextPosition) {
                    SelectPopupItem childItem = items.get(nextPosition);
                    if (childItem.isGroupHeader())
                        break;

                    position[0] = nextPosition;
                    Choice child = createChoice(items, position, selected, selectedIndex);
                    assert child != null;
                    choices.add(child);
                    nextPosition = position[0]+1;
                }
                choice.choices = choices.toArray(new Choice[choices.size()]);
            }
            return choice;
        }

        public ChoicePrompt(List<SelectPopupItem> items, boolean multiple, int[] selected,
                            Callback<int[]> selectionChangedCallback) {
            if (items == null) {
                this.choices = null;
            } else {
                ArrayList<Choice> choices = new ArrayList<>();
                int[] position = new int[]{0};
                int[] selectedIndex = new int[]{0};
                while (position[0] < items.size()) {
                    Choice child = createChoice(items, position, selected, selectedIndex);
                    assert child != null;
                    choices.add(child);
                    position[0]++;
                }
                this.choices = choices.toArray(new Choice[choices.size()]);
            }

            message = null;
            type = multiple ? WSession.PromptDelegate.ChoicePrompt.Type.MULTIPLE :
                    WSession.PromptDelegate.ChoicePrompt.Type.SINGLE;
            mSelectionChangedCallback = selectionChangedCallback;
        }

        private <T> boolean isValidArgument(T[] selectedChoices) {
            if ((WSession.PromptDelegate.ChoicePrompt.Type.MENU == type ||
                    WSession.PromptDelegate.ChoicePrompt.Type.SINGLE == type)
                    && (selectedChoices == null || selectedChoices.length != 1)) {
                throw new IllegalArgumentException();
            }
            return true;
        }

        @NonNull
        public WSession.PromptDelegate.PromptResponse confirm(final int[] selectedIds) {
            if (!isComplete())
                mSelectionChangedCallback.onResult(selectedIds);
            return super.confirm();
        }

        @UiThread
        @Override
        @NonNull
        public WSession.PromptDelegate.PromptResponse confirm(@NonNull final String selectedId) {
            try {
                int id = Integer.parseInt(selectedId);
                return confirm(new int[] {id});
            } catch (NumberFormatException nfe) {
                return confirm((int[]) null);
            }
        }

        @UiThread
        @Override
        @NonNull
        public WSession.PromptDelegate.PromptResponse confirm(@NonNull final String[] selectedIds) {
            if (!isValidArgument(selectedIds)) {
                return confirm((int[]) null);
            }

            int length = 0;
            int[] ids = new int[selectedIds.length];
            for (int i = 0; i < selectedIds.length; i++) {
                try {
                    ids[length++] = Integer.parseInt(selectedIds[i]);
                } catch (NumberFormatException nfe) {
                }
            }

            ids = Arrays.copyOf(ids, length);
            return confirm(ids);
        }

        @UiThread
        @Override
        @NonNull
        public WSession.PromptDelegate.PromptResponse confirm(
                @NonNull final WSession.PromptDelegate.ChoicePrompt.Choice selectedChoice) {
            return confirm(selectedChoice == null ? null : selectedChoice.id());
        }

        @UiThread
        @Override
        @NonNull
        public WSession.PromptDelegate.PromptResponse confirm(
                @NonNull final WSession.PromptDelegate.ChoicePrompt.Choice[] selectedChoices) {
            if (!isValidArgument(selectedChoices)) {
                return confirm((int[]) null);
            }

            int[] ids = new int[selectedChoices.length];
            int length = 0;
            for (int i = 0; i < ids.length; i++) {
                if (selectedChoices[i] != null) {
                    try {
                        ids[length++] = Integer.parseInt(selectedChoices[i].id());
                    } catch (NumberFormatException nfe) {
                    }
                }
            }
            ids = Arrays.copyOf(ids, length);
            return confirm(ids);
        }

        @NonNull
        @Override
        public WSession.PromptDelegate.PromptResponse dismiss() {
            if (!isComplete())
                mSelectionChangedCallback.onResult(null);
            return super.dismiss();
        }

        @Override
        @NonNull
        public WSession.PromptDelegate.ChoicePrompt.Choice[] choices() {
            return this.choices;
        }

        @Override
        @Nullable
        public String message() {
            return this.message;
        }

        @Override
        @ChoiceType public int type() {
            return this.type;
        }
    }

    public class ChoicePromptBridge implements SelectPopup.Ui {

        private final ChoicePrompt mChoicePrompt;

        public ChoicePromptBridge(Context windowContext, Callback<int[]> selectionChangedCallback,
            List<SelectPopupItem> items, boolean multiple, int[] selected) {
            mChoicePrompt = new ChoicePrompt(items, multiple, selected, selectionChangedCallback);
        }

        @Override
        public void show() {
            try {
                final WSession.PromptDelegate delegate = mSession.getPromptDelegate();
                delegate.onChoicePrompt(mSession, mChoicePrompt);
            } catch (WindowManager.BadTokenException e) {
                mChoicePrompt.confirm();
            }
        }

        @Override
        public void hide(boolean sendsCancelMessage) {
            if (!sendsCancelMessage) {
                mChoicePrompt.confirm();
            }
            mChoicePrompt.dismiss();
        }
    }
}
