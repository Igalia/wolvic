package com.igalia.wolvic.ui.widgets.prompts;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.audio.AudioEngine;
import com.igalia.wolvic.downloads.Download;
import com.igalia.wolvic.downloads.DownloadsManager;
import com.igalia.wolvic.ui.adapters.FileUploadAdapter;
import com.igalia.wolvic.ui.adapters.FileUploadItem;
import com.igalia.wolvic.ui.callbacks.FileUploadSelectionCallback;
import com.igalia.wolvic.ui.views.CustomRecyclerView;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class FilePromptWidget extends PromptWidget implements DownloadsManager.DownloadsListener {


    public interface FilePromptDelegate extends PromptDelegate {
        void confirm(@NonNull Uri[] uris);
    }

    private AudioEngine mAudio;
    private CustomRecyclerView mFilesList;
    private Button mCloseButton;
    private Button mUploadButton;
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

        inflate(aContext, R.layout.prompt_file, this);

        mLayout = findViewById(R.id.layout);

        mFileUploadAdapter = new FileUploadAdapter(mOnSelectionCallback);
        mFilesList = findViewById(R.id.filesList);
        mFilesList.setAdapter(mFileUploadAdapter);
        mFilesList.setHasFixedSize(true);
        mFilesList.setItemViewCacheSize(20);
        mFilesList.setDrawingCacheEnabled(true);
        mFilesList.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

        onDownloadsUpdate(mDownloadsManager.getDownloads());

        mTitle = findViewById(R.id.promptTitle);
        mMessage = findViewById(R.id.promptMessage);

        mCloseButton = findViewById(R.id.negativeButton);
        mCloseButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            mPromptDelegate.dismiss();
            hide(REMOVE_WIDGET);
        });

        mUploadButton = findViewById(R.id.positiveButton);
        mUploadButton.setOnClickListener(view -> {
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
        mUploadButton.setVisibility(GONE);
    }

    public void setIsMultipleSelection(boolean isMultipleSelection) {
        mFileUploadAdapter.setIsMultipleSelection(isMultipleSelection);
        mUploadButton.setVisibility(isMultipleSelection ? VISIBLE : GONE);

        onDownloadsUpdate(mDownloadsManager.getDownloads());
    }

    public void setMimeTypes(String[] mimeTypes) {
        mFileUploadAdapter.setMimeTypes(mimeTypes);

        onDownloadsUpdate(mDownloadsManager.getDownloads());
    }

    private final FileUploadSelectionCallback mOnSelectionCallback = new FileUploadSelectionCallback() {
        @Override
        public void onSelection(@NonNull Uri[] uris) {
            ((FilePromptDelegate) mPromptDelegate).confirm(uris);
            hide(REMOVE_WIDGET);
        }
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
                        Uri.parse(download.getOutputFileUri()),
                        download.getMediaType(),
                        download.getSizeBytes())).
                collect(Collectors.toList());
    }

    public void onDownloadsUpdate(@NonNull List<Download> downloads) {
        List<FileUploadItem> fileItems = getFileItemsFromDownloads(downloads);
        mFileUploadAdapter.setFilesList(fileItems);
    }

    public void onDownloadCompleted(@NonNull Download download) {
        List<FileUploadItem> fileItems = getFileItemsFromDownloads(mDownloadsManager.getDownloads());
        mFileUploadAdapter.setFilesList(fileItems);
    }

    public void onDownloadError(@NonNull String error, @NonNull String file) {
        List<FileUploadItem> fileItems = getFileItemsFromDownloads(mDownloadsManager.getDownloads());
        mFileUploadAdapter.setFilesList(fileItems);
    }
}
