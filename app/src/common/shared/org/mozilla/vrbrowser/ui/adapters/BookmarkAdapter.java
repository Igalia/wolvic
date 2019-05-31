package org.mozilla.vrbrowser.ui.adapters;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.databinding.BookmarkItemBinding;
import org.mozilla.vrbrowser.ui.callbacks.BookmarkClickCallback;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;

import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import mozilla.components.concept.storage.BookmarkNode;

public class BookmarkAdapter extends RecyclerView.Adapter<BookmarkAdapter.BookmarkViewHolder> {
    static final String LOGTAG = "VRB";
    private static final int ICON_ANIMATION_DURATION = 200;

    private List<? extends BookmarkNode> mBookmarkList;

    private int mMinPadding;
    private int mMaxPadding;
    private int mIconColorHover;
    private int mIconNormalColor;

    @Nullable
    private final BookmarkClickCallback mBookmarkClickCallback;

    public BookmarkAdapter(@Nullable BookmarkClickCallback clickCallback, Context aContext) {
        mBookmarkClickCallback = clickCallback;

        mMinPadding = WidgetPlacement.pixelDimension(aContext, R.dimen.tray_icon_padding_min);
        mMaxPadding = WidgetPlacement.pixelDimension(aContext, R.dimen.tray_icon_padding_max);

        mIconColorHover = aContext.getResources().getColor(R.color.white, aContext.getTheme());
        mIconNormalColor = aContext.getResources().getColor(R.color.rhino, aContext.getTheme());

        setHasStableIds(true);
    }

    public void setBookmarkList(final List<? extends BookmarkNode> bookmarkList) {
        if (mBookmarkList == null) {
            mBookmarkList = bookmarkList;
            notifyItemRangeInserted(0, bookmarkList.size());

        } else {
            DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return mBookmarkList.size();
                }

                @Override
                public int getNewListSize() {
                    return bookmarkList.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    return mBookmarkList.get(oldItemPosition).getGuid().equals(bookmarkList.get(newItemPosition).getGuid());
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    BookmarkNode newBookmark = bookmarkList.get(newItemPosition);
                    BookmarkNode oldBookmark = mBookmarkList.get(oldItemPosition);
                    return newBookmark.getGuid().equals(oldBookmark.getGuid())
                            && Objects.equals(newBookmark.getTitle(), oldBookmark.getTitle())
                            && Objects.equals(newBookmark.getUrl(), oldBookmark.getUrl());
                }
            });

            mBookmarkList = bookmarkList;
            result.dispatchUpdatesTo(this);
        }
    }

    public void removeItem(BookmarkNode aBookmark) {
        int position = mBookmarkList.indexOf(aBookmark);
        if (position >= 0) {
            mBookmarkList.remove(position);
            notifyItemRemoved(position);
        }
    }

    public int itemCount() {
        return mBookmarkList != null ? mBookmarkList.size() : 0;
    }

    @Override
    public BookmarkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        BookmarkItemBinding binding = DataBindingUtil
                .inflate(LayoutInflater.from(parent.getContext()), R.layout.bookmark_item,
                        parent, false);
        binding.setCallback(mBookmarkClickCallback);
        binding.trash.setOnHoverListener(mTrashHoverListener);
        binding.trash.setOnTouchListener((view, motionEvent) -> {
            int ev = motionEvent.getActionMasked();
            switch (ev) {
                case MotionEvent.ACTION_UP:
                    mBookmarkClickCallback.onDelete(binding.getBookmark());
                    return true;

                case MotionEvent.ACTION_DOWN:
                    return true;
            }
            return false;
        });
        return new BookmarkViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull BookmarkViewHolder holder, int position) {
        holder.binding.setBookmark(mBookmarkList.get(position));
        holder.binding.executePendingBindings();
    }

    @Override
    public int getItemCount() {
        return mBookmarkList == null ? 0 : mBookmarkList.size();
    }

    @Override
    public long getItemId(int position) {
        BookmarkNode bookmark = mBookmarkList.get(position);
        return  bookmark.getPosition() != null ? bookmark.getPosition() : RecyclerView.NO_ID;
    }

    static class BookmarkViewHolder extends RecyclerView.ViewHolder {

        final BookmarkItemBinding binding;

        BookmarkViewHolder(BookmarkItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private View.OnHoverListener mTrashHoverListener = (view, motionEvent) -> {
        ImageView icon = (ImageView)view;
        int ev = motionEvent.getActionMasked();
        switch (ev) {
            case MotionEvent.ACTION_HOVER_ENTER:
                icon.setColorFilter(mIconColorHover);
                animateViewPadding(view,
                        mMaxPadding,
                        mMinPadding,
                        ICON_ANIMATION_DURATION);
                return false;

            case MotionEvent.ACTION_HOVER_EXIT:
                animateViewPadding(view,
                        mMinPadding,
                        mMaxPadding,
                        ICON_ANIMATION_DURATION,
                        () -> icon.setColorFilter(mIconNormalColor));
                return false;
        }

        return false;
    };

    private void animateViewPadding(View view, int paddingStart, int paddingEnd, int duration) {
        animateViewPadding(view, paddingStart, paddingEnd, duration, null);
    }

    private void animateViewPadding(View view, int paddingStart, int paddingEnd, int duration, Runnable onAnimationEnd) {
        ValueAnimator animation = ValueAnimator.ofInt(paddingStart, paddingEnd);
        animation.setDuration(duration);
        animation.setInterpolator(new AccelerateDecelerateInterpolator());
        animation.addUpdateListener(valueAnimator -> {
            try {
                int newPadding = Integer.parseInt(valueAnimator.getAnimatedValue().toString());
                view.setPadding(newPadding, newPadding, newPadding, newPadding);
            } catch (NumberFormatException ex) {
                Log.e(LOGTAG, "Error parsing BookmarkAdapter animation value: " + valueAnimator.getAnimatedValue().toString());
            }
        });
        animation.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

            }

            @Override
            public void onAnimationEnd(Animator animator) {
                if (onAnimationEnd != null) {
                    onAnimationEnd.run();
                }
            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });
        animation.start();
    }

}
