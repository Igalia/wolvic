package org.mozilla.vrbrowser.ui.widgets.prompts;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.mozilla.geckoview.GeckoSession.PromptDelegate.ChoicePrompt.Choice;
import org.mozilla.geckoview.GeckoSession.PromptDelegate.ChoicePrompt.Type;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;

import java.util.ArrayList;
import java.util.Arrays;

public class ChoicePromptWidget extends PromptWidget {

    public interface ChoicePromptDelegate extends PromptDelegate {
        void confirm(String[] choices);
    }

    private static final int DIALOG_CLOSE_DELAY = 250;
    private static final int LISTVIEW_ITEM_HEIGHT = 20;

    private AudioEngine mAudio;
    private ListView mList;
    private Button mCloseButton;
    private Button mOkButton;
    private ChoiceWrapper[] mListItems;
    private ChoiceAdapter mAdapter;

    public ChoicePromptWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public ChoicePromptWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public ChoicePromptWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    protected void initialize(Context aContext) {
        inflate(aContext, R.layout.prompt_choice, this);

        mAudio = AudioEngine.fromContext(aContext);

        mLayout = findViewById(R.id.layout);

        mList = findViewById(R.id.choiceslist);
        mList.setOnItemClickListener((parent, view, position, id) -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            mAdapter.notifyDataSetChanged();

            postDelayed(() -> {
                ChoiceWrapper selectedItem = mListItems[position];
                if (mList.getChoiceMode() == ListView.CHOICE_MODE_SINGLE) {
                    if (mPromptDelegate != null && mPromptDelegate instanceof ChoicePromptDelegate) {
                        ((ChoicePromptDelegate)mPromptDelegate).confirm(new String[]{selectedItem.getChoice().id});
                        hide(REMOVE_WIDGET);
                    }
                }
            }, DIALOG_CLOSE_DELAY);
        });

        mTitle = findViewById(R.id.promptTitle);
        mMessage = findViewById(R.id.promptMessage);

