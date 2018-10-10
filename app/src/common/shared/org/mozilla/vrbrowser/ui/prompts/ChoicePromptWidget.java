package org.mozilla.vrbrowser.ui.prompts;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

import org.mozilla.geckoview.GeckoSession.PromptDelegate.Choice;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.WidgetManagerDelegate;
import org.mozilla.vrbrowser.WidgetPlacement;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.ui.UIWidget;

import java.util.ArrayList;
import java.util.Arrays;

public class ChoicePromptWidget extends UIWidget implements WidgetManagerDelegate.FocusChangeListener {

    private static final int DIALOG_CLOSE_DELAY = 250;

    public interface ChoicePromptDelegate {
        void onDismissed(String[] text);
    }

    private AudioEngine mAudio;
    private ListView mList;
    private Button mCloseButton;
    private Button mOkButton;
    private TextView mPromptTitle;
    private TextView mPromptMessage;
    private ChoiceWrapper[] mListItems;
    private ChoicePromptDelegate mPromptDelegate;
    private ChoiceAdapter mAdapter;
    private final Handler handler = new Handler();

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

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.choice_prompt, this);

        mWidgetManager.addFocusChangeListener(this);

        mAudio = AudioEngine.fromContext(aContext);

        mList = findViewById(R.id.choiceslist);
        mList.setSoundEffectsEnabled(false);
        mList.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            public void onItemClick(AdapterView<?> parent, View view, final int position, long id)
            {
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }

                mAdapter.notifyDataSetChanged();

                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        ChoiceWrapper selectedItem = mListItems[position];
                        if (mList.getChoiceMode() == ListView.CHOICE_MODE_SINGLE) {
                            if (mPromptDelegate != null) {
                                mPromptDelegate.onDismissed(new String[]{selectedItem.getChoice().id});
                            }
                        }
                    }
                }, DIALOG_CLOSE_DELAY);
            }
        });

        mPromptTitle = findViewById(R.id.promptTitle);
        mPromptMessage = findViewById(R.id.promptMessage);

        mCloseButton = findViewById(R.id.closeButton);
        mCloseButton.setSoundEffectsEnabled(false);
        mCloseButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }

                switch (mList.getChoiceMode()) {
                    case ListView.CHOICE_MODE_SINGLE:
                    case ListView.CHOICE_MODE_MULTIPLE: {
                        if (mPromptDelegate != null) {
                            mPromptDelegate.onDismissed(getDefaultChoices(mListItems));
                        }
                    }
                    break;
                }
            }
        });

        mOkButton = findViewById(R.id.okButton);
        mOkButton.setSoundEffectsEnabled(false);
        mOkButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }

                switch (mList.getChoiceMode()) {
                    case ListView.CHOICE_MODE_SINGLE:
                    case ListView.CHOICE_MODE_MULTIPLE: {
                        if (mPromptDelegate != null) {
                            int len = mList.getCount();
                            SparseBooleanArray selected = mList.getCheckedItemPositions();
                            ArrayList<String> selectedChoices = new ArrayList<>();
                            for (int i = 0; i < len; i++) {
                                if (selected.get(i)) {
                                    selectedChoices.add(mListItems[i].getChoice().id);
                                }
                            }
                            mPromptDelegate.onDismissed(selectedChoices.toArray(new String[selectedChoices.size()]));
                        }
                    }
                    break;
                }
            }
        });

        mListItems = new ChoiceWrapper[]{};
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.choice_prompt_width);
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.choice_prompt_height);
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.5f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.choice_prompt_world_z);
    }

    @Override
    public void releaseWidget() {
        mWidgetManager.removeFocusChangeListener(this);

        super.releaseWidget();
    }

    @Override
    protected void onBackButton() {
        hide();

        if (mPromptDelegate != null) {
            mPromptDelegate.onDismissed(getDefaultChoices(mListItems));
        }
    }

    @Override
    public void show() {
        super.show();

        for (int i = 0; i < mListItems.length; i++) {
            mList.setItemChecked(i, mListItems[i].mChoice.selected);
        }
        mAdapter.notifyDataSetChanged();
    }

    public void setDelegate(ChoicePromptDelegate delegate) {
        mPromptDelegate = delegate;
    }

    public void setChoices(Choice[] choices) {
        mListItems = getWrappedChoices(choices);
        mAdapter = new ChoiceAdapter(getContext(), R.layout.choice_prompt_item, mListItems);
        mList.setAdapter(mAdapter);
    }

    public void setTitle(String title) {
        if (title == null || title.isEmpty()) {
            mPromptTitle.setVisibility(View.GONE);

        } else {
            mPromptTitle.setText(title);
        }
    }

    public void setMessage(String message) {
        if (message == null || message.isEmpty()) {
            mPromptMessage.setVisibility(View.GONE);

        } else {
            mPromptMessage.setText(message);
        }
    }

    public void setMenuType(int type) {
        switch (type) {
            case Choice.CHOICE_TYPE_SINGLE:
            case Choice.CHOICE_TYPE_MENU: {
                mList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
                mCloseButton.setVisibility(View.VISIBLE);
                mOkButton.setVisibility(View.GONE);
            }
            break;
            case Choice.CHOICE_TYPE_MULTIPLE: {
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

    @NonNull
    private static String[] getDefaultChoices(ChoiceWrapper[] aChoices) {
        ArrayList<String> defaultChoices = new ArrayList<>();
        for (int i = 0; i < aChoices.length; i++) {
            if (aChoices[i].getChoice().selected) {
                defaultChoices.add(aChoices[i].getChoice().id);
            }
        }

        return defaultChoices.toArray(new String[defaultChoices.size()]);
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
                listItem = mInflater.inflate(R.layout.choice_prompt_item, parent, false);

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

        private OnHoverListener mHoverListener = new OnHoverListener() {
            @Override
            public boolean onHover(View view, MotionEvent motionEvent) {
                int position = (int)view.getTag(R.string.position_tag);
                if (!isEnabled(position))
                    return false;

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
            }
        };

    }

    // WidgetManagerDelegate.FocusChangeListener
    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (oldFocus == this) {
            if (mPromptDelegate != null) {
                mPromptDelegate.onDismissed(getDefaultChoices(mListItems));
            }
        }
    }

}
