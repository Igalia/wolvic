package org.mozilla.vrbrowser.ui.adapters;

import org.mozilla.vrbrowser.utils.StringUtils;

import java.util.Locale;

public class Language {

    public Language(Locale locale) {
        this.locale = locale;
        this.isPreferred = false;

        String languageId = locale.toLanguageTag();
        String displayName = StringUtils.capitalize(locale.getDisplayName());
        this.displayName = displayName + " [" + languageId + "]";
    }

    public Language(Locale locale, String displayName) {
        this.locale = locale;
        this.isPreferred = false;
        this.displayName = StringUtils.capitalize(displayName);
    }

    private Locale locale;
    private boolean isPreferred;
    private String displayName;

    public Locale getLocale() {
        return this.locale;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getLanguageTag() {
        return this.locale.toLanguageTag();
    }

    public void setPreferred(boolean isPreferred) {
        this.isPreferred = isPreferred;
    }

    public boolean isPreferred() {
        return isPreferred;
    }

    @Override
    public int hashCode() {
        return locale.hashCode();
    }
}
