package com.igalia.wolvic.ui.views;

import java.util.List;
import java.util.concurrent.Executor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.VRBrowserApplication;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.NewTabBinding;
import com.igalia.wolvic.ui.adapters.Bookmark;
import com.igalia.wolvic.ui.adapters.NewTabAdapter;
import com.igalia.wolvic.ui.callbacks.BookmarkItemCallback;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.WindowWidget;

import mozilla.appservices.places.BookmarkRoot;
import mozilla.components.concept.storage.BookmarkNode;

public class NewTabView extends FrameLayout {

    private WidgetManagerDelegate mWidgetManager;

    private NewTabBinding mBinding;

    private NewTabAdapter mNewTabAdapter;

    public NewTabView(Context context) {
        super(context);
        initialize();
    }

    protected void initialize() {
        mWidgetManager = ((VRBrowserActivity) getContext());
        updateUI();
    }

    @SuppressLint("ClickableViewAccessibility")
    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        mBinding = DataBindingUtil.inflate(inflater, R.layout.new_tab, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity)getContext());

        mBinding.bookmarkAddView.setVisibility(GONE);

        mNewTabAdapter = new NewTabAdapter(mBookmarkItemCallback);
        mBinding.bookmarksList.setAdapter(mNewTabAdapter);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
        mBinding.bookmarksList.setLayoutManager(layoutManager);
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

        mBinding.bookmarkAdd.setOnClickListener(view -> {
            mBinding.bookmarkAddButton.requestFocusFromTouch();
            showAddBookmarkView();
        });

        mBinding.executePendingBindings();

        updateBookmarks();

        setOnTouchListener((v, event) -> {
            v.requestFocusFromTouch();
            return false;
        });

    }

    private void showAddBookmarkView() {
        mBinding.bookmarkAddView.setVisibility(VISIBLE);
        mBinding.main.setAlpha(0.5F);

        mBinding.bookmarkAddCancelButton.setOnClickListener(view -> {
            mBinding.bookmarkAddView.setVisibility(GONE);
            mBinding.main.setAlpha(1F);
        });

        mBinding.bookmarkAddButton.setOnClickListener(view -> {
            String title = mBinding.bookmarkTitle.getText().toString();
            String url = mBinding.bookmarkUrl.getText().toString();

            if (!title.isEmpty() && !url.isEmpty()) {
                mNewTabAdapter.addItem(title, url);
            }

            mBinding.bookmarkAddView.setVisibility(GONE);
            mBinding.main.setAlpha(1F);
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

    private final BookmarkItemCallback mBookmarkItemCallback = new BookmarkItemCallback() {
        @Override
        public void onClick(@NonNull View view, @NonNull Bookmark item) {
            mBinding.bookmarksList.requestFocusFromTouch();

            Session session = SessionStore.get().getActiveSession();
            session.loadUri(item.getUrl());

            WindowWidget window = mWidgetManager.getFocusedWindow();
            window.hideNewTab(true);
        }

        @Override
        public void onDelete(@NonNull View view, @NonNull Bookmark item) {
            mBinding.bookmarksList.requestFocusFromTouch();
            SessionStore.get().getBookmarkStore().deleteBookmarkById(item.getGuid());
            mNewTabAdapter.removeItem(item);
        }

        @Override
        public void onMore(@NonNull View view, @NonNull Bookmark item) {

        }

        @Override
        public void onFolderOpened(@NonNull Bookmark item) {

        }
    };
}