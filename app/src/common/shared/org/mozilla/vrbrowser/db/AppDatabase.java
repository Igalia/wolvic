package org.mozilla.vrbrowser.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import org.mozilla.vrbrowser.AppExecutors;

@Database(entities = {PopUpSite.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "app";

    private static AppDatabase mInstance = null;

    private final MutableLiveData<Boolean> mIsDatabaseCreated = new MutableLiveData<>();

    public abstract PopUpSiteDao popUpSiteDao();

    public static AppDatabase getAppDatabase(Context context, final AppExecutors executors) {
        if (mInstance == null) {
            synchronized (AppDatabase.class) {
                mInstance = buildDatabase(context.getApplicationContext(), executors);
                mInstance.updateDatabaseCreated(context);
            }
        }

        return mInstance;
    }

    @NonNull
    private static AppDatabase buildDatabase(final @NonNull Context appContext, final @NonNull AppExecutors executors) {
        return Room.databaseBuilder(appContext, AppDatabase.class, DATABASE_NAME)
                .addCallback(new Callback() {
                    @Override
                    public void onCreate(@NonNull SupportSQLiteDatabase db) {
                        super.onCreate(db);

                        executors.diskIO().execute(() -> {
                            AppDatabase database = AppDatabase.getAppDatabase(appContext, executors);
                            database.setDatabaseCreated();
                        });
                    }

                    @Override
                    public void onOpen(@NonNull SupportSQLiteDatabase db) {
                        super.onOpen(db);
                    }

                    @Override
                    public void onDestructiveMigration(@NonNull SupportSQLiteDatabase db) {
                        super.onDestructiveMigration(db);
                    }
                })
                .build();
    }

    private void updateDatabaseCreated(final @NonNull Context context) {
        if (context.getDatabasePath(DATABASE_NAME).exists()) {
            setDatabaseCreated();
        }
    }

    private void setDatabaseCreated() {
        mIsDatabaseCreated.postValue(true);
    }

    public LiveData<Boolean> getDatabaseCreated() {
        return mIsDatabaseCreated;
    }
}
