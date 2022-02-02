package com.igalia.wolvic.utils.zip;

import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

public class UnzipResultReceiver extends ResultReceiver {

    static final String ZIP_PATH = "zipPath";
    static final String ZIP_PROGRESS = "zipProgress";
    static final String ZIP_OUTPUT_PATH = "zipOutputPath";
    static final String ZIP_ERROR = "zipError";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = { STARTED, PROGRESS, FINISH, CANCEL, ERROR})
    @interface UnzipEvent {}
    static final int STARTED = 0;
    static final int PROGRESS = 1;
    static final int FINISH = 2;
    static final int CANCEL = 3;
    static final int ERROR = 4;

    private ArrayList<UnzipCallback> mReceivers;

    UnzipResultReceiver(Handler handler) {
        super(handler);

        mReceivers = new ArrayList<>();
    }


    void addReceiver(@NonNull UnzipCallback receiver) {
        mReceivers.add(receiver);
    }

    void removeReceiver(@NonNull UnzipCallback receiver) {
        mReceivers.remove(receiver);
    }

    @Override
    protected void onReceiveResult(int resultCode, Bundle resultData) {
        String zipPath = resultData.getString(ZIP_PATH);
        mReceivers.forEach(receiver -> {
            switch (resultCode) {
                case STARTED:
                    receiver.onUnzipStart(zipPath);
                    break;
                case PROGRESS:
                    receiver.onUnzipProgress(zipPath, resultData.getDouble(ZIP_PROGRESS));
                    break;
                case FINISH:
                    receiver.onUnzipFinish(zipPath, resultData.getString(ZIP_OUTPUT_PATH));
                    break;
                case CANCEL:
                    receiver.onUnzipCancelled(zipPath);
                    break;
                case ERROR:
                    receiver.onUnzipError(zipPath, resultData.getString(ZIP_ERROR));
                    break;
            }
        });
    }

}
