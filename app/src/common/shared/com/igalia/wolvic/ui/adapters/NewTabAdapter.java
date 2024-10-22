package com.igalia.wolvic.ui.adapters;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.BookmarkItemBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import mozilla.appservices.places.BookmarkRoot;
import mozilla.components.browser.icons.IconRequest;
import mozilla.components.concept.storage.BookmarkNode;

public class NewTabAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<Bookmark> bookmarkItems;

    public NewTabAdapter() {

    }

    public void setBookmarkListInNewTab(final List<BookmarkNode> bookmarkNodes) {
        if (bookmarkItems == null || bookmarkItems.isEmpty()) {
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
        } else {
            Log.e("New Tab", "Set bookmarks: Second case - not implemented yet");
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        BookmarkItemBinding binding = DataBindingUtil
                .inflate(LayoutInflater.from(parent.getContext()), R.layout.bookmark_item,
                        parent, false);

        /*binding.setCallback(mBookmarkItemCallback);
        binding.setIsHovered(false);
        binding.setIsNarrow(mIsNarrowLayout);*/

        return new BookmarkAdapter.BookmarkViewHolder(binding);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Bookmark item = bookmarkItems.get(position);

        BookmarkAdapter.BookmarkViewHolder bookmarkHolder = (BookmarkAdapter.BookmarkViewHolder) holder;
        BookmarkItemBinding binding = bookmarkHolder.binding;
        binding.setItem(item);
        //binding.setIsNarrow(mIsNarrowLayout);

        // Load favicon
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
                    binding.more.setImageState(new int[]{android.R.attr.state_active},false);
                    binding.trash.setImageState(new int[]{android.R.attr.state_active},false);
                    binding.setIsHovered(true);
                    return false;

                case MotionEvent.ACTION_CANCEL:
                    binding.setIsHovered(false);
                    return false;
            }
            return false;
        });
        //binding.more.setOnHoverListener(mIconHoverListener);
        binding.more.setOnTouchListener((view, motionEvent) -> {
            binding.setIsHovered(true);
            int ev = motionEvent.getActionMasked();
            switch (ev) {
                case MotionEvent.ACTION_UP:
                    /*if (mBookmarkItemCallback != null) {
                        mBookmarkItemCallback.onMore(view, binding.getItem());
                    }*/
                    binding.more.setImageState(new int[]{android.R.attr.state_active},true);
                    return true;

                case MotionEvent.ACTION_DOWN:
                    binding.more.setImageState(new int[]{android.R.attr.state_pressed},true);
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    binding.setIsHovered(false);
                    binding.more.setImageState(new int[]{android.R.attr.state_active},true);
                    return false;
            }
            return false;
        });
        //binding.trash.setOnHoverListener(mIconHoverListener);
        binding.trash.setOnTouchListener((view, motionEvent) -> {
            binding.setIsHovered(true);
            int ev = motionEvent.getActionMasked();
            switch (ev) {
                case MotionEvent.ACTION_UP:
                    /*if (mBookmarkItemCallback != null) {
                        mBookmarkItemCallback.onDelete(view, binding.getItem());
                    }*/
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

}