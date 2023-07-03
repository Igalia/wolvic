package com.igalia.wolvic.ui.widgets.prompts;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.audio.AudioEngine;
import com.igalia.wolvic.databinding.PromptFileBinding;
import com.igalia.wolvic.downloads.Download;
import com.igalia.wolvic.downloads.DownloadsManager;
import com.igalia.wolvic.ui.adapters.FileUploadAdapter;
import com.igalia.wolvic.ui.adapters.FileUploadItem;
import com.igalia.wolvic.ui.callbacks.FileUploadSelectionCallback;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class FilePromptWidget extends PromptWidget implements DownloadsManager.DownloadsListener {


    public interface FilePromptDelegate extends PromptDelegate {
        void confirm(@NonNull Uri[] uris);
    }

    private AudioEngine mAudio;
    private PromptFileBinding mBinding;
    private FileUploadAdapter mFileUploadAdapter;
    private DownloadsManager mDownloadsManager;

    public FilePromptWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public FilePromptWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public FilePromptWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    protected void initialize(Context aContext) {
        mDownloadsManager = ((VRBrowserActivity) getContext()).getServicesProvider().getDownloadsManager();
        mAudio = AudioEngine.fromContext(aContext);

        LayoutInflater inflater = LayoutInflater.from(aContext);
        mBinding = DataBindingUtil.inflate(inflater, R.layout.prompt_file, this, true);

        mLayout = mBinding.layout;

        mFileUploadAdapter = new FileUploadAdapter(mOnSelectionCallback);
        mBinding.filesList.setAdapter(mFileUploadAdapter);
        mBinding.filesList.setHasFixedSize(true);
        mBinding.filesList.setItemViewCacheSize(20);
        // TODO: This method was deprecated in API level 28.
        //  The view drawing cache was largely made obsolete with the introduction of
        //  hardware-accelerated rendering in API 11. With hardware-acceleration, intermediate
        //  cache layers are largely unnecessary and can easily result in a net loss in performance
        //  due to the cost of creating and updating the layer. In the rare cases where caching
        //  layers are useful, such as for alpha animations,
        //  setLayerType(int, android.graphics.Paint) handles this with hardware rendering.
        //  For software-rendered snapshots of a small part of the View hierarchy or individual
        //  Views it is recommended to create a Canvas from either a Bitmap or Picture and call
        //  draw(android.graphics.Canvas) on the View. However these software-rendered usages are
        //  discouraged and have compatibility issues with hardware-only rendering features such
        //  as Config.HARDWARE bitmaps, real-time shadows, and outline clipping. For screenshots of
        //  the UI for feedback reports or unit testing the PixelCopy API is recommended.
        mBinding.filesList.setDrawingCacheEnabled(true);
        mBinding.filesList.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

        onDownloadsUpdate(mDownloadsManager.getDownloads());

        mTitle = findViewById(R.id.promptTitle);
        mMessage = findViewById(R.id.promptMessage);

        mBinding.negativeButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            mPromptDelegate.dismiss();
            hide(REMOVE_WIDGET);
        });

        mBinding.positiveButton.setOnClickListener(view -> {
            if (mPromptDelegate instanceof FilePromptDelegate) {
                Collection<FileUploadItem> selectedItems = mFileUploadAdapter.getSelectedItems();
                if (selectedItems.size() > 0) {
                    Uri[] selectedUris = selectedItems.stream().map(FileUploadItem::getUri).toArray(Uri[]::new);
                    ((FilePromptDelegate) mPromptDelegate).confirm(selectedUris);
                } else {
                    mPromptDelegate.dismiss();
                }
            } else {
                Log.w(LOGTAG, "Prompt delegate is not an instance of FilePromptDelegate");
                mPromptDelegate.dismiss();
            }
            hide(REMOVE_WIDGET);
        });
        // hidden unless multiple selection is enabled
        mBinding.positiveButton.setVisibility(GONE);
    }

    public void setIsMultipleSelection(boolean isMultipleSelection) {
        mFileUploadAdapter.setIsMultipleSelection(isMultipleSelection);
        mBinding.positiveButton.setVisibility(isMultipleSelection ? VISIBLE : GONE);

        onDownloadsUpdate(mDownloadsManager.getDownloads());
    }

    public void setMimeTypes(String[] mimeTypes) {
        mFileUploadAdapter.setMimeTypes(mimeTypes);

        onDownloadsUpdate(mDownloadsManager.getDownloads());
    }

    private final FileUploadSelectionCallback mOnSelectionCallback = uris -> {
        ((FilePromptDelegate) mPromptDelegate).confirm(uris);
        hide(REMOVE_WIDGET);
    };

    @Override
    public void show(@ShowFlags int aShowFlags) {
        super.show(aShowFlags);

        onDownloadsUpdate(mDownloadsManager.getDownloads());
    }

    @Override
    public void hide(int aHideFlags) {
        super.hide(aHideFlags);

        mDownloadsManager.removeListener(this);
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        super.initializeWidgetPlacement(aPlacement);
        aPlacement.width = WidgetPlacement.dpDimension(getContext(), R.dimen.prompt_file_width);
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.prompt_file_height);
    }

    private List<FileUploadItem> getFileItemsFromDownloads(@NonNull List<Download> downloads) {
        return downloads.
                stream().
                filter(download -> download.getStatus() == Download.SUCCESSFUL).
                map(download -> new FileUploadItem(
                        download.getFilename(),
                        download.getOutputFileUri(),
                        download.getMediaType(),
                        download.getSizeBytes())).
                collect(Collectors.toList());
    }

    public void onDownloadsUpdate(@NonNull List<Download> downloads) {
        List<FileUploadItem> fileItems = getFileItemsFromDownloads(downloads);
        mFileUploadAdapter.setFilesList(fileItems);
        mBinding.setIsEmpty(mFileUploadAdapter.getItemCount() == 0);
    }

    public void onDownloadCompleted(@NonNull Download download) {
        List<FileUploadItem> fileItems = getFileItemsFromDownloads(mDownloadsManager.getDownloads());
        mFileUploadAdapter.setFilesList(fileItems);
        mBinding.setIsEmpty(mFileUploadAdapter.getItemCount() == 0);
    }

    public void onDownloadError(@NonNull String error, @NonNull String file) {
        List<FileUploadItem> fileItems = getFileItemsFromDownloads(mDownloadsManager.getDownloads());
        mFileUploadAdapter.setFilesList(fileItems);
        mBinding.setIsEmpty(mFileUploadAdapter.getItemCount() == 0);
    }
}
