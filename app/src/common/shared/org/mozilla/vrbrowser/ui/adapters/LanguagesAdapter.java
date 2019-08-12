package org.mozilla.vrbrowser.ui.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.mozilla.gecko.util.ThreadUtils;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.databinding.LanguageItemBinding;
import org.mozilla.vrbrowser.ui.callbacks.LanguageItemCallback;
import org.mozilla.vrbrowser.utils.ViewUtils;

import java.util.Collections;
import java.util.List;

public class LanguagesAdapter extends RecyclerView.Adapter<LanguagesAdapter.LanguageViewHolder> {

    private List<Language> mLanguagesList;
    private boolean mIsPreferred;

    @Nullable
    private final LanguageItemCallback mLanguageItemCallback;

    public LanguagesAdapter(@Nullable LanguageItemCallback clickCallback, boolean isPreferred) {
        mLanguageItemCallback = clickCallback;
        mIsPreferred = isPreferred;

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
                    if (!language.isDefault() && !language.isPreferred())
                        mLanguageItemCallback.onAdd(holder.binding.add, language);

                } else if (holder.binding.delete.getVisibility() == View.VISIBLE &&
                        ViewUtils.isInsideView(holder.binding.delete, (int)event.getRawX(), (int)event.getRawY())) {
                    if (!language.isDefault() && language.isPreferred())
                        mLanguageItemCallback.onRemove(holder.binding.delete, language);
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

    // There seems to be a bug int he RecyclerView adapter that makes it crash sometimes when updating the dataset
    // This seems to be the only thing that works until there is a ew RecyclerView version > 1.0.0
    public static class CustLinearLayoutManager extends LinearLayoutManager {

        public CustLinearLayoutManager(Context context) {
            super(context);
        }

        public CustLinearLayoutManager(Context context, int orientation, boolean reverseLayout) {
            super(context, orientation, reverseLayout);
        }

        public CustLinearLayoutManager(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
            super(context, attrs, defStyleAttr, defStyleRes);
        }

        // Something is happening here

        @Override
        public boolean supportsPredictiveItemAnimations() {
            return false;
        }
    }

}