        mCloseButton = findViewById(R.id.negativeButton);
        mCloseButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            switch (mList.getChoiceMode()) {
                case ListView.CHOICE_MODE_SINGLE:
                case ListView.CHOICE_MODE_MULTIPLE: {
                    if (mPromptDelegate != null) {
                       onDismiss();
                    }
                }
                break;
            }
        });

        mOkButton = findViewById(R.id.positiveButton);
        mOkButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            switch (mList.getChoiceMode()) {
                case ListView.CHOICE_MODE_SINGLE:
                case ListView.CHOICE_MODE_MULTIPLE: {
                    if (mPromptDelegate != null && mPromptDelegate instanceof ChoicePromptDelegate) {
                        int len = mList.getCount();
                        SparseBooleanArray selected = mList.getCheckedItemPositions();
                        ArrayList<String> selectedChoices = new ArrayList<>();
                        for (int i = 0; i < len; i++) {
                            if (selected.get(i)) {
                                selectedChoices.add(mListItems[i].getChoice().id);
                            }
                        }
                        ((ChoicePromptDelegate)mPromptDelegate).confirm(selectedChoices.toArray(new String[selectedChoices.size()]));
                    }
                }
                break;
            }

            hide(REMOVE_WIDGET);
        });

        mListItems = new ChoiceWrapper[]{};
    }

    @Override
    public void show(@ShowFlags int aShowFlags) {
        super.show(aShowFlags);
        for (int i = 0; i < mListItems.length; i++) {
            mList.setItemChecked(i, mListItems[i].mChoice.selected);
        }
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public int getMinHeight() {
        return  WidgetPlacement.dpDimension(getContext(), R.dimen.prompt_min_height);
    }

    public void setChoices(Choice[] choices) {
        mListItems = getWrappedChoices(choices);
        mAdapter = new ChoiceAdapter(getContext(), R.layout.prompt_choice_item, mListItems);
        mList.setAdapter(mAdapter);
        int height = WidgetPlacement.dpDimension(getContext(), R.dimen.prompt_choice_min_height);
        height += choices.length * LISTVIEW_ITEM_HEIGHT;
        height = Math.min(height, WidgetPlacement.dpDimension(getContext(), R.dimen.prompt_choice_max_height));
        mWidgetPlacement.height = height;
    }

    public void setMenuType(int type) {
        switch (type) {
            case Type.SINGLE:
            case Type.MENU: {
                mList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
                mCloseButton.setVisibility(View.VISIBLE);
                mOkButton.setVisibility(View.GONE);
            }
            break;
            case Type.MULTIPLE: {
                mList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
                mCloseButton.setVisibility(View.VISIBLE);
                mOkButton.setVisibility(View.VISIBLE);
            }
            break;
        }
    }

    @NonNull
    private static ChoiceWrapper[] getWrappedChoices(Choice[] aChoices) {
        return getWrappedChoices(aChoices, 0);
    }

    @NonNull
    private static ChoiceWrapper[] getWrappedChoices(Choice[] aChoices, int aLevel) {
        ArrayList<ChoiceWrapper> flattenedChoicesList = new ArrayList<>();
        for (int i = 0; i < aChoices.length; i++) {
            flattenedChoicesList.add(new ChoiceWrapper(aChoices[i], aLevel));
            if (aChoices[i].items != null && aChoices[i].items.length > 0) {
                ChoiceWrapper[] childChoices = getWrappedChoices(aChoices[i].items, aLevel+1);
                flattenedChoicesList.addAll(Arrays.asList(childChoices));
            }
        }

        return flattenedChoicesList.toArray(new ChoiceWrapper[flattenedChoicesList.size()]);
    }

    static class ChoiceWrapper {

        private int mLevel;
        private Choice mChoice;
        private boolean isParent;

        public ChoiceWrapper(Choice choice, int level) {
            mChoice = choice;
            mLevel = level;
            isParent = mChoice.items != null && mChoice.items.length > 0;
        }

        public boolean isParent() {
            return isParent;
        }

        public int getLevel() {
            return mLevel;
        }

        public Choice getChoice() {
            return mChoice;
        }
    }

    public class ChoiceAdapter extends ArrayAdapter<ChoiceWrapper> {

        private class ChoiceViewHolder {
            LinearLayout layout;
            RadioButton check;
            TextView label;
        }

        private LayoutInflater mInflater;

        public ChoiceAdapter(Context context, int resource, ChoiceWrapper[] choices) {
            super(context, resource, choices);

            mInflater = LayoutInflater.from(getContext());
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return !getItem(position).getChoice().disabled && !getItem(position).isParent();
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View listItem = convertView;

            ChoiceViewHolder choiceViewHolder;
            if(listItem == null) {
                listItem = mInflater.inflate(R.layout.prompt_choice_item, parent, false);

                choiceViewHolder = new ChoiceViewHolder();

                choiceViewHolder.layout = listItem.findViewById(R.id.choiceItemLayoutId);
                choiceViewHolder.check = listItem.findViewById(R.id.radioOption);
                choiceViewHolder.label = listItem.findViewById(R.id.optionLabel);

                listItem.setTag(R.string.list_item_view_tag, choiceViewHolder);

                listItem.setOnHoverListener(mHoverListener);

            } else {
                choiceViewHolder = (ChoiceViewHolder) listItem.getTag(R.string.list_item_view_tag);
            }

            ChoiceWrapper currentChoice = getItem(position);

            // Reset state
            choiceViewHolder.check.setVisibility(View.VISIBLE);
            choiceViewHolder.label.setEnabled(true);
            choiceViewHolder.check.setEnabled(true);
            choiceViewHolder.layout.setTag(R.string.position_tag, position);

            choiceViewHolder.label.setTypeface(choiceViewHolder.check.getTypeface(), Typeface.NORMAL);
            if (currentChoice.isParent()) {
                choiceViewHolder.label.setTypeface(choiceViewHolder.check.getTypeface(), Typeface.BOLD);
                choiceViewHolder.check.setVisibility(View.GONE);
                choiceViewHolder.label.setEnabled(false);
            }
            choiceViewHolder.label.setText(currentChoice.getChoice().label);

            listItem.setEnabled(!currentChoice.getChoice().disabled);

            choiceViewHolder.check.setChecked(mList.isItemChecked(position));

            if (currentChoice.getChoice().disabled) {
                choiceViewHolder.check.setEnabled(false);
                choiceViewHolder.label.setEnabled(false);
            }

            return listItem;
        }

        private OnHoverListener mHoverListener = (view, motionEvent) -> {
            int position = (int)view.getTag(R.string.position_tag);
            if (!isEnabled(position)) {
                return false;
            }

            TextView label = view.findViewById(R.id.optionLabel);
            RadioButton check = view.findViewById(R.id.radioOption);
            int ev = motionEvent.getActionMasked();
            switch (ev) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    view.setHovered(true);
                    label.setHovered(true);
                    check.setHovered(true);
                    view.setBackgroundResource(R.drawable.prompt_item_selected);
                    return true;

                case MotionEvent.ACTION_HOVER_EXIT:
                    view.setHovered(false);
                    label.setHovered(false);
                    check.setHovered(false);
                    view.setBackgroundColor(getContext().getColor(R.color.void_color));
                    return true;
            }

            return false;
        };

    }

}
