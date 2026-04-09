package com.igalia.wolvic.search;

import android.content.Context;
import android.net.Uri;
import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.R;

import java.net.IDN;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchEngineValidation {

    public static final String DUMMY_PLACEHOLDER_FOR_VALIDATION = "test";
    public static final int CUSTOM_SEARCH_ENGINE_NAME_MAX_LEN = 50;

    public static class ValidationResult {
        private final boolean isValid;
        @Nullable
        private final String errorMessage;

        private ValidationResult(boolean isValid, @Nullable String errorMessage) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult error(@NonNull String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() {
            return isValid;
        }

        @Nullable
        public String getErrorMessage() {
            return errorMessage;
        }
    }

    @NonNull
    public static ValidationResult validateSearchUrl(@NonNull Context context, @Nullable String searchUrl) {
        return validateUrl(context, searchUrl, true);
    }

    @NonNull
    public static ValidationResult validateName(@NonNull Context context, @Nullable String name) {
        if (name == null || name.trim().isEmpty()) {
            return ValidationResult.error(context.getString(R.string.search_engine_error_name_required));
        }

        String trimmedName = name.trim();
        if (trimmedName.length() > CUSTOM_SEARCH_ENGINE_NAME_MAX_LEN) {
            return ValidationResult.error(context.getString(R.string.search_engine_error_name_too_long));
        }

        return ValidationResult.success();
    }

    @NonNull
    public static ValidationResult validateSuggestUrl(@NonNull Context context, @Nullable String suggestUrl) {
        return validateUrl(context, suggestUrl, false);
    }

    @NonNull
    private static ValidationResult validateUrl(@NonNull Context context, @Nullable String url, boolean isRequired) {
        if (url == null || url.trim().isEmpty()) {
            return isRequired
                    ? ValidationResult.error(context.getString(R.string.search_engine_error_url_required))
                    : ValidationResult.success();
        }

        String trimmedUrl = url.trim();

        if (!trimmedUrl.contains("%s") && !trimmedUrl.contains("{searchTerms}")) {
            return ValidationResult.error(context.getString(R.string.search_engine_error_url_placeholder, "%s"));
        }

        String urlForValidation = trimmedUrl
                .replace("%s", DUMMY_PLACEHOLDER_FOR_VALIDATION)
                .replace("{searchTerms}", DUMMY_PLACEHOLDER_FOR_VALIDATION);

        Uri uri = Uri.parse(urlForValidation);

        String scheme = uri.getScheme();
        if (scheme == null) {
            return ValidationResult.error(context.getString(R.string.search_engine_error_url_scheme));
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return ValidationResult.error(context.getString(R.string.search_engine_error_url_scheme_unsupported));
        }

        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            return ValidationResult.error(context.getString(R.string.search_engine_error_url_host));
        }

        if (isPrivateOrLocalHost(host)) {
            return ValidationResult.error(context.getString(R.string.search_engine_error_url_private));
        }

        return ValidationResult.success();
    }

    @NonNull
    public static List<ValidationResult> validateAll(@NonNull Context context, @Nullable String name, @Nullable String searchUrl, @Nullable String suggestUrl) {
        List<ValidationResult> results = new ArrayList<>();
        
        ValidationResult nameResult = validateName(context, name);
        if (!nameResult.isValid()) {
            results.add(nameResult);
        }

        ValidationResult searchUrlResult = validateSearchUrl(context, searchUrl);
        if (!searchUrlResult.isValid()) {
            results.add(searchUrlResult);
        }

        ValidationResult suggestUrlResult = validateSuggestUrl(context, suggestUrl);
        if (!suggestUrlResult.isValid()) {
            results.add(suggestUrlResult);
        }

        return results;
    }

    private static boolean isPrivateOrLocalHost(@NonNull String host) {
        String lowerHost = IDN.toASCII(host).toLowerCase();
        if (lowerHost.equals("localhost") || lowerHost.equals("localhost.localdomain")) {
            return true;
        }

        if (Patterns.IP_ADDRESS.matcher(host).matches()) {
            return isPrivateIp(host);
        }

        try {
            String asciiHost = IDN.toASCII(host).toLowerCase();
            if (asciiHost.endsWith(".local") || asciiHost.endsWith(".localhost")) {
                return true;
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    private static boolean isPrivateIp(@NonNull String ip) {
        try {
            java.net.InetAddress address = java.net.InetAddress.getByName(ip);
            return address.isSiteLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress();
        } catch (Exception ignored) {
            return false;
        }
    }
}
