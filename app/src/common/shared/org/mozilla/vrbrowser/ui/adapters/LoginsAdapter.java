package org.mozilla.vrbrowser.ui.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.databinding.OptionsSavedLoginItemBinding;
import org.mozilla.vrbrowser.databinding.PromptSelectLoginItemBinding;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.AnimationHelper;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.util.List;
import java.util.Objects;

import mozilla.components.concept.storage.Login;

public class LoginsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    static final String LOGTAG = SystemUtils.createLogtag(HistoryAdapter.class);

    private static final int ICON_ANIMATION_DURATION = 200;

    @IntDef(value = { SELECTION_LIST, SAVED_LOGINS_LIST})
    public @interface Type {}
    public static final int SELECTION_LIST = 0;
    public static final int SAVED_LOGINS_LIST = 1;

    public interface Delegate {
        default void onLoginSelected(@NonNull View view, @NonNull Login login) {}
        default void onLoginDeleted(@NonNull View view, @NonNull Login login) {}
    }

    private List<Login> mItemsList;
    private Delegate mDelegate;
    private @Type int mType;
    private int mMinPadding;
    private int mMaxPadding;

    public LoginsAdapter(@Nullable Context aContext, @NonNull Delegate delegate, @Type int type) {
        mDelegate = delegate;
        mType = type;

        mMinPadding = WidgetPlacement.pixelDimension(aContext, R.dimen.library_icon_padding_min);
        mMaxPadding = WidgetPlacement.pixelDimension(aContext, R.dimen.library_icon_padding_max);

        setHasStableIds(true);
    }

    public void setItems(final List<Login> items) {
        if (mItemsList == null) {
            mItemsList = items;
            notifyItemRangeInserted(0, items.size());

        } else {
            DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return mItemsList.size();
                }

                @Override
                public int getNewListSize() {
                    return items.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    return mItemsList.get(oldItemPosition).hashCode() == items.get(newItemPosition).hashCode();
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    Login newHistoryItem = items.get(newItemPosition);
                    Login oldHistoryItem = mItemsList.get(oldItemPosition);
                    return Objects.equals(newHistoryItem.getGuid(), oldHistoryItem.getGuid()) &&
                            Objects.equals(newHistoryItem.getFormActionOrigin(), oldHistoryItem.getFormActionOrigin()) &&
                            Objects.equals(newHistoryItem.getHttpRealm(), oldHistoryItem.getHttpRealm()) &&
                            Objects.equals(newHistoryItem.getOrigin(), oldHistoryItem.getOrigin()) &&
                            Objects.equals(newHistoryItem.getPassword(), oldHistoryItem.getPassword()) &&
                            Objects.equals(newHistoryItem.getPasswordField(), oldHistoryItem.getPasswordField()) &&
                            Objects.equals(newHistoryItem.getTimeCreated(), oldHistoryItem.getTimeCreated()) &&
                            Objects.equals(newHistoryItem.getTimePasswordChanged(), oldHistoryItem.getTimePasswordChanged()) &&
                            Objects.equals(newHistoryItem.getTimesUsed(), oldHistoryItem.getTimesUsed()) &&
                            Objects.equals(newHistoryItem.getUsername(), oldHistoryItem.getUsername()) &&
                            Objects.equals(newHistoryItem.getUsernameField(), oldHistoryItem.getUsernameField()) &&
                            Objects.equals(newHistoryItem.getTimeLastUsed(), oldHistoryItem.getTimeLastUsed());
                }
            });

            mItemsList = items;
            result.dispatchUpdatesTo(this);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (mType) {
            case SELECTION_LIST: {
                PromptSelectLoginItemBinding binding = DataBindingUtil
                        .inflate(LayoutInflater.from(parent.getContext()), R.layout.prompt_select_login_item,
                                parent, false);
                binding.setDelegate(mDelegate);

                return new PromptSelectLoginItemHolder(binding);
            }
            case SAVED_LOGINS_LIST: {
                OptionsSavedLoginItemBinding binding = DataBindingUtil
                        .inflate(LayoutInflater.from(parent.getContext()), R.layout.options_saved_login_item,
                                parent, false);
                binding.setDelegate(mDelegate);
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
                            if (mDelegate != null) {
                                mDelegate.onLoginDeleted(view, binding.getLogin());
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

                return new PromptSavedLoginItemHolder(binding);
            }
        }

        throw new UnsupportedOperationException("Unsopported view type: " + mType);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Login item = mItemsList.get(position);

        if (holder instanceof PromptSelectLoginItemHolder) {
            PromptSelectLoginItemHolder viewHolder = (PromptSelectLoginItemHolder) holder;
            PromptSelectLoginItemBinding binding = viewHolder.binding;
            binding.setLogin(item);

        } else if (holder instanceof PromptSavedLoginItemHolder) {
            PromptSavedLoginItemHolder viewHolder = (PromptSavedLoginItemHolder) holder;
            OptionsSavedLoginItemBinding binding = viewHolder.binding;
            binding.setLogin(item);
        }
    }

    @Override
    public int getItemCount() {
        return mItemsList == null ? 0 : mItemsList.size();
    }

    @Override
    public int getItemViewType(int position) {
        return mType;
    }

    @Override
    public long getItemId(int position) {
        Login item = mItemsList.get(position);
        return item.hashCode();
    }

    private View.OnHoverListener mIconHoverListener = (view, motionEvent) -> {
        ImageView icon = (ImageView)view;
        int ev = motionEvent.getActionMasked();
        switch (ev) {
            case MotionEvent.ACTION_HOVER_ENTER:
                icon.setImageState(new int[]{android.R.attr.state_hovered},true);
                AnimationHelper.animateViewPadding(view,
                        mMaxPadding,
                        mMinPadding,
                        ICON_ANIMATION_DURATION);
                return false;

            case MotionEvent.ACTION_HOVER_EXIT:
                icon.setImageState(new int[]{android.R.attr.state_active},true);
                AnimationHelper.animateViewPadding(view,
                        mMinPadding,
                        mMaxPadding,
                        ICON_ANIMATION_DURATION);
                return false;
        }

        return false;
    };

    static class PromptSelectLoginItemHolder extends RecyclerView.ViewHolder {

        final PromptSelectLoginItemBinding binding;

        PromptSelectLoginItemHolder(@NonNull PromptSelectLoginItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class PromptSavedLoginItemHolder extends RecyclerView.ViewHolder {

        final OptionsSavedLoginItemBinding binding;

        PromptSavedLoginItemHolder(@NonNull OptionsSavedLoginItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

}
