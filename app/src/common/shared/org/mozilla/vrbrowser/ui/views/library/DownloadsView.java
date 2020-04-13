/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.views.library;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserActivity;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.DownloadsBinding;
import org.mozilla.vrbrowser.downloads.Download;
import org.mozilla.vrbrowser.downloads.DownloadsManager;
import org.mozilla.vrbrowser.telemetry.GleanMetricsService;
import org.mozilla.vrbrowser.ui.adapters.DownloadsAdapter;
import org.mozilla.vrbrowser.ui.callbacks.DownloadItemCallback;
import org.mozilla.vrbrowser.ui.callbacks.DownloadsCallback;
import org.mozilla.vrbrowser.ui.callbacks.DownloadsContextMenuCallback;
import org.mozilla.vrbrowser.ui.viewmodel.DownloadsViewModel;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.ui.widgets.WindowWidget;
import org.mozilla.vrbrowser.ui.widgets.Windows;
import org.mozilla.vrbrowser.ui.widgets.dialogs.PromptDialogWidget;
import org.mozilla.vrbrowser.ui.widgets.menus.library.DownloadsContextMenuWidget;
import org.mozilla.vrbrowser.ui.widgets.menus.library.LibraryContextMenuWidget;
import org.mozilla.vrbrowser.ui.widgets.menus.library.SortingContextMenuWidget;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class DownloadsView extends LibraryView implements DownloadsManager.DownloadsListener {

    private static final String LOGTAG = SystemUtils.createLogtag(DownloadsView.class);

    private DownloadsBinding mBinding;
    private DownloadsAdapter mDownloadsAdapter;
    private DownloadsManager mDownloadsManager;
    private Comparator<Download> mSortingComparator;
    private DownloadsViewModel mViewModel;

    public DownloadsView(Context aContext) {
        super(aContext);
        initialize();
    }

    public DownloadsView(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize();
    }

    public DownloadsView(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize();
    }

    @Override
    protected void initialize() {
        super.initialize();

        mDownloadsManager = ((VRBrowserActivity) getContext()).getServicesProvider().getDownloadsManager();
        mViewModel = new ViewModelProvider(
                (VRBrowserActivity)getContext(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(((VRBrowserActivity) getContext()).getApplication()))
                .get(DownloadsViewModel.class);

        mSortingComparator = mAZFileNameComparator;

        updateUI();
    }

    @SuppressLint("ClickableViewAccessibility")
    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.downloads, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity)getContext());
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
        mBinding.downloadsList.setDrawingCacheEnabled(true);
        mBinding.downloadsList.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

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
    }

    @Override
    public void onHide() {
        mDownloadsManager.removeListener(this);
    }

    private final DownloadItemCallback mDownloadItemCallback = new DownloadItemCallback() {

        @Override
        public void onClick(@NonNull View view, @NonNull Download item) {
            mBinding.downloadsList.requestFocusFromTouch();

            SessionStore.get().getActiveSession().loadUri(item.getOutputFile());

            WindowWidget window = mWidgetManager.getFocusedWindow();
            window.hidePanel(Windows.PanelType.HISTORY);
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
                    index -> {
                        if (index == PromptDialogWidget.POSITIVE) {
                            mDownloadsManager.removeDownload(item.getId());
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
                        rowPosition == layoutManager.findLastVisibleItemPosition()-1 || rowPosition == layoutManager.findLastCompletelyVisibleItemPosition()-1)
                        && rowPosition != lastItem) {
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
                    index -> {
                        if (index == PromptDialogWidget.POSITIVE) {
                            mDownloadsManager.clearAllDownloads();
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
                                    item.getOutputFile(),
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
            double width = Math.ceil(getWidth()/getContext().getResources().getDisplayMetrics().density);
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
            GleanMetricsService.Tabs.openedCounter(GleanMetricsService.Tabs.TabSource.DOWNLOADS);
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
                    index -> {
                        if (index == PromptDialogWidget.POSITIVE) {
                            mDownloadsManager.removeDownload(item.getDownloadsId());
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
        getDrawingRect(offsetViewBounds);
        offsetDescendantRectToMyCoords(view, offsetViewBounds);

        SortingContextMenuWidget menu = new SortingContextMenuWidget(getContext());
        menu.setItemDelegate(item -> {
            switch (item) {
                case SortingContextMenuWidget.SORT_FILENAME_AZ:
                    mSortingComparator = mAZFileNameComparator;
                    break;
                case SortingContextMenuWidget.SORT_FILENAME_ZA:
                    mSortingComparator = mZAFilenameComparator;
                    break;
                case SortingContextMenuWidget.SORT_DATE_ASC:
                    mSortingComparator = mDownloadDateAscComparator;
                    break;
                case SortingContextMenuWidget.SORT_DATE_DESC:
                    mSortingComparator = mDownloadDateDescComparator;
                    break;
                case SortingContextMenuWidget.SORT_SIZE_ASC:
                    mSortingComparator = mDownloadSizeAscComparator;
                    break;
                case SortingContextMenuWidget.SORT_SIZE_DESC:
                    mSortingComparator = mDownloadSizeDescComparator;
                    break;
            }
            onDownloadsUpdate(mDownloadsManager.getDownloads());
        });
        menu.getPlacement().parentHandle = window.getHandle();

        menu.getPlacement().anchorY = 1.0f;
        PointF position = new PointF(
                (offsetViewBounds.left + view.getWidth()) * ratio,
                -(offsetViewBounds.top + view.getHeight()) * ratio);
        menu.getPlacement().translationX = position.x - (menu.getWidth()/menu.getPlacement().density);
        menu.getPlacement().translationY = position.y + getResources().getDimension(R.dimen.library_menu_top_margin)/menu.getPlacement().density;
        menu.show(UIWidget.REQUEST_FOCUS);
    }

    // DownloadsManager.DownloadsListener

    private Comparator<Download> mAZFileNameComparator = (o1, o2) -> o1.getFilename().compareTo(o2.getFilename());
    private Comparator<Download> mZAFilenameComparator = (o1, o2) -> o2.getFilename().compareTo(o1.getFilename());
    private Comparator<Download> mDownloadDateAscComparator = (o1, o2) -> (int)(o1.getLastModified() - o2.getLastModified());
    private Comparator<Download> mDownloadDateDescComparator = (o1, o2) -> (int)(o2.getLastModified() - o1.getLastModified());
    private Comparator<Download> mDownloadSizeAscComparator = (o1, o2) -> (int)(o1.getSizeBytes() - o2.getSizeBytes());
    private Comparator<Download> mDownloadSizeDescComparator = (o1, o2) -> (int)(o2.getSizeBytes() - o1.getSizeBytes());

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
                getContext().getString(R.string.download_error_title, filename),
                error,
                null
        );
    }
}
