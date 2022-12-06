package com.igalia.wolvic.ui.widgets.prompts;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.audio.AudioEngine;
import com.igalia.wolvic.downloads.Download;
import com.igalia.wolvic.downloads.DownloadsManager;
import com.igalia.wolvic.ui.adapters.FileUploadAdapter;
import com.igalia.wolvic.ui.adapters.FileUploaditem;
import com.igalia.wolvic.ui.callbacks.FileUploadItemCallback;
import com.igalia.wolvic.ui.views.CustomRecyclerView;

import java.util.List;
import java.util.stream.Collectors;

public class FilePromptWidget extends PromptWidget implements DownloadsManager.DownloadsListener {


    public interface FilePromptDelegate extends PromptDelegate {
        void confirm(@NonNull Uri[] uris);
    }

    private static final int DIALOG_CLOSE_DELAY = 250;
    private static final int LISTVIEW_ITEM_HEIGHT = 20;

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

        mFileUploadAdapter = new FileUploadAdapter(mFileUploadItemCallback);
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
            // TODO upload the file
            mPromptDelegate.dismiss();
            hide(REMOVE_WIDGET);
        });
    }

    private final FileUploadItemCallback mFileUploadItemCallback = new FileUploadItemCallback() {
        @Override
        public void onClick(@NonNull View view, @NonNull FileUploaditem item) {
            mFilesList.requestFocusFromTouch();

            ((FilePromptDelegate) mPromptDelegate).confirm(new Uri[]{item.getUri()});

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

    private List<FileUploaditem> getFileItemsFromDownloads(@NonNull List<Download> downloads) {
        return downloads.
                stream().
                filter(download -> download.getStatus() == Download.SUCCESSFUL).
                map(download -> new FileUploaditem(download.getFilename(), Uri.parse(download.getOutputFileUri()), download.getSizeBytes())).
                collect(Collectors.toList());
    }

    public void onDownloadsUpdate(@NonNull List<Download> downloads) {
        List<FileUploaditem> fileItems = getFileItemsFromDownloads(downloads);
        mFileUploadAdapter.setFilesList(fileItems);
    }

    public void onDownloadCompleted(@NonNull Download download) {
        List<FileUploaditem> fileItems = getFileItemsFromDownloads(mDownloadsManager.getDownloads());
        mFileUploadAdapter.setFilesList(fileItems);
    }

    public void onDownloadError(@NonNull String error, @NonNull String file) {
        List<FileUploaditem> fileItems = getFileItemsFromDownloads(mDownloadsManager.getDownloads());
        mFileUploadAdapter.setFilesList(fileItems);
    }
}
