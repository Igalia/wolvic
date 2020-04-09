package org.mozilla.vrbrowser.browser.content;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListUpdateCallback;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.ContentBlockingController;
import org.mozilla.geckoview.ContentBlockingController.ContentBlockingException;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserActivity;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.db.SitePermission;
import org.mozilla.vrbrowser.ui.viewmodel.SitePermissionViewModel;
import org.mozilla.vrbrowser.utils.UrlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class TrackingProtectionStore implements DefaultLifecycleObserver,
        SharedPreferences.OnSharedPreferenceChangeListener, ListUpdateCallback {

    public interface TrackingProtectionListener {
        default void onExcludedTrackingProtectionChange(@NonNull String uri, boolean excluded) {};
        default void onTrackingProtectionLevelUpdated(int level) {};
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(mContext.getString(R.string.settings_key_tracking_protection_level))) {
            setTrackingProtectionLevel(SettingsStore.getInstance(mContext).getTrackingProtectionLevel());
        }
    }

    private Context mContext;
    private GeckoRuntime mRuntime;
    private ContentBlockingController mContentBlockingController;
    private Lifecycle mLifeCycle;
    private SitePermissionViewModel mViewModel;
    private List<TrackingProtectionListener> mListeners;
    private SharedPreferences mPrefs;
    private List<SitePermission> mCurrentSitePermissions;
    private List<SitePermission> mSitePermissions;

    public TrackingProtectionStore(@NonNull Context context,
                                   @NonNull GeckoRuntime runtime) {
        mContext = context;
        mRuntime = runtime;
        mContentBlockingController = mRuntime.getContentBlockingController();
        mListeners = new ArrayList<>();
        mCurrentSitePermissions = new ArrayList<>();
        mSitePermissions = new ArrayList<>();

        mLifeCycle = ((VRBrowserActivity) context).getLifecycle();
        mLifeCycle.addObserver(this);

        mViewModel = new SitePermissionViewModel(((Application)context.getApplicationContext()));

        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mPrefs.registerOnSharedPreferenceChangeListener(this);

        setTrackingProtectionLevel(SettingsStore.getInstance(mContext).getTrackingProtectionLevel());
    }

    public void addListener(@NonNull TrackingProtectionListener listener) {
        mListeners.add(listener);
    }

    public void removeListener(@NonNull TrackingProtectionListener listener) {
        mListeners.remove(listener);
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        mViewModel.getAll(SitePermission.SITE_PERMISSION_TRACKING).observeForever(mSitePermissionObserver);
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        mViewModel.getAll(SitePermission.SITE_PERMISSION_TRACKING).removeObserver(mSitePermissionObserver);
    }

    private Observer<List<SitePermission>> mSitePermissionObserver = this::notifyDiff;

    @Override
    public void onInserted(int position, int count) {
        if (mSitePermissions == null) {
            return;
        }

        for (int i=0; i<count; i++) {
            SitePermission permission = mSitePermissions.get(position + i);
            ContentBlockingException exception = toContentBlockingException(permission);
            if (exception != null) {
                mListeners.forEach(listener -> listener.onExcludedTrackingProtectionChange(
                        UrlUtils.getHost(exception.uri),
                        true));
            }
        }

        final List<ContentBlockingException> exceptionsList = new ArrayList<>();
        mContentBlockingController.clearExceptionList();
        for (SitePermission permission: mSitePermissions) {
            ContentBlockingException exception = toContentBlockingException(permission);
            if (exception != null) {
                exceptionsList.add(exception);
            }
        }
        mContentBlockingController.restoreExceptionList(exceptionsList);
    }

    @Override
    public void onRemoved(int position, int count) {
        if (mCurrentSitePermissions == null) {
            return;
        }

        for (int i=0; i<count; i++) {
            SitePermission permission = mCurrentSitePermissions.get(position + i);
            ContentBlockingException exception = toContentBlockingException(permission);
            if (exception != null) {
                mContentBlockingController.removeException(exception);
                mListeners.forEach(listener -> listener.onExcludedTrackingProtectionChange(
                        UrlUtils.getHost(exception.uri),
                        false));
            }
        }
    }

    @Override
    public void onMoved(int fromPosition, int toPosition) {
        // We never move from the exceptions list
    }

    @Override
    public void onChanged(int position, int count, @Nullable Object payload) {
        // We never update from the exceptions list
    }

    private void notifyDiff(List<SitePermission> newList) {
        if (newList == null) {
            return;
        }

        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return mSitePermissions.size();
            }

            @Override
            public int getNewListSize() {
                return newList.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return mSitePermissions.get(oldItemPosition).url.equals(newList.get(newItemPosition).url) &&
                        mSitePermissions.get(oldItemPosition).principal.equals(newList.get(newItemPosition).principal) &&
                        mSitePermissions.get(oldItemPosition).category == newList.get(newItemPosition).category;
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                SitePermission newSite = newList.get(newItemPosition);
                SitePermission olSite = mSitePermissions.get(oldItemPosition);
                return newSite.url.equals(olSite.url)
                        && Objects.equals(newSite.category, olSite.category)
                        && Objects.equals(newSite.principal, olSite.principal);
            }
        });

        mCurrentSitePermissions = mSitePermissions;
        mSitePermissions = newList;
        result.dispatchUpdatesTo(this);
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        mLifeCycle.removeObserver(this);
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    public void contains(@NonNull Session session, Function<Boolean, Void> onResult) {
        mContentBlockingController.checkException(session.getGeckoSession()).accept(aBoolean -> {
            if (aBoolean != null) {
                onResult.apply(aBoolean);

            } else {
                onResult.apply(false);
            }
        });
    }

    public void fetchAll(Function<List<SitePermission>, Void> onResult) {
        final List<SitePermission> list = new ArrayList<>();
        mContentBlockingController.saveExceptionList().accept(contentBlockingExceptions -> {
            if (contentBlockingExceptions != null) {
                contentBlockingExceptions.forEach(exception -> {
                    SitePermission site = toSitePermission(exception);
                    if (site != null) {
                        list.add(site);
                    }
                });
            }
            onResult.apply(list);
        });
    }

    public void add(@NonNull Session session) {
        mContentBlockingController.addException(session.getGeckoSession());
        mListeners.forEach(listener -> listener.onExcludedTrackingProtectionChange(
                UrlUtils.getHost(session.getCurrentUri()),
                true));
        persist();
    }

    public void remove(@NonNull Session session) {
        mContentBlockingController.removeException(session.getGeckoSession());
        mListeners.forEach(listener -> listener.onExcludedTrackingProtectionChange(
                UrlUtils.getHost(session.getCurrentUri()),
                false));
        persist();
    }

    public void remove(@NonNull SitePermission permission) {
        ContentBlockingException exception = toContentBlockingException(permission);
        if (exception != null) {
            mContentBlockingController.removeException(exception);
            persist();
        }
    }

    public void removeAll(@NonNull List<Session> activeSessions) {
        mContentBlockingController.clearExceptionList();
        activeSessions.forEach(session ->
                mListeners.forEach(listener ->
                        listener.onExcludedTrackingProtectionChange(
                                UrlUtils.getHost(session.getCurrentUri()),
                                false)));
        persist();
    }

    private void persist() {
        mViewModel.deleteAll(SitePermission.SITE_PERMISSION_TRACKING);
        mRuntime.getContentBlockingController().saveExceptionList().accept(contentBlockingExceptions -> {
            if (contentBlockingExceptions != null && !contentBlockingExceptions.isEmpty()) {
                contentBlockingExceptions.forEach(exception -> {
                    Log.d("TrackingProtectionStore", "Excluded site: " + exception.uri);
                    SitePermission site = toSitePermission(exception);
                    if (site != null) {
                        mViewModel.insertSite(site);
                    }
                });
            }
        });
    }

    @Nullable
    private static SitePermission toSitePermission(@NonNull ContentBlockingException exception) {
        try {
            JSONObject json = exception.toJson();
            return new SitePermission(
                    json.getString("uri"),
                    json.getString("principal"),
                    SitePermission.SITE_PERMISSION_TRACKING);

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Nullable
    private static ContentBlockingException toContentBlockingException(@NonNull SitePermission permission) {
        try {
            JSONObject json = new JSONObject();
            json.put("uri", permission.url);
            json.put("principal", permission.principal);

            return ContentBlockingException.fromJson(json);

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    private void setTrackingProtectionLevel(int level) {
        ContentBlocking.Settings settings = mRuntime.getSettings().getContentBlocking();
        TrackingProtectionPolicy policy = TrackingProtectionPolicy.recommended();
        if (mRuntime != null) {
            switch (level) {
                case ContentBlocking.EtpLevel.NONE:
                    policy = TrackingProtectionPolicy.none();
                    break;
                case ContentBlocking.EtpLevel.DEFAULT:
                    policy = TrackingProtectionPolicy.recommended();
                    break;
                case ContentBlocking.EtpLevel.STRICT:
                    policy = TrackingProtectionPolicy.strict();
                    break;
            }

            settings.setEnhancedTrackingProtectionLevel(level);
            settings.setStrictSocialTrackingProtection(policy.shouldBlockContent());
            settings.setAntiTracking(policy.getAntiTrackingPolicy());
            settings.setCookieBehavior(policy.getCookiePolicy());

            mListeners.forEach(listener -> listener.onTrackingProtectionLevelUpdated(level));
        }
    }

    public static TrackingProtectionPolicy getTrackingProtectionPolicy(Context mContext) {
        int level = SettingsStore.getInstance(mContext).getTrackingProtectionLevel();
        switch (level) {
            case ContentBlocking.EtpLevel.NONE:
                return TrackingProtectionPolicy.none();
            case ContentBlocking.EtpLevel.DEFAULT:
                return TrackingProtectionPolicy.recommended();
            case ContentBlocking.EtpLevel.STRICT:
                return TrackingProtectionPolicy.strict();
        }

        return TrackingProtectionPolicy.recommended();
    }

}
