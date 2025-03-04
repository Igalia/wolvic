package com.igalia.wolvic.ui.adapters;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.ExperienceItemBinding;
import com.igalia.wolvic.utils.Experience;
import com.igalia.wolvic.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;

import mozilla.components.browser.icons.IconRequest;

public class ExperiencesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String LOGTAG = ExperiencesAdapter.class.getSimpleName();

    private final Context mContext;
    private final List<Experience> mExperiences = new ArrayList<>();
    private String mThumbnailRoot;
    private ClickListener mListener;

    public interface ClickListener {
        void onClicked(Experience experience);
    }

    public ExperiencesAdapter(Context context) {
        mContext = context;
    }

    public void updateExperiences(List<Experience> experiences, String thumbnailRoot) {
        mThumbnailRoot = thumbnailRoot;

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
        ExperienceItemBinding binding = DataBindingUtil
                .inflate(LayoutInflater.from(mContext), R.layout.experience_item,
                        parent, false);

        return new ExperienceViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ExperienceViewHolder viewHolder = (ExperienceViewHolder) holder;
        Experience experience = mExperiences.get(position);

        viewHolder.binding.setExperience(experience);

        // Construct the full thumbnail URL and load the image.
        String imageUrl;
        if (StringUtils.isEmpty(mThumbnailRoot)) {
            imageUrl = experience.getThumbnail();
        } else {
            Uri imageUri = Uri.withAppendedPath(Uri.parse(mThumbnailRoot), experience.getThumbnail());
            imageUrl = imageUri.toString();
        }
        SessionStore.get().getBrowserIcons().loadIntoView(viewHolder.binding.thumbnail, imageUrl, IconRequest.Size.LAUNCHER);

        viewHolder.itemView.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onClicked(experience);
            }
        });
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