/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.content.Context;
import android.view.LayoutInflater;

import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.HistoryStore;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.ClearHistoryDialogBinding;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class ClearHistoryDialogWidget extends SettingDialogWidget {

    public static final int TODAY = 0;
    public static final int YESTERDAY = 1;
    public static final int LAST_WEEK = 2;
    public static final int EVERYTHING = 3;

    private ClearHistoryDialogBinding mClearHistoryBinding;

    public ClearHistoryDialogWidget(Context aContext) {
        super(aContext);
    }

    @Override
    protected void initialize(Context aContext) {
        super.initialize(aContext);

        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mClearHistoryBinding = DataBindingUtil.inflate(inflater, R.layout.clear_history_dialog, mBinding.content, true);
        mClearHistoryBinding.clearHistoryRadio.setChecked(0, false);

        mBinding.headerLayout.setTitle(R.string.history_clear);
        mBinding.footerLayout.setFooterButtonText(R.string.history_clear_now);
        mBinding.footerLayout.setFooterButtonClickListener((view -> {
            Calendar date = new GregorianCalendar();
            date.set(Calendar.HOUR_OF_DAY, 0);
            date.set(Calendar.MINUTE, 0);
            date.set(Calendar.SECOND, 0);
            date.set(Calendar.MILLISECOND, 0);

            long currentTime = System.currentTimeMillis();
            long todayLimit = date.getTimeInMillis();
            long yesterdayLimit = todayLimit - SystemUtils.ONE_DAY_MILLIS;
            long oneWeekLimit = todayLimit - SystemUtils.ONE_WEEK_MILLIS;

            HistoryStore store = SessionStore.get().getHistoryStore();
            switch (mClearHistoryBinding.clearHistoryRadio.getCheckedRadioButtonId()) {
                case ClearHistoryDialogWidget.TODAY:
                    store.deleteVisitsBetween(todayLimit, currentTime);
                    break;
                case ClearHistoryDialogWidget.YESTERDAY:
                    store.deleteVisitsBetween(yesterdayLimit, currentTime);
                    break;
                case ClearHistoryDialogWidget.LAST_WEEK:
                    store.deleteVisitsBetween(oneWeekLimit, currentTime);
                    break;
                case ClearHistoryDialogWidget.EVERYTHING:
                    store.deleteEverything();
                    break;
            }
            SessionStore.get().purgeSessionHistory();
            onDismiss();
        }));
    }

    @Override
    public void show(int aShowFlags) {
        super.show(aShowFlags);

        mClearHistoryBinding.clearHistoryRadio.setChecked(0, false);
    }
}
