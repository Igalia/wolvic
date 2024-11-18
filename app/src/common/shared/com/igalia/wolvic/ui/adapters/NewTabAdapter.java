package com.igalia.wolvic.ui.adapters;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.BookmarkItemInNewTabBinding;
import com.igalia.wolvic.ui.callbacks.BookmarkItemCallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import mozilla.appservices.places.BookmarkRoot;
import mozilla.components.browser.icons.IconRequest;
import mozilla.components.concept.storage.BookmarkNode;

// Initial implementation: Show, add, delete bookmarks in New Tab page.
// TODO: Implement data structure and logics to handle pages in New Tab that are separate from bookmarks.
public class NewTabAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<Bookmark> bookmarkItems;

    @Nullable
    private final BookmarkItemCallback mBookmarkItemCallback;

    public NewTabAdapter(@Nullable BookmarkItemCallback clickCallback) {
        mBookmarkItemCallback = clickCallback;
    }

    public void setBookmarkListInNewTab(final List<BookmarkNode> bookmarkNodes) {
            //bookmarkItems = Bookmark.getBookmarkItems(bookmarkNodes);

            List<Bookmark> newDisplayList;
            bookmarkItems = new ArrayList<>();
            newDisplayList = Bookmark.getDisplayListTree(bookmarkNodes, Collections.singletonList(BookmarkRoot.Mobile.getId()));
            for (Bookmark node : newDisplayList) {
                if (node.getType() == Bookmark.Type.ITEM) {
                    bookmarkItems.add(node);
                }
            }

            notifyItemRangeInserted(0, bookmarkItems.size());
    }

    public void removeItem(Bookmark aBookmark) {
        bookmarkItems.remove(aBookmark);
        notifyDataSetChanged();
    }

    public void addItem(String title, String url) {
        if (!url.startsWith("http://") || !url.startsWith("https://")) {
            url = "https://" + url;
        }
        SessionStore.get().getBookmarkStore().addBookmark(url, title);
        Bookmark item = new Bookmark(title, url);
        bookmarkItems.add(item);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        BookmarkItemInNewTabBinding binding = DataBindingUtil
                .inflate(LayoutInflater.from(parent.getContext()), R.layout.bookmark_item_in_new_tab,
                        parent, false);

        binding.setCallback(mBookmarkItemCallback);
        binding.setIsHovered(false);

        return new BookmarkViewHolder(binding);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Bookmark item = bookmarkItems.get(position);

        BookmarkViewHolder bookmarkHolder = (BookmarkViewHolder) holder;
        BookmarkItemInNewTabBinding binding = bookmarkHolder.binding;
        binding.setItem(item);

        SessionStore.get().getBrowserIcons().loadIntoView(binding.favicon, item.getUrl(), IconRequest.Size.DEFAULT);

        binding.layout.setOnHoverListener((view, motionEvent) -> {
            int ev = motionEvent.getActionMasked();
            switch (ev) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    binding.setIsHovered(true);
                    view.getBackground().setState(new int[]{android.R.attr.state_hovered});
                    view.postInvalidate();
                    return false;

                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_HOVER_EXIT:
                    view.getBackground().setState(new int[]{android.R.attr.state_active});
                    binding.setIsHovered(false);
                    view.postInvalidate();
                    return false;
            }

            return false;
        });
        binding.layout.setOnTouchListener((view, motionEvent) -> {
            int ev = motionEvent.getActionMasked();
            switch (ev) {
                case MotionEvent.ACTION_UP:
                    return false;

                case MotionEvent.ACTION_DOWN:
                    binding.trash.setImageState(new int[]{android.R.attr.state_active},false);
                    binding.setIsHovered(true);
                    return false;

                case MotionEvent.ACTION_CANCEL:
                    binding.setIsHovered(false);
                    return false;
            }
            return false;
        });
        binding.trash.setOnHoverListener(mIconHoverListener);
        binding.trash.setOnTouchListener((view, motionEvent) -> {
            binding.setIsHovered(true);
            int ev = motionEvent.getActionMasked();
            switch (ev) {
                case MotionEvent.ACTION_UP:
                    if (mBookmarkItemCallback != null) {
                        mBookmarkItemCallback.onDelete(view, binding.getItem());
                    }
                    binding.trash.setImageState(new int[]{android.R.attr.state_active},true);
                    return true;

                case MotionEvent.ACTION_DOWN:
                    binding.trash.setImageState(new int[]{android.R.attr.state_pressed},true);
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    binding.setIsHovered(false);
                    binding.trash.setImageState(new int[]{android.R.attr.state_active},true);
                    return false;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return bookmarkItems == null ? 0 : bookmarkItems.size();
    }

    static class BookmarkViewHolder extends RecyclerView.ViewHolder {

        final BookmarkItemInNewTabBinding binding;

        BookmarkViewHolder(@NonNull BookmarkItemInNewTabBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private View.OnHoverListener mIconHoverListener = (view, motionEvent) -> {
        ImageView icon = (ImageView) view;
        int ev = motionEvent.getActionMasked();
        switch (ev) {
            case MotionEvent.ACTION_HOVER_ENTER:
                icon.setImageState(new int[]{android.R.attr.state_hovered}, true);
                return false;

            case MotionEvent.ACTION_HOVER_EXIT:
                icon.setImageState(new int[]{android.R.attr.state_active}, true);
                return false;
        }

        return false;
    };
}