package org.mozilla.vrbrowser.geolocation;

import android.content.Context;

import androidx.annotation.NonNull;

import org.mozilla.gecko.util.ThreadUtils;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.engine.EngineProvider;

public class GeolocationWrapper {

    private static final int MAX_RETRIES = 2;
    private static final int RETRY_SLEEP = 5 * 1000;

    public static void update(final @NonNull Context aContext) {
        String endpoint = aContext.getString(R.string.geolocation_api_url);
        update(aContext, endpoint, 0, MAX_RETRIES);
    }

    private static void update(final @NonNull Context aContext,
                               final @NonNull String endPoint,
                               final int retryCount,
                               final int maxRetries) {
        if (retryCount <= maxRetries - 1) {
            GeolocationClient.getGeolocation(
                    EngineProvider.INSTANCE.getDefaultGeckoWebExecutor(aContext),
                    endPoint,
                    (data) -> {
                        if (data == null) {
                            if (retryCount <= maxRetries) {
                                ThreadUtils.postDelayedToUiThread(() ->
                                                update(aContext, endPoint, retryCount + 1, maxRetries),
                                        RETRY_SLEEP);
                            }

                        } else {
                            SettingsStore.getInstance(aContext).setGeolocationData(data.toString());
                        }
                        return null;
                    },
                    (error) -> {
                        if (retryCount <= maxRetries) {
                            ThreadUtils.postDelayedToUiThread(() ->
                                            update(aContext, endPoint, retryCount + 1, maxRetries),
                                    RETRY_SLEEP);
                        }
                        return null;
                    });
        }
    }

}
