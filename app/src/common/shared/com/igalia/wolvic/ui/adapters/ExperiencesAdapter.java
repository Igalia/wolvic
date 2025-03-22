package com.igalia.wolvic.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.ExperienceHeaderItemBinding;
import com.igalia.wolvic.databinding.ExperienceItemBinding;
import com.igalia.wolvic.utils.Experience;
import com.igalia.wolvic.utils.LocaleUtils;
import com.igalia.wolvic.utils.RemoteExperiences;
import com.igalia.wolvic.utils.SystemUtils;

import java.util.ArrayList;
import java.util.List;

public class ExperiencesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String LOGTAG = SystemUtils.createLogtag(ExperiencesAdapter.class);

    private final Context mContext;
    private final List<ExperienceItem> mExperiences = new ArrayList<>();
    private ClickListener mListener;

    // Encapsulate the two types of items in this collection: headers and individual experiences.
    @IntDef({TYPE_HEADER, TYPE_EXPERIENCE})
    public @interface ItemType {}
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_EXPERIENCE = 1;

    private static class ExperienceItem {
        @ItemType
        private final int type;
        private final String headerTitle;
        private final Experience experience;

        ExperienceItem(String headerText) {
            type = TYPE_HEADER;
            headerTitle = headerText;
            experience = null;
        }

        ExperienceItem(Experience aExperience) {
            type = TYPE_EXPERIENCE;
            headerTitle = null;
            experience = aExperience;
        }
    }

    public interface ClickListener {
        void onClicked(Experience experience);
    }

    public ExperiencesAdapter(Context context) {
        mContext = context;
    }

    public void updateExperiences(RemoteExperiences experiences) {
        mExperiences.clear();

        for (String category : experiences.getCategoryNames()) {
            String categoryName = experiences.getCategoryNameForLanguage(category, LocaleUtils.getDisplayLanguage(mContext).getLocale().getLanguage());
            mExperiences.add(new ExperienceItem(categoryName));

            for (Experience experience : experiences.getExperiencesForCategory(category)) {
                mExperiences.add(new ExperienceItem(experience));
            }
        }

        notifyDataSetChanged();
    }

    public void setClickListener(ClickListener listener) {
        mListener = listener;
    }

    @ItemType
    @Override
    public int getItemViewType(int position) {
        return mExperiences.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            ExperienceHeaderItemBinding binding = DataBindingUtil.inflate(
                    LayoutInflater.from(mContext), R.layout.experience_header_item, parent, false);
            return new HeaderViewHolder(binding);
        } else {
            ExperienceItemBinding binding = DataBindingUtil.inflate(
                    LayoutInflater.from(mContext), R.layout.experience_item, parent, false);
            return new ExperienceViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ExperienceItem item = mExperiences.get(position);

        if (holder instanceof HeaderViewHolder) {
            HeaderViewHolder viewHolder = (HeaderViewHolder) holder;
            viewHolder.binding.setTitle(item.headerTitle);
            viewHolder.binding.executePendingBindings();
        } else if (holder instanceof ExperienceViewHolder) {
            ExperienceViewHolder viewHolder = (ExperienceViewHolder) holder;
            viewHolder.binding.setExperience(item.experience);
            viewHolder.binding.setListener(mListener);

            SessionStore.get().getRemoteImageHelper().loadIntoView(
                    viewHolder.binding.thumbnail, item.experience.getThumbnail(), false);
        }
    }

    @Override
    public int getItemCount() {
        return mExperiences.size();
    }

    // Header items span the whole width of the grid.
    public GridLayoutManager.SpanSizeLookup getSpanSizeLookup(final int totalSpanCount) {
        return new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return getItemViewType(position) == TYPE_HEADER ? totalSpanCount : 1;
            }
        };
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        final ExperienceHeaderItemBinding binding;

        HeaderViewHolder(ExperienceHeaderItemBinding aBinding) {
            super(aBinding.getRoot());
            binding = aBinding;
        }
    }

    static class ExperienceViewHolder extends RecyclerView.ViewHolder {
        final ExperienceItemBinding binding;

        ExperienceViewHolder(ExperienceItemBinding aBinding) {
            super(aBinding.getRoot());
            binding = aBinding;
        }
    }
}