package org.mozilla.vrbrowser.ui.adapters;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.mozilla.gecko.util.ThreadUtils;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.databinding.LanguageItemBinding;
import org.mozilla.vrbrowser.ui.callbacks.LanguageItemCallback;
import org.mozilla.vrbrowser.utils.ViewUtils;

import java.util.Collections;
import java.util.List;

public class LanguagesAdapter extends RecyclerView.Adapter<LanguagesAdapter.LanguageViewHolder> {

    private static final int ICON_ANIMATION_DURATION = 200;

    private List<Language> mLanguagesList;
    private boolean mIsPreferred;

    private int mIconColorHover;
    private int mIconNormalColor;
    private int mIconSize;
    private int mMaxIconSize;

    @Nullable
    private final LanguageItemCallback mLanguageItemCallback;

    public LanguagesAdapter(@NonNull Context context, @Nullable LanguageItemCallback clickCallback, boolean isPreferred) {
        mLanguageItemCallback = clickCallback;
        mIsPreferred = isPreferred;

        mIconSize = (int)context.getResources().getDimension(R.dimen.language_row_icon_size);
        mMaxIconSize = mIconSize + ((mIconSize*25)/100);

        mIconColorHover = context.getResources().getColor(R.color.smoke, context.getTheme());
        mIconNormalColor = context.getResources().getColor(R.color.concrete, context.getTheme());

        setHasStableIds(true);
    }

    public void setLanguageList(final List<Language> languagesList) {
        // Ideally we would use the DiffTools here as we do in the Bookmarks adapter but as we are
        // using elements from the shared local languages list from LocaleUtils and get get the
        // preferred and available Language items from the global list, the diff is always void.
        // We save some memory though.
        mLanguagesList = languagesList;
        notifyItemRangeInserted(0, languagesList.size());
        notifyDataSetChanged();
    }

    public void addItem(Language language) {
        mLanguagesList.add(0, language);
        notifyItemInserted(mLanguagesList.indexOf(language));
        // This shouldn't be necessary but for some reason the last list item is not refreshed
        // if we don't do a full refresh. Might be another RecyclerView bug.
        ThreadUtils.postToUiThread(() -> notifyDataSetChanged());
    }

    public void addItemAlphabetical(Language language) {
        int index = Collections.binarySearch(mLanguagesList, language,
                (ob1, ob2) -> ob1.getName().compareToIgnoreCase(ob2.getName()));

        if (index < 0) {
            index = (index * -1) - 1;
        }

        mLanguagesList.add(index, language);
        notifyItemInserted(index);
    }

