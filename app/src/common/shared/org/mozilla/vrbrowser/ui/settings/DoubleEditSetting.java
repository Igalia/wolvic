package org.mozilla.vrbrowser.ui.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.mozilla.vrbrowser.R;

public class DoubleEditSetting extends SingleEditSetting {

    private String mBy;
    private TextView mText2;
    private EditText mEdit2;

    public DoubleEditSetting(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DoubleEditSetting(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.EditSetting, defStyleAttr, 0);
        mBy = attributes.getString(R.styleable.EditSetting_by);
        attributes.recycle();

        initialize(context);
    }

    private void initialize(Context aContext) {
        TextView by = findViewById(R.id.settingBy);
        by.setText(mBy);
        by.setVisibility(View.VISIBLE);

        mText2 = findViewById(R.id.textValue2);
        mEdit2 = findViewById(R.id.editValue2);
        mEdit2.setSoundEffectsEnabled(false);

        mEdit2.setOnEditorActionListener(mInternalEditorActionListener);
    }

    protected void onClickListener(View v) {
        mText2.setVisibility(mEdit1.getVisibility());
        mEdit2.setVisibility(mEdit1.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);

        super.onClickListener(v);
    }

    public String getSecondText() {
        return mEdit2.getText().toString();
    }

    public void setSecondText(String text) {
        mText2.setText(text);
        mEdit2.setText(text);
    }

}
