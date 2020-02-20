package org.mozilla.vrbrowser.ui.views.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.Nullable;

import org.mozilla.vrbrowser.R;

@SuppressLint("AppCompatCustomView")
public class SettingsEditText extends EditText {

    private int mNormalTextColor = 0;
    private int mHighlightedTextColor = 0;

    public SettingsEditText(Context context) {
        this(context, null);
    }

    public SettingsEditText(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SettingsEditText(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.EditSetting, defStyleAttr, 0);
        mHighlightedTextColor = attributes.getColor(R.styleable.EditSetting_highlightedTextColor, 0);
        setShowSoftInputOnFocus(false);

        initialize();
    }

    private void initialize() {
        mNormalTextColor = getTextColors().getColorForState(View.EMPTY_STATE_SET, 0);
        addTextChangedListener(watcher);

        setOnFocusChangeListener((view, b) -> {
            if (hasSelection()) {
                if (b) {
                    int start = getSelectionStart();
                    int end = getSelectionEnd();
                    if (end < start) {
                        int tmp = end;
                        end = start;
                        start = tmp;
                    }

                    ForegroundColorSpan highlightedColor = new ForegroundColorSpan(mHighlightedTextColor);
                    getText().setSpan(highlightedColor, start, end, 0);

                } else {
                    ForegroundColorSpan normalColor = new ForegroundColorSpan(mNormalTextColor);
                    getText().setSpan(normalColor, 0, length(), 0);
                }

            } else {
                ForegroundColorSpan normalColor = new ForegroundColorSpan(mNormalTextColor);
                getText().setSpan(normalColor, 0, length(), 0);
            }
        });
    }

    TextWatcher watcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            ForegroundColorSpan normalColor = new ForegroundColorSpan(mNormalTextColor);
            getText().setSpan(normalColor, 0, length(), 0);
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

        }

        @Override
        public void afterTextChanged(Editable editable) {

        }
    };

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);

        if (getTextColors() != null) {
            handleTextColor();
        }
    }

    private void handleTextColor() {
        if (mHighlightedTextColor == 0) {
            mHighlightedTextColor = mNormalTextColor;
        }

        if (isPressed()) {
            ForegroundColorSpan normalColor = new ForegroundColorSpan(mNormalTextColor);
            getText().setSpan(normalColor, 0, length(), 0);
        }

        if (hasSelection()) {
            int start = getSelectionStart();
            int end = getSelectionEnd();
            if (end < start) {
                int tmp = end;
                end = start;
                start = tmp;
            }

            ForegroundColorSpan normalColor = new ForegroundColorSpan(mNormalTextColor);
            getText().setSpan(normalColor, 0, length(), 0);
            ForegroundColorSpan highlightedColor = new ForegroundColorSpan(mHighlightedTextColor);
            getText().setSpan(highlightedColor, start, end, 0);
        }
    }

    public void setHighlightedTextColor(int color) {
        mHighlightedTextColor = color;
    }

}
