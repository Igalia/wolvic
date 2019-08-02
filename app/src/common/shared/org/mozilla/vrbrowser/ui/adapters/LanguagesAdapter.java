package org.mozilla.vrbrowser.ui.adapters;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.databinding.LanguageItemBinding;
import org.mozilla.vrbrowser.ui.callbacks.LanguageItemCallback;

import java.util.Collections;
import java.util.List;

public class LanguagesAdapter extends RecyclerView.Adapter<LanguagesAdapter.LanguageViewHolder> {

    private List<Language> mLanguagesList;
    private Language mDefaultLanguage;
    private boolean mIsPreferred;

    @Nullable
    private final LanguageItemCallback mLanguageItemCallback;

    public LanguagesAdapter(@Nullable LanguageItemCallback clickCallback) {
        mLanguageItemCallback = clickCallback;

        setHasStableIds(true);
    }

    public void setLanguageList(final List<Language> languagesList) {
        if (mLanguagesList == null) {
            mLanguagesList = languagesList;
            notifyItemRangeInserted(0, languagesList.size());

        } else {
            DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return mLanguagesList.size();
                }

                @Override
                public int getNewListSize() {
                    return languagesList.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    return mLanguagesList.get(oldItemPosition).getId().equals(languagesList.get(newItemPosition).getId());
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    Language newBookmark = languagesList.get(newItemPosition);
                    Language oldBookmark = mLanguagesList.get(oldItemPosition);
                    return newBookmark.getId().equals(oldBookmark.getId());
                }
            });

            mLanguagesList = languagesList;
            result.dispatchUpdatesTo(this);
        }
    }

    public void addItem(Language language) {
        mLanguagesList.add(0, language);
        notifyDataSetChanged();
    }

    public void addItemAlphabetical(Language language) {
        int index = Collections.binarySearch(mLanguagesList, language,
                (ob1, ob2) -> ob1.getName().compareToIgnoreCase(ob2.getName()));

        if (index < 0) {
            index = (index * -1) - 1;
        }

        mLanguagesList.add(index, language);
        notifyDataSetChanged();
    }

    public void removeItem(Language language) {
        int position = mLanguagesList.indexOf(language);
        if (position >= 0) {
            mLanguagesList.remove(position);
            notifyDataSetChanged();
        }
    }

    public void moveItemUp(View view, Language language) {
        int position = mLanguagesList.indexOf(language);
        if (position > 0) {
            Collections.swap(mLanguagesList, position, position - 1);
            view.startAnimation(AnimationUtils.loadAnimation(view.getContext(), R.anim.button_click_scale));
            notifyDataSetChanged();
        }
    }

    public void moveItemDown(View view, Language language) {
        int position = mLanguagesList.indexOf(language);
        if (position < mLanguagesList.size()-1) {
            Collections.swap(mLanguagesList, position, position + 1);
            view.startAnimation(AnimationUtils.loadAnimation(view.getContext(), R.anim.button_click_scale));
            notifyDataSetChanged();
        }
    }

    public void onCLick(Language language) {
        if (mLanguagesList.indexOf(language) < 0) {
            if (mIsPreferred)
                addItem(language);
            else
                addItemAlphabetical(language);

        } else {
            removeItem(language);
        }
    }

    public void setPreferred(Language language) {
        mIsPreferred = true;
        mDefaultLanguage = language;
    }

    public List<Language> getItems() {
        return mLanguagesList;
    }

    @Override
    public LanguageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LanguageItemBinding binding = DataBindingUtil
                .inflate(LayoutInflater.from(parent.getContext()), R.layout.language_item,
                        parent, false);
        binding.setCallback(mLanguageItemCallback);
        binding.setIsPreferred(mIsPreferred);

        return new LanguageViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull LanguageViewHolder holder, int position) {
        Language language = mLanguagesList.get(position);
        holder.binding.setLanguage(language);
        holder.binding.setIsFirst(position == 0);
        holder.binding.setIsLast(position == mLanguagesList.size()-1);
        holder.binding.setIsDefault(mIsPreferred ? language.equals(mDefaultLanguage) : false);
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
