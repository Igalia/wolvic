package org.mozilla.vrbrowser.ui.views;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import mozilla.components.ui.autocomplete.InlineAutocompleteEditText;

public class CustomInlineAutocompleteEditText extends InlineAutocompleteEditText {
    private OnSelectionChangedCallback mSelectionCallback;
    interface OnSelectionChangedCallback {
        void onSelectionChanged(int selectionStart, int selectionEnd);
    }

    public CustomInlineAutocompleteEditText(@NonNull Context ctx, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(ctx, attrs, defStyleAttr);
    }

    public CustomInlineAutocompleteEditText(@NonNull Context ctx, @Nullable AttributeSet attrs) {
        super(ctx, attrs);
    }

    public CustomInlineAutocompleteEditText(@NonNull Context ctx) {
        super(ctx);
    }

    @Override
    public void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (mSelectionCallback != null) {
            mSelectionCallback.onSelectionChanged(selStart, selEnd);
        }
    }

    public void setOnSelectionChangedCallback(@Nullable OnSelectionChangedCallback aCallback) {
        mSelectionCallback = aCallback;
    }
}
