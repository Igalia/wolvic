package com.igalia.wolvic.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.ExperienceItemBinding;
import com.igalia.wolvic.utils.Experience;
import com.igalia.wolvic.utils.SystemUtils;

import java.util.ArrayList;
import java.util.List;

public class ExperiencesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String LOGTAG = SystemUtils.createLogtag(ExperiencesAdapter.class);

    private final Context mContext;
    private final List<Experience> mExperiences = new ArrayList<>();
    private ClickListener mListener;

    public interface ClickListener {
        void onClicked(Experience experience);
    }

    public ExperiencesAdapter(Context context) {
        mContext = context;
    }

    public void updateExperiences(List<Experience> experiences) {
        mExperiences.clear();
        if (experiences != null) {
            mExperiences.addAll(experiences);
        }

        notifyDataSetChanged();
    }

    public void setClickListener(ClickListener listener) {
        mListener = listener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ExperienceItemBinding binding = DataBindingUtil.inflate(LayoutInflater.from(mContext), R.layout.experience_item, parent, false);

        return new ExperienceViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ExperienceViewHolder viewHolder = (ExperienceViewHolder) holder;
        Experience experience = mExperiences.get(position);

        viewHolder.binding.setExperience(experience);
        viewHolder.binding.setListener(mListener);

        SessionStore.get().getRemoteImageHelper().loadIntoView(viewHolder.binding.thumbnail, experience.getThumbnail(), false);
    }

    @Override
    public int getItemCount() {
        return mExperiences.size();
    }

    static class ExperienceViewHolder extends RecyclerView.ViewHolder {
        final ExperienceItemBinding binding;

        ExperienceViewHolder(ExperienceItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}