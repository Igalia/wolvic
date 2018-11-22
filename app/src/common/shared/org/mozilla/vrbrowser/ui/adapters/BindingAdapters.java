package org.mozilla.vrbrowser.ui.adapters;

import android.view.View;

import androidx.databinding.BindingAdapter;


public class BindingAdapters {
    @BindingAdapter("visibleGone")
    public static void showHide(View view, boolean show) {
        view.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}