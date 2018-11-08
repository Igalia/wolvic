package org.mozilla.vrbrowser.search;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mozilla.vrbrowser.geolocation.GeolocationData;

import java.util.Locale;

import mozilla.components.browser.search.provider.localization.SearchLocalizationProvider;

public class GeolocationLocalizationProvider extends SearchLocalizationProvider {

    private String mCountry;
    private String mLanguage;
    private String mRegion;

    GeolocationLocalizationProvider(GeolocationData data) {
        mCountry = data.getCountryCode();
        mLanguage = Locale.getDefault().getLanguage();
        mRegion = data.getCountryCode();
    }

    @NotNull
    @Override
    public String getCountry() {
        return mCountry;
    }

    @NotNull
    @Override
    public String getLanguage() {
        return mLanguage;
    }

    @Nullable
    @Override
    public String getRegion() {
        return mRegion;
    }

}