    public void removeItem(Language language) {
        int position = mLanguagesList.indexOf(language);
        if (position >= 0) {
            mLanguagesList.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void moveItemUp(View view, Language language) {
        int position = mLanguagesList.indexOf(language);
        if (position > 0) {
            Collections.swap(mLanguagesList, position, position - 1);
            view.startAnimation(AnimationUtils.loadAnimation(view.getContext(), R.anim.button_click_scale));
            notifyItemRangeChanged(position - 1, 2);
        }
    }

    public void moveItemDown(View view, Language language) {
        int position = mLanguagesList.indexOf(language);
        if (position < mLanguagesList.size()-1) {
            Collections.swap(mLanguagesList, position, position + 1);
            view.startAnimation(AnimationUtils.loadAnimation(view.getContext(), R.anim.button_click_scale));
            notifyItemRangeChanged(position, 2);
        }
    }

    public void onAdd(Language language) {
        if (mIsPreferred) {
            addItem(language);

        } else {
            language.setPreferred(true);
        }

        notifyDataSetChanged();
    }

    public void onRemove(Language language) {
        if (mIsPreferred) {
            removeItem(language);

        } else {
            language.setPreferred(false);
        }

        notifyDataSetChanged();
    }

    public List<Language> getItems() {
        return mLanguagesList;
    }

    @Override
    public LanguageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LanguageItemBinding binding = DataBindingUtil
                .inflate(LayoutInflater.from(parent.getContext()), R.layout.language_item,
                        parent, false);
        binding.add.setOnHoverListener(mIconHoverListener);
        binding.delete.setOnHoverListener(mIconHoverListener);
        binding.up.setOnHoverListener(mIconHoverListener);
        binding.down.setOnHoverListener(mIconHoverListener);
        binding.setIsPreferred(mIsPreferred);

        return new LanguageViewHolder(binding);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull LanguageViewHolder holder, int position) {
        Language language = mLanguagesList.get(position);
        holder.binding.setLanguage(language);
        holder.binding.setIsFirst(position == 0);
        holder.binding.setIsLast(position == mLanguagesList.size()-1);
        // We can't use duplicateParentState to change the state drawables of child views if they
        // handling click events as when they get focus they stop propagating their own state changes
        // so we use duplicateParentState but we handle the events here for add/remove/moveup/movedown.
        holder.binding.layout.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (holder.binding.up.getVisibility() == View.VISIBLE &&
                        ViewUtils.isInsideView(holder.binding.up, (int)event.getRawX(), (int)event.getRawY())) {
                    mLanguageItemCallback.onMoveUp(holder.binding.up, language);

                } else if (holder.binding.down.getVisibility() == View.VISIBLE &&
                    ViewUtils.isInsideView(holder.binding.down, (int)event.getRawX(), (int)event.getRawY())) {
                    mLanguageItemCallback.onMoveDown(holder.binding.down, language);

                } else if (holder.binding.add.getVisibility() == View.VISIBLE &&
                        ViewUtils.isInsideView(holder.binding.add, (int)event.getRawX(), (int)event.getRawY())) {
                    if (!language.isPreferred()) {
                        mLanguageItemCallback.onAdd(holder.binding.add, language);
                    }

                } else if (holder.binding.delete.getVisibility() == View.VISIBLE &&
                        ViewUtils.isInsideView(holder.binding.delete, (int)event.getRawX(), (int)event.getRawY())) {
                    if (language.isPreferred()) {
                        mLanguageItemCallback.onRemove(holder.binding.delete, language);
                    }
                }

            }
            return false;
        });
        holder.binding.executePendingBindings();
    }

    @Override
    public int getItemCount() {
        return mLanguagesList == null ? 0 : mLanguagesList.size();
    }

    static class LanguageViewHolder extends RecyclerView.ViewHolder {

        final LanguageItemBinding binding;

        LanguageViewHolder(@NonNull LanguageItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    @Override
    public long getItemId(int position) {
        return  position;
    }

    private View.OnHoverListener mIconHoverListener = (view, motionEvent) -> {
        ImageView icon = (ImageView)view;
        int ev = motionEvent.getActionMasked();
        switch (ev) {
            case MotionEvent.ACTION_HOVER_ENTER: {
                icon.setColorFilter(mIconColorHover);
                ValueAnimator anim = ValueAnimator.ofInt(mIconSize, mMaxIconSize);
                anim.addUpdateListener(valueAnimator -> {
                    int val = (Integer) valueAnimator.getAnimatedValue();
                    ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                    layoutParams.width = val;
                    layoutParams.height = val;
                    view.setLayoutParams(layoutParams);
                });
                anim.setDuration(ICON_ANIMATION_DURATION);
                anim.start();

                return false;
            }

            case MotionEvent.ACTION_HOVER_EXIT: {
                ValueAnimator anim = ValueAnimator.ofInt(mMaxIconSize, mIconSize);
                anim.addUpdateListener(valueAnimator -> {
                    int val = (Integer) valueAnimator.getAnimatedValue();
                    ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                    layoutParams.width = val;
                    layoutParams.height = val;
                    view.setLayoutParams(layoutParams);
                });
                anim.setDuration(ICON_ANIMATION_DURATION);
                anim.start();
                icon.setColorFilter(mIconNormalColor);

                return false;
            }
        }

        return false;
    };

}
