package org.mozilla.vrbrowser.ui.views;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import org.mozilla.vrbrowser.R;

public class DeveloperOptionsEditText extends android.support.v7.widget.AppCompatEditText {

    public DeveloperOptionsEditText(Context context, AttributeSet attrs) {
        super(context, attrs);

        initialize();
    }

    public DeveloperOptionsEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        initialize();
    }

    private void initialize() {
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
        setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                if (b)
                    setTextColor(getContext().getColor(R.color.fog));
                else
                    setTextColor(getContext().getColor(R.color.asphalt));
            }
        });
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                selectAll();
            }
        });
    }

    @Override
    public void addTextChangedListener(final TextWatcher watcher) {
        super.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                watcher.beforeTextChanged(charSequence, i, i1, i2);
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                watcher.onTextChanged(charSequence, i, i1, i2);
            }

            @Override
            public void afterTextChanged(Editable editable) {
                setTextColor(getContext().getColor(R.color.asphalt));
                watcher.afterTextChanged(editable);
            }
        });
    }

    @Override
    public void setOnFocusChangeListener(final View.OnFocusChangeListener l) {
        super.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                l.onFocusChange(view, b);
            }
        });
    }

    @Override
    public void setOnClickListener(final View.OnClickListener l) {
        super.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                l.onClick(view);
            }
        });
    }

}
