package org.mozilla.vrbrowser.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.FrameLayout;
import android.widget.GridLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;

import java.util.ArrayList;
import java.util.List;


public class KeyboardSelectorView extends FrameLayout {
    public static class Item {
        public final String title;
        public final Object tag;

        public Item(String aTitle, Object aTag) {
            this.title = aTitle;
            this.tag = aTag;
        }
    }

    public interface Delegate {
        void onItemClick(Item aItem);
    }

    private GridLayout mLangRowContainer;
    private Delegate mDelegate;
    private List<Item> mItems;
    private List<UITextButton> mButtons = new ArrayList<>();
    private int mDomainColItemWidth;
    private int mNarrowColItemWidth;
    private int mWideColItemWidth;
    private static final int kMaxItemsPerColumn = 4;

    public KeyboardSelectorView(@NonNull Context context) {
        super(context);
        initialize();
    }

    public KeyboardSelectorView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public KeyboardSelectorView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        inflate(getContext(), R.layout.language_selection, this);
        mLangRowContainer = findViewById(R.id.langRowContainer);
        mDomainColItemWidth = WidgetPlacement.pixelDimension(getContext(), R.dimen.lang_selector_domain_col_item_width);
        mNarrowColItemWidth = WidgetPlacement.pixelDimension(getContext(), R.dimen.lang_selector_narrow_col_item_width);
        mWideColItemWidth = WidgetPlacement.pixelDimension(getContext(), R.dimen.lang_selector_wide_col_item_width);
    }

    public void setDelegate(Delegate aDelegate) {
        mDelegate = aDelegate;
    }

    public void setItems(List<Item> aItems) {
        mItems = aItems;
        mLangRowContainer.removeAllViews();
        mButtons.clear();
        int columns = (aItems.size() / kMaxItemsPerColumn) + 1;
        int rows = aItems.size() < kMaxItemsPerColumn ? aItems.size() : kMaxItemsPerColumn;
        mLangRowContainer.setColumnCount(columns);
        mLangRowContainer.setRowCount(rows);

        int[] columnWidth = new int[columns];
        for (int i=0; i<aItems.size(); i++) {
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = GridLayout.LayoutParams.WRAP_CONTENT;
            UITextButton button = createItemButton(aItems.get(i));
            int padding = button.getPaddingStart() + button.getPaddingEnd();
            float textWidth = button.getPaint().measureText(aItems.get(i).title) + padding;
            int column = i/kMaxItemsPerColumn;
            int width;
            if (textWidth < mDomainColItemWidth) {
                width = mDomainColItemWidth;
            } else if (textWidth < mNarrowColItemWidth) {
                width = mNarrowColItemWidth;
            } else  {
                width = mWideColItemWidth;
            }
            if (columnWidth[column] < width) {
                columnWidth[column] = width;
            }
            mLangRowContainer.addView(button, params);
            mButtons.add(button);
        }

        // Assign widest row width to the whole column rows
        for (int i=0; i<aItems.size(); i++) {
            int column = i/kMaxItemsPerColumn;
            UITextButton button = (UITextButton)mLangRowContainer.getChildAt(i);
            button.getLayoutParams().width = columnWidth[column];
        }
    }

    public void setSelectedItem(Object aTag) {
        for (UITextButton button: mButtons) {
            Item item = (Item) button.getTag();
            button.setSelected(item.tag == aTag);
        }
    }

    public List<Item> getItems() {
        return mItems;
    }

    private OnClickListener clickHandler = v -> {
        UITextButton button = (UITextButton) v;
        if (mDelegate != null) {
            mDelegate.onItemClick((Item)button.getTag());
        }
    };

    private UITextButton createItemButton(Item aItem) {
        UITextButton button = new UITextButton(getContext());
        button.setTintColorList(R.drawable.lang_selector_button_color);
        button.setBackground(getContext().getDrawable(R.drawable.lang_selector_button_background));
        button.setOnClickListener(clickHandler);
        button.setPadding(13, 13, 13, 13);
        button.setIncludeFontPadding(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        button.setText(aItem.title);
        button.setTextAlignment(TEXT_ALIGNMENT_VIEW_START);
        button.setTag(aItem);

        return button;
    }
}
