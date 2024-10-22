package com.igalia.wolvic.ui.views;

import java.util.List;
import java.util.concurrent.Executor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.VRBrowserApplication;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.NewTab1Binding;
import com.igalia.wolvic.ui.adapters.NewTabAdapter;

import mozilla.appservices.places.BookmarkRoot;
import mozilla.components.concept.storage.BookmarkNode;

public class NewTabView extends FrameLayout {

    private NewTab1Binding mBinding;

    private NewTabAdapter mNewTabAdapter;

    public NewTabView(Context context) {
        super(context);
        initialize();
    }

    protected void initialize() {
        updateUI();
    }

    @SuppressLint("ClickableViewAccessibility")
    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        mBinding = DataBindingUtil.inflate(inflater, R.layout.new_tab_1, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity)getContext());

        mNewTabAdapter = new NewTabAdapter();
        mBinding.bookmarksList.setAdapter(mNewTabAdapter);
        mBinding.bookmarksList.setOnTouchListener((v, event) -> {
            v.requestFocusFromTouch();
            return false;
        });
        mBinding.bookmarksList.setHasFixedSize(true);
        mBinding.bookmarksList.setItemViewCacheSize(20);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            mBinding.bookmarksList.setDrawingCacheEnabled(true);
            mBinding.bookmarksList.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
        }

        mBinding.executePendingBindings();

        updateBookmarks();

        setOnTouchListener((v, event) -> {
            v.requestFocusFromTouch();
            return false;
        });
    }

    private void updateBookmarks() {
        Executor executor = ((VRBrowserApplication)getContext().getApplicationContext()).getExecutors().mainThread();

        SessionStore.get().getBookmarkStore().getTree(BookmarkRoot.Root.getId(), true).
                thenAcceptAsync(this::showBookmarks, executor).
                exceptionally(throwable -> {
                    Log.d("NewTab", "Error getting bookmarks: " + throwable.getLocalizedMessage());
                    throwable.printStackTrace();
                    return null;
                });
    }

    private void showBookmarks(List<BookmarkNode> aBookmarks) {
        mNewTabAdapter.setBookmarkListInNewTab(aBookmarks);
        mBinding.executePendingBindings();
    }
}