package com.igalia.wolvic.ui.adapters;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.databinding.FileUploadItemBinding;
import com.igalia.wolvic.ui.callbacks.FileUploadItemCallback;
import com.igalia.wolvic.utils.SystemUtils;

import java.util.List;
import java.util.Objects;

public class FileUploadAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    static final String LOGTAG = SystemUtils.createLogtag(FileUploadAdapter.class);

    private List<FileUploaditem> mFilesList;

    @Nullable
    private final FileUploadItemCallback mFileUploadItemCallback;

    public FileUploadAdapter(@Nullable FileUploadItemCallback clickCallback) {
        mFileUploadItemCallback = clickCallback;

        setHasStableIds(true);
    }

    // For the time being, we allow the user to upload downloaded files.
    // TODO upload other files in the system
    public void setFilesList(@NonNull final List<FileUploaditem> filesList) {
        mFilesList = filesList;
        notifyDataSetChanged();
    }

    public int getItemPosition(long id) {
        for (int position = 0; position < mFilesList.size(); position++)
            if (mFilesList.get(position).getId() == id)
                return position;
        return 0;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FileUploadItemBinding binding = DataBindingUtil
                .inflate(LayoutInflater.from(parent.getContext()), R.layout.file_upload_item,
                        parent, false);
        binding.setCallback(mFileUploadItemCallback);

        return new FileUploadViewHolder(binding);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        FileUploaditem item = mFilesList.get(position);
        FileUploadViewHolder itemHolder = (FileUploadViewHolder) holder;
        FileUploadItemBinding binding = itemHolder.binding;

        binding.setItem(item);
        final Uri itemUri = item.getUri();
        ThumbnailAsyncTask task = new ThumbnailAsyncTask(binding.layout.getContext(), item.getUri(),
                bitmap -> {
                    if (binding.getItem() != null && Objects.equals(itemUri, binding.getItem().getUri()))
                        binding.thumbnail.setImageBitmap(bitmap);
                    else
                        binding.thumbnail.setImageResource(R.drawable.ic_generic_file);
                }
        );
        task.execute();

        binding.layout.setOnHoverListener((view, motionEvent) -> {
            int ev = motionEvent.getActionMasked();
            switch (ev) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    view.getBackground().setState(new int[]{android.R.attr.state_hovered});
                    view.postInvalidate();
                    return false;

                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_HOVER_EXIT:
                    view.getBackground().setState(new int[]{android.R.attr.state_active});
                    view.postInvalidate();
                    return false;
            }

            return false;
        });
    }

    @Override
    public int getItemCount() {
        return mFilesList == null ? 0 : mFilesList.size();
    }

    @Override
    public long getItemId(int position) {
        FileUploaditem fileItem = mFilesList.get(position);
        return fileItem.getId();
    }

    static class FileUploadViewHolder extends RecyclerView.ViewHolder {
        final FileUploadItemBinding binding;

        FileUploadViewHolder(@NonNull FileUploadItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
