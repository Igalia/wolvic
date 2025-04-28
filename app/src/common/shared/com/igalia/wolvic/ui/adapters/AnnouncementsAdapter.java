package com.igalia.wolvic.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.AnnouncementItemBinding;
import com.igalia.wolvic.utils.Announcement;
import com.igalia.wolvic.utils.LocaleUtils;
import com.igalia.wolvic.utils.RemoteAnnouncements;
import com.igalia.wolvic.utils.StringUtils;
import com.igalia.wolvic.utils.SystemUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AnnouncementsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String LOGTAG = SystemUtils.createLogtag(AnnouncementsAdapter.class);

    // JSON source uses ISO date format ("2025-12-31")
    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    // Convert to local date format (e.g. "31 December 2025")
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.getDefault());

    private final Context mContext;
    private RemoteAnnouncements mRemoteAnnouncements;
    private final List<Announcement> mAnnouncements = new ArrayList<>();
    private ClickListener mListener;

    public interface ClickListener {
        void onClicked(Announcement announcement);
        void onDismissed(Announcement announcement);
    }

    public AnnouncementsAdapter(Context context) {
        mContext = context;
    }

    public void updateAnnouncements(RemoteAnnouncements remoteAnnouncements) {
        mRemoteAnnouncements = remoteAnnouncements;
        mAnnouncements.clear();

        if (remoteAnnouncements != null) {
            mAnnouncements.addAll(remoteAnnouncements.getAnnouncements());
        }

        notifyDataSetChanged();
    }

    public void setClickListener(ClickListener listener) {
        mListener = listener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        AnnouncementItemBinding binding = DataBindingUtil.inflate(
                LayoutInflater.from(mContext),
                R.layout.announcement_item,
                parent,
                false);
        return new AnnouncementViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        AnnouncementViewHolder viewHolder = (AnnouncementViewHolder) holder;
        Announcement announcement = mAnnouncements.get(position);

        String languageCode = LocaleUtils.getDisplayLanguage(mContext).getLocale().getLanguage();

        String title = mRemoteAnnouncements.getAnnouncementTitleForLanguage(announcement, languageCode);
        viewHolder.binding.announcementTitle.setText(title);

        String body = mRemoteAnnouncements.getAnnouncementBodyForLanguage(announcement, languageCode);
        viewHolder.binding.announcementBody.setText(body);

        try {
            LocalDate date = LocalDate.parse(announcement.getDate(), ISO_FORMAT);
            viewHolder.binding.announcementDate.setText(date.format(DISPLAY_FORMAT));
        } catch (DateTimeParseException e) {
            viewHolder.binding.announcementDate.setText(announcement.getDate());
        }

        if (!StringUtils.isEmpty(announcement.getImage())) {
            viewHolder.binding.announcementImage.setImageResource(R.drawable.empty_drawable);
            viewHolder.binding.announcementImage.setVisibility(View.VISIBLE);
            SessionStore.get().getRemoteImageHelper().loadIntoView(
                    viewHolder.binding.announcementImage,
                    announcement.getImage(),
                    false);
        } else {
            viewHolder.binding.announcementImage.setVisibility(View.GONE);
            viewHolder.binding.announcementImage.setImageResource(R.drawable.empty_drawable);
        }

        if (announcement.getLink() == null) {
            viewHolder.binding.linkIcon.setVisibility(View.GONE);
        } else {
            viewHolder.binding.linkIcon.setVisibility(View.VISIBLE);
        }

        viewHolder.binding.setAnnouncement(announcement);
        viewHolder.binding.setListener(mListener);
    }

    @Override
    public int getItemCount() {
        return mAnnouncements.size();
    }

    @Override public long getItemId(int position) {
        return mAnnouncements.get(position).getId().hashCode();
    }

    static class AnnouncementViewHolder extends RecyclerView.ViewHolder {
        final AnnouncementItemBinding binding;

        AnnouncementViewHolder(AnnouncementItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}