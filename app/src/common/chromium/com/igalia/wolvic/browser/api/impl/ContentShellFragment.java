package com.igalia.wolvic.browser.api.impl;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.igalia.wolvic.R;

import org.chromium.components.embedder_support.view.ContentViewRenderView;

public class ContentShellFragment extends Fragment {
    private ContentViewRenderView mContentViewRenderView;

    public ContentShellFragment() {
        super(R.layout.content_shell_fragment);
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        ViewGroup view = (ViewGroup) super.onCreateView(inflater, container, savedInstanceState);
        assert view != null;
        view.addView(
                mContentViewRenderView,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    public void setContentViewRenderView(ContentViewRenderView contentViewRenderView) {
        mContentViewRenderView = contentViewRenderView;
    }
}
