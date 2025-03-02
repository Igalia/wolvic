package com.igalia.wolvic.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.igalia.wolvic.R;
import com.igalia.wolvic.utils.Experience;

import java.util.ArrayList;
import java.util.List;

public class ExperiencesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final Context mContext;
    private final List<Experience> mExperiences = new ArrayList<>();
    private String mThumbnailRoot;
    private OnExperienceClickListener mListener;

    public interface OnExperienceClickListener {
        void onExperienceClicked(Experience experience);
    }

    public ExperiencesAdapter(Context context) {
        mContext = context;
    }

    public void setData(List<Experience> experiences, String thumbnailRoot) {
        mExperiences.clear();
        mThumbnailRoot = thumbnailRoot;

        if (experiences != null) {
            mExperiences.addAll(experiences);
        }

        notifyDataSetChanged();
    }

    public void setOnExperienceClickListener(OnExperienceClickListener listener) {
        mListener = listener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.experience_item, parent, false);
        return new ExperienceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ExperienceViewHolder viewHolder = (ExperienceViewHolder) holder;
        Experience experience = mExperiences.get(position);

        viewHolder.titleText.setText(experience.getTitle());

        // Load thumbnail with Glide
        String fullThumbnailUrl = mThumbnailRoot + experience.getThumbnail();
        Glide.with(mContext)
                .load(fullThumbnailUrl)
                .placeholder(R.drawable.placeholder_image)
                .error(R.drawable.error_image)
                .into(viewHolder.thumbnailImage);

        viewHolder.itemView.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onExperienceClicked(experience);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mExperiences.size();
    }

    static class ExperienceViewHolder extends RecyclerView.ViewHolder {
        final ImageView thumbnailImage;
        final TextView titleText;

        ExperienceViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnailImage = itemView.findViewById(R.id.experience_thumbnail);
            titleText = itemView.findViewById(R.id.experience_title);
        }
    }
}