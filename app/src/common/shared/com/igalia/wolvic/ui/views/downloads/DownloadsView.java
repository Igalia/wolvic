/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.views.downloads;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.DownloadsBinding;
import com.igalia.wolvic.downloads.Download;
import com.igalia.wolvic.downloads.DownloadsManager;
import com.igalia.wolvic.telemetry.TelemetryService;
import com.igalia.wolvic.ui.adapters.DownloadsAdapter;
import com.igalia.wolvic.ui.callbacks.DownloadItemCallback;
import com.igalia.wolvic.ui.callbacks.DownloadsCallback;
import com.igalia.wolvic.ui.callbacks.DownloadsContextMenuCallback;
import com.igalia.wolvic.ui.viewmodel.DownloadsViewModel;
import com.igalia.wolvic.ui.views.library.LibraryPanel;
import com.igalia.wolvic.ui.views.library.LibraryView;
import com.igalia.wolvic.ui.widgets.UIWidget;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.ui.widgets.WindowWidget;
import com.igalia.wolvic.ui.widgets.dialogs.PromptDialogWidget;
import com.igalia.wolvic.ui.widgets.menus.DownloadsContextMenuWidget;
import com.igalia.wolvic.ui.widgets.menus.library.LibraryContextMenuWidget;
import com.igalia.wolvic.ui.widgets.menus.library.SortingContextMenuWidget;
import com.igalia.wolvic.utils.SystemUtils;
import com.igalia.wolvic.browser.extensions.LocalExtension;
import com.igalia.wolvic.utils.UrlUtils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.UUID;

public class DownloadsView extends LibraryView implements DownloadsManager.DownloadsListener {

    private static final String LOGTAG = SystemUtils.createLogtag(DownloadsView.class);

    private DownloadsBinding mBinding;
    private DownloadsAdapter mDownloadsAdapter;
    private DownloadsManager mDownloadsManager;
    private Comparator<Download> mSortingComparator;
    private DownloadsViewModel mViewModel;
    private DownloadsPanel mDownloadsPanel;

    public DownloadsView(Context aContext, @NonNull DownloadsPanel delegate) {
        super(aContext);
        mDownloadsPanel = delegate;
        initialize();
    }

