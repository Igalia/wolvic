package com.igalia.wolvic.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.igalia.wolvic.R;
import com.igalia.wolvic.downloads.Download;
import com.igalia.wolvic.downloads.DownloadJob;
import com.igalia.wolvic.downloads.DownloadsManager;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

public class DictionariesManager implements DownloadsManager.DownloadsListener, SharedPreferences.OnSharedPreferenceChangeListener {

    static final String LOGTAG = SystemUtils.createLogtag(DictionariesManager.class);

    private final WidgetManagerDelegate mApplicationDelegate;
    private final Context mContext;
    private final DownloadsManager mDownloadManager;
    private final SharedPreferences mPrefs;
    private long mDicDownloadLang = -1;

    public DictionariesManager(@NonNull Context context) {
        mContext = context;
        mApplicationDelegate = ((WidgetManagerDelegate)context);
        WidgetManagerDelegate mApplicationDelegate = ((WidgetManagerDelegate) context);
        mDownloadManager = mApplicationDelegate.getServicesProvider().getDownloadsManager();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    public void init() {
        mDownloadManager.addListener(this);
        mPrefs.registerOnSharedPreferenceChangeListener(this);
    }

    public void end() {
        mDownloadManager.removeListener(this);
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    public void getOrDownloadDictionary(@NonNull String lang) {
        if (DictionaryUtils.isBuiltinDictionary(lang)) {
            for (String dbName : DictionaryUtils.getBuiltinDicNames(lang)) {
                if (!mContext.getDatabasePath(dbName).exists()) {
                    try {
                        InputStream in = mContext.getAssets().open(
                            DictionaryUtils.getBuiltinDicPath() + dbName);
                        storeDatabase(in, dbName);
                    } catch (Exception e) {
                        Log.e(LOGTAG, Objects.requireNonNull(e.getMessage()));
                    }
                }
            }
        } else if (DictionaryUtils.isExternalDictionary(mContext, lang)) {
            if (DictionaryUtils.getExternalDicPath(mContext, lang) != null)
                return;

            if (mDicDownloadLang != -1) {
                mDownloadManager.removeDownload(mDicDownloadLang, true);
            }

            downloadDictionary(lang);
        }
    }

    private void downloadDictionary(@NonNull String lang) {
        final Dictionary dictionary = DictionaryUtils.getExternalDictionaryByLang(mContext, lang);
        if (dictionary == null) {
            return;
        }

        final String payload = DictionaryUtils.getDictionaryPayload(dictionary);
        try {
            new URL(payload).toURI();
        } catch (Exception e) {
            Log.e(LOGTAG, "Invalid URI in payload for " + lang + " dictionary: " + e.getMessage());
            return;
        }

        // Check if the dic is being downloaded or has been already downloaded.
        // The download item will be removed in 2 situations:
        //   1- the user selects another keyboard before the download is completed
        //   2- when the storing as database task is completed successfully.
        Download dicItem = mDownloadManager.getDownloads().stream().filter(item -> item.getUri().equals(payload)).findFirst().orElse(null);

        if (dicItem == null) {
            // If the dic has not been downloaded, start downloading it
            String outputPath = Objects.requireNonNull(mContext.getExternalFilesDir(null)).getAbsolutePath();
            DownloadJob job = DownloadJob.create(payload);
            job.setOutputPath(outputPath + "/" + job.getFilename());
            mDownloadManager.startDownload(job);
        } else if (dicItem.getStatus() == Download.SUCCESSFUL) {
            Log.w(LOGTAG, "The storing as database task failed for unknown reasons");
            mDicDownloadLang = -1;
            mDownloadManager.removeDownload(dicItem.getId(), true);
        } else {
            // The 'downloadDictionary' method is called either by 'getOrDownloadDictionary' when the user changes / type the keyboard
            Log.w(LOGTAG, "Download is still in progress; we shouldn't reach this code.");
        }
    }

    private void storeDatabase(@NonNull InputStream inputStream, @NonNull String databaseName) {
        OutputStream outputStream;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                outputStream = Files.newOutputStream(mContext.getDatabasePath(databaseName).toPath());
            } else {
                outputStream = new FileOutputStream(mContext.getDatabasePath(databaseName));
            }

            byte[] buffer = new byte[1024 * 8];
            int numOfBytesToRead;
            while ((numOfBytesToRead = inputStream.read(buffer)) > 0)
                outputStream.write(buffer, 0, numOfBytesToRead);
            inputStream.close();
            outputStream.close();
        } catch (Exception e) {
            Log.e(LOGTAG, Objects.requireNonNull(e.getMessage()));
        }
    }

    // DownloadsManager
    @Override
    public void onDownloadsUpdate(@NonNull List<Download> downloads) {
        downloads.stream().filter(download -> DictionaryUtils.getExternalDictionaryByPayload(mContext, download.getUri()) != null).findFirst().ifPresent(dicDownload -> mDicDownloadLang = dicDownload.getId());
    }

    @Override
    public void onDownloadCompleted(@NonNull Download download) {
        assert download.getStatus() == Download.SUCCESSFUL;
        if (download.getOutputFile() == null) {
            Log.w(LOGTAG, "Failed to download URI, missing input stream: " + download.getUri());
            return;
        }
        Dictionary dic = DictionaryUtils.getExternalDictionaryByPayload(mContext, download.getUri());
        if (dic == null) {
            return;
        }
        mDicDownloadLang = -1;

        // Store as database
        try {
            InputStream in;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                in = Files.newInputStream(download.getOutputFile().toPath());
            } else {
                in = new FileInputStream(download.getOutputFile());
            }
            storeDatabase(in, DictionaryUtils.getExternalDicFullName(dic.getLang()));
        } catch (Exception e) {
            Log.e(LOGTAG, Objects.requireNonNull(e.getMessage()));
        }

        mDownloadManager.removeDownload(download.getId(), true);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (s.equals(mContext.getString(R.string.settings_key_remote_props))) {
            mApplicationDelegate.updateKeyboardDictionary();
        }
    }
}
