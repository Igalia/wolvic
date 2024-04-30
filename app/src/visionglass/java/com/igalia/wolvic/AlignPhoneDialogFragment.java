package com.igalia.wolvic;

import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

public class AlignPhoneDialogFragment extends DialogFragment {
    private final int mThemeResId;
    private AlignDynamicButton mRealignButton;
    private View.OnClickListener mRealignButtonClickListener;

    public AlignPhoneDialogFragment(int themeResId) {
        mThemeResId = themeResId;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(getContext(), mThemeResId);
        LayoutInflater themedInflater = getLayoutInflater().cloneInContext(themedContext);
        View view = themedInflater.inflate(R.layout.dialog_align_phone, container);
        mRealignButton = view.findViewById(R.id.realign_button);
        if (mRealignButtonClickListener != null)
            mRealignButton.setOnClickListener(mRealignButtonClickListener);

        return view;
    }

    public void updatePosition(float x, float y) {
        mRealignButton.updatePosition(x, y);
    }

    public void setOnRealignButtonClickListener(View.OnClickListener listener) {
        mRealignButtonClickListener = listener;
        if (mRealignButton != null)
            mRealignButton.setOnClickListener(listener);
    }
}