    @Override
    protected void initialize() {
        super.initialize();

        mDownloadsManager = ((VRBrowserActivity) getContext()).getServicesProvider().getDownloadsManager();
        mViewModel = new ViewModelProvider(
                (VRBrowserActivity) getContext(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(((VRBrowserActivity) getContext()).getApplication()))
                .get(DownloadsViewModel.class);

        mSortingComparator = getSorting(SettingsStore.getInstance(getContext()).getDownloadsSortingOrder());

        updateUI();
    }

    @SuppressLint("ClickableViewAccessibility")
    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.downloads, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity) getContext());
        mBinding.setCallback(mDownloadsCallback);
        mBinding.setDownloadsViewModel(mViewModel);
        mDownloadsAdapter = new DownloadsAdapter(mDownloadItemCallback, getContext());
        mBinding.downloadsList.setAdapter(mDownloadsAdapter);
        mBinding.downloadsList.setOnTouchListener((v, event) -> {
            v.requestFocusFromTouch();
            return false;
        });
        mBinding.downloadsList.addOnScrollListener(mScrollListener);
        mBinding.downloadsList.setHasFixedSize(true);
        mBinding.downloadsList.setItemViewCacheSize(20);
        // Drawing Cache is deprecated in API level 28: https://developer.android.com/reference/android/view/View#getDrawingCache().
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            mBinding.downloadsList.setDrawingCacheEnabled(true);
            mBinding.downloadsList.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
        }

        mViewModel.setIsEmpty(true);
        mViewModel.setIsLoading(true);
        mViewModel.setIsNarrow(false);

        onDownloadsUpdate(mDownloadsManager.getDownloads());

        setOnTouchListener((v, event) -> {
            v.requestFocusFromTouch();
            return false;
        });
    }

    @Override
    public void onDestroy() {
        mBinding.downloadsList.removeOnScrollListener(mScrollListener);
    }

    @Override
    public void onShow() {
        mDownloadsManager.addListener(this);
        onDownloadsUpdate(mDownloadsManager.getDownloads());
        updateLayout();
        if (mDownloadsPanel != null) {
            mDownloadsPanel.onViewUpdated(getContext().getString(R.string.downloads_title));
        }
    }

    @Override
    public void onHide() {
        mDownloadsManager.removeListener(this);
    }

    private final DownloadItemCallback mDownloadItemCallback = new DownloadItemCallback() {

        @Override
        public void onClick(@NonNull View view, @NonNull Download item) {
            mBinding.downloadsList.requestFocusFromTouch();

            if (item.getMediaType().equals(UrlUtils.EXTENSION_MIME_TYPE)) {
                if (SettingsStore.getInstance(getContext()).isLocalAddonAllowed()) {
                    mWidgetManager.getFocusedWindow().showConfirmPrompt(
                            getContext().getString(R.string.download_addon_install),
                            item.getFilename(),
                            new String[]{
                                    getContext().getString(R.string.download_addon_install_cancel),
                                    getContext().getString(R.string.download_addon_install_confirm_install),
                            },
                            (index, isChecked) -> {
                                if (index == PromptDialogWidget.POSITIVE) {
                                    LocalExtension.install(
                                            SessionStore.get().getWebExtensionRuntime(),
                                            UUID.randomUUID().toString(),
                                            item.getOutputFileUriAsString(),
                                            ((VRBrowserActivity) getContext()).getServicesProvider().getAddons()
                                    );
                                }
                            }
                    );
                } else {
                    mWidgetManager.getFocusedWindow().showAlert(
                            getContext().getString(R.string.download_addon_install_blocked),
                            getContext().getString(R.string.download_addon_install_blocked_body),
                            null
                    );
                }
            } else {
                SessionStore.get().getActiveSession().loadUri(item.getOutputFileUriAsString());

                WindowWidget window = mWidgetManager.getFocusedWindow();
                window.hideDownloadsPanel();
            }
        }

        @Override
        public void onDelete(@NonNull View view, @NonNull Download item) {
            mWidgetManager.getFocusedWindow().showConfirmPrompt(
                    getContext().getString(R.string.download_delete_file_confirm_title),
                    getContext().getString(R.string.download_delete_file_confirm_body),
                    new String[]{
                            getContext().getString(R.string.download_delete_confirm_cancel),
                            getContext().getString(R.string.download_delete_confirm_delete)
                    },
                    getContext().getString(R.string.download_delete_file_confirm_checkbox),
                    (index, isChecked) -> {
                        if (index == PromptDialogWidget.POSITIVE) {
                            mDownloadsManager.removeDownload(item.getId(), isChecked);
                        }
                    }
            );
        }

        @Override
        public void onMore(@NonNull View view, @NonNull Download item) {
            mBinding.downloadsList.requestFocusFromTouch();

            int rowPosition = mDownloadsAdapter.getItemPosition(item.getId());
            RecyclerView.ViewHolder row = mBinding.downloadsList.findViewHolderForLayoutPosition(rowPosition);
            boolean isLastVisibleItem = false;
            if (mBinding.downloadsList.getLayoutManager() instanceof LinearLayoutManager) {
                LinearLayoutManager layoutManager = (LinearLayoutManager) mBinding.downloadsList.getLayoutManager();
                int lastItem = mDownloadsAdapter.getItemCount();
                if ((rowPosition == layoutManager.findLastVisibleItemPosition() || rowPosition == layoutManager.findLastCompletelyVisibleItemPosition() ||
                        rowPosition == layoutManager.findLastVisibleItemPosition() - 1 || rowPosition == layoutManager.findLastCompletelyVisibleItemPosition() - 1)
                        && ((rowPosition == (lastItem - 1)) && rowPosition > 2)) {
                    isLastVisibleItem = true;
                }
            }

            if (row != null) {
                mBinding.getCallback().onShowContextMenu(
                        row.itemView,
                        item,
                        isLastVisibleItem);
            }
        }
    };

    private DownloadsCallback mDownloadsCallback = new DownloadsCallback() {
        @Override
        public void onDeleteDownloads(@NonNull View view) {
            view.requestFocusFromTouch();
            mWidgetManager.getFocusedWindow().showConfirmPrompt(
                    getContext().getString(R.string.download_delete_all_confirm_title),
                    getContext().getString(R.string.download_delete_all_confirm_body),
                    new String[]{
                            getContext().getString(R.string.download_delete_confirm_cancel),
                            getContext().getString(R.string.download_delete_confirm_delete)
                    },
                    getContext().getString(R.string.download_delete_all_confirm_checkbox),
                    (index, isChecked) -> {
                        if (index == PromptDialogWidget.POSITIVE) {
                            mViewModel.setIsEmpty(true);
                            post(() -> mDownloadsManager.removeAllDownloads(isChecked));
                        }
                    }
            );
            hideContextMenu();
        }

        @Override
        public void onShowContextMenu(@NonNull View view, Download item, boolean isLastVisibleItem) {
            showContextMenu(
                    view,
                    new DownloadsContextMenuWidget(getContext(),
                            new DownloadsContextMenuWidget.DownloadsContextMenuItem(
                                    item.getOutputFileUriAsString(),
                                    item.getTitle(),
                                    item.getId()),
                            mWidgetManager.canOpenNewWindow()),
                    mCallback,
                    isLastVisibleItem);
        }

        @Override
        public void onHideContextMenu(@NonNull View view) {
            hideContextMenu();
        }

        @Override
        public void onShowSortingContextMenu(@NonNull View view) {
            showSortingContextMenu(view);
        }
    };

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        updateLayout();
    }

    @Override
    protected void updateLayout() {
        post(() -> {
            double width = Math.ceil(getWidth() / getContext().getResources().getDisplayMetrics().density);
            boolean isNarrow = width < SettingsStore.WINDOW_WIDTH_DEFAULT;

            if (isNarrow != mViewModel.getIsNarrow().getValue().get()) {
                mDownloadsAdapter.setNarrow(isNarrow);

                mViewModel.setIsNarrow(isNarrow);
                mBinding.executePendingBindings();

                mViewModel.setIsNarrow(isNarrow);
                mBinding.executePendingBindings();

                requestLayout();
            }
        });
    }

    private DownloadsContextMenuCallback mCallback = new DownloadsContextMenuCallback() {

        @Override
        public void onOpenInNewWindowClick(LibraryContextMenuWidget.LibraryContextMenuItem item) {
            mWidgetManager.openNewWindow(item.getUrl());
            hideContextMenu();
        }

        @Override
        public void onOpenInNewTabClick(LibraryContextMenuWidget.LibraryContextMenuItem item) {
            mWidgetManager.openNewTabForeground(item.getUrl());
            TelemetryService.Tabs.openedCounter(TelemetryService.Tabs.TabSource.DOWNLOADS);
            hideContextMenu();
        }

        @Override
        public void onDelete(DownloadsContextMenuWidget.DownloadsContextMenuItem item) {
            mWidgetManager.getFocusedWindow().showConfirmPrompt(
                    getContext().getString(R.string.download_delete_file_confirm_title),
                    getContext().getString(R.string.download_delete_file_confirm_body),
                    new String[]{
                            getContext().getString(R.string.download_delete_confirm_cancel),
                            getContext().getString(R.string.download_delete_confirm_delete)
                    },
                    getContext().getString(R.string.download_delete_file_confirm_checkbox),
                    (index, isChecked) -> {
                        if (index == PromptDialogWidget.POSITIVE) {
                            mDownloadsManager.removeDownload(item.getDownloadsId(), isChecked);
                        }
                    }
            );
            hideContextMenu();
        }
    };

    protected void showSortingContextMenu(@NonNull View view) {
        view.requestFocusFromTouch();

        hideContextMenu();

        WindowWidget window = mWidgetManager.getFocusedWindow();

        float ratio = WidgetPlacement.viewToWidgetRatio(getContext(), window);

        Rect offsetViewBounds = new Rect();
        mDownloadsPanel.getDrawingRect(offsetViewBounds);
        mDownloadsPanel.offsetDescendantRectToMyCoords(view, offsetViewBounds);

        SortingContextMenuWidget menu = new SortingContextMenuWidget(getContext());
        menu.setItemDelegate(item -> {
            mSortingComparator = getSorting(item);
            onDownloadsUpdate(mDownloadsManager.getDownloads());
            mBinding.downloadsList.scrollToPosition(0);
        });
        menu.getPlacement().parentHandle = window.getHandle();

        menu.getPlacement().anchorY = 0.0f;
        PointF position = new PointF(
                (offsetViewBounds.left + view.getWidth()) * ratio,
                -(offsetViewBounds.top) * ratio);
        menu.getPlacement().translationX = position.x - (menu.getWidth() / menu.getPlacement().density);
        menu.getPlacement().translationY = position.y + getResources().getDimension(R.dimen.library_menu_top_margin) / menu.getPlacement().density;
        menu.show(UIWidget.REQUEST_FOCUS);
    }

    private Comparator<Download> getSorting(@SortingContextMenuWidget.Order int order) {
        switch (order) {
            case SortingContextMenuWidget.SORT_FILENAME_AZ:
                return mAZFileNameComparator;
            case SortingContextMenuWidget.SORT_FILENAME_ZA:
                return mZAFilenameComparator;
            case SortingContextMenuWidget.SORT_DATE_ASC:
                return mDownloadDateAscComparator;
            case SortingContextMenuWidget.SORT_DATE_DESC:
                return mDownloadDateDescComparator;
            case SortingContextMenuWidget.SORT_SIZE_ASC:
                return mDownloadSizeAscComparator;
            case SortingContextMenuWidget.SORT_SIZE_DESC:
                return mDownloadSizeDescComparator;
        }

        return mDownloadIdComparator;
    }

    // DownloadsManager.DownloadsListener

    private Comparator<Download> mDownloadIdComparator = (o1, o2) -> (int) (o1.getId() - o2.getId());

    private Comparator<Download> mAZFileNameComparator = (o1, o2) -> {
        int nameDiff = o1.getFilename().compareTo(o2.getFilename());
        if (nameDiff == 0) {
            return mDownloadIdComparator.compare(o1, o2);

        } else {
            return nameDiff;
        }
    };
    private Comparator<Download> mZAFilenameComparator = (o1, o2) -> {
        int nameDiff = o2.getFilename().compareTo(o1.getFilename());
        if (nameDiff == 0) {
            return mDownloadIdComparator.compare(o1, o2);

        } else {
            return nameDiff;
        }
    };
    private Comparator<Download> mDownloadDateAscComparator = (o1, o2) -> mDownloadIdComparator.compare(o1, o2);
    private Comparator<Download> mDownloadDateDescComparator = (o1, o2) -> mDownloadIdComparator.compare(o2, o1);
    private Comparator<Download> mDownloadSizeAscComparator = (o1, o2) -> {
        int sizeDiff = (int) (o1.getSizeBytes() - o2.getSizeBytes());
        if (sizeDiff == 0) {
            return mDownloadIdComparator.compare(o1, o2);

        } else {
            return sizeDiff;
        }
    };
    private Comparator<Download> mDownloadSizeDescComparator = (o1, o2) -> {
        int sizeDiff = (int) (o2.getSizeBytes() - o1.getSizeBytes());
        if (sizeDiff == 0) {
            return mDownloadIdComparator.compare(o1, o2);

        } else {
            return sizeDiff;
        }
    };

    @Override
    public void onDownloadsUpdate(@NonNull List<Download> downloads) {
        if (downloads.size() == 0) {
            mViewModel.setIsEmpty(true);
            mViewModel.setIsLoading(false);

        } else {
            mViewModel.setIsEmpty(false);
            mViewModel.setIsLoading(false);
            List<Download> sorted = downloads.stream().sorted(mSortingComparator).collect(Collectors.toList());
            mDownloadsAdapter.setDownloadsList(sorted);
        }

        mBinding.executePendingBindings();
    }

    @Override
    public void onDownloadError(@NonNull String error, @NonNull String filename) {
        Log.e(LOGTAG, error);
        mWidgetManager.getFocusedWindow().showAlert(
                getContext().getString(R.string.download_error_title_v1),
                error,
                null
        );
    }
}
