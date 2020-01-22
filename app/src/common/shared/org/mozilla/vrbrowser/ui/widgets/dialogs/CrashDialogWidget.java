/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.content.Context;

import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.util.ArrayList;

public class CrashDialogWidget extends PromptDialogWidget {

    private String mDumpFile;
    private String mExtraFile;
    private ArrayList<String> mFiles;

    public CrashDialogWidget(@NonNull Context aContext, @NonNull String dumpFile, @NonNull String extraFile) {
        super(aContext);

        mDumpFile = dumpFile;
        mExtraFile = extraFile;

        initialize(aContext);
    }

    public CrashDialogWidget(@NonNull Context aContext, @NonNull ArrayList<String> files) {
        super(aContext);

        mFiles = files;

        initialize(aContext);
    }

    @Override
    protected void initialize(Context aContext) {
        super.initialize(aContext);

        setButtons(new int[] {
                R.string.do_not_sent_button,
                R.string.send_data_button
        });
        setButtonsDelegate(index -> {
            if (index == PromptDialogWidget.NEGATIVE) {
                if (mFiles != null) {
                    SystemUtils.clearCrashFiles(getContext(), mFiles);
                }
                 onDismiss();

            } else if (index == PromptDialogWidget.POSITIVE) {
                if (mFiles != null) {
                    SystemUtils.postCrashFiles(getContext(), mFiles);

                } else {
                    SystemUtils.postCrashFiles(getContext(), mDumpFile, mExtraFile);
                }

                SettingsStore.getInstance(getContext()).setCrashReportingEnabled(mBinding.checkbox.isChecked());

                onDismiss();
            }
        });

        setDescriptionVisible(false);

        setIcon(R.drawable.sad_fox);
        setTitle(R.string.crash_dialog_heading);
        setBody(getContext().getString(R.string.crash_dialog_message, getContext().getString(R.string.app_name)));
        setCheckboxText(R.string.crash_dialog_send_data);
        setLinkDelegate(() -> {
            mWidgetManager.openNewTabForeground(getContext().getString(R.string.crash_dialog_learn_more_url));
            onDismiss();
        });
    }

    @Override
    public void show(int aShowFlags) {
        mBinding.checkbox.setChecked(false);

        super.show(aShowFlags);
    }
}
