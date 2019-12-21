package org.mozilla.vrbrowser.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.vrbrowser.geolocation.GeolocationData;

import java.util.Locale;

import kotlin.coroutines.Continuation;
import mozilla.components.browser.search.provider.localization.SearchLocalization;
import mozilla.components.browser.search.provider.localization.SearchLocalizationProvider;

public class GeolocationLocalizationProvider implements SearchLocalizationProvider {

    private String mCountry;
    private String mLanguage;
    private String mRegion;

    GeolocationLocalizationProvider(@NonNull GeolocationData data) {
        mCountry = data.getCountryCode();
        mLanguage = Locale.getDefault().getLanguage();
        mRegion = data.getCountryCode();
    }

    @Nullable
    @Override
    public SearchLocalization determineRegion(@NonNull Continuation<? super SearchLocalization> continuation) {
        return new SearchLocalization(mLanguage, mCountry, mRegion);
    }
}
