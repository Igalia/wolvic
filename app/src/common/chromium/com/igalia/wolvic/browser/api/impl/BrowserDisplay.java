package com.igalia.wolvic.browser.api.impl;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.igalia.wolvic.browser.SettingsStore;

public class BrowserDisplay extends FrameLayout {
    protected FragmentContainerView mFragmentContainer;
    protected boolean mAcquired;
    private FragmentManager mFragmentManager;


    public BrowserDisplay(@NonNull Context context) {
        super(context);
    }

    public void attach(@NonNull FragmentManager fragmentManager, @NonNull ViewGroup parentContainer, @NonNull Fragment fragment) {
        if (mFragmentContainer != null) {
            throw new IllegalStateException("Already attached");
        }
        mFragmentManager = fragmentManager;

        // Create the container for the content_shell fragment. Android recommends using FragmentContainerView.
        mFragmentContainer = new FragmentContainerView(getContext());
        mFragmentContainer.setId(View.generateViewId());

        // Add the content_shell container to the activity content. Use default width & height for now.
        // It can be resized later in surfaceChanged.
        SettingsStore settings = SettingsStore.getInstance(getContext());
        Log.e("WolvicLifecycle", "initial width=" + settings.getWindowWidth() + " height=" + settings.getWindowHeight());
        parentContainer.addView(this, new ViewGroup.LayoutParams(settings.getWindowWidth(), settings.getWindowHeight()));
        this.addView(mFragmentContainer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));


        // Retain the fragment instance to simulate the behavior of
        // external embedders (note that if this is changed, then WebLayer Shell should handle
        // rotations and resizes itself via its manifest, as otherwise the user loses all state
        // when the shell is rotated in the foreground).
        fragment.setRetainInstance(true);

        // Attach fragment to mFragmentContainer
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(mFragmentContainer.getId(), fragment);

        // Note the commitNow() instead of commit(). We want the fragment to get attached to
        // activity synchronously, so we can use all the functionality immediately. Otherwise we'd
        // have to wait until the commit is executed.
        transaction.commitNow();
    }

    public boolean isAcquired() {
        return mAcquired;
    }

    public void setAcquired(boolean value) {
        mAcquired = value;
    }


}
