package com.igalia.wolvic.browser.api;

import android.os.Bundle;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * The Autocomplete API provides a way to leverage Browser Engine's input form handling for autocompletion.
 *
 * <p>The API is split into two parts: 1. Storage-level delegates. 2. User-prompt delegates.
 *
 * <p>The storage-level delegates connect Browser Engine mechanics to the app's storage, e.g., retrieving and
 * storing of login entries.
 *
 * <p>The user-prompt delegates propagate decisions to the app that could require user choice, e.g.,
 * saving or updating of login entries or the selection of a login entry out of multiple options.
 *
 * <p>Throughout the documentation, we will refer to the filling out of input forms using two terms:
 * 1. Autofill: automatic filling without user interaction. 2. Autocomplete: semi-automatic filling
 * that requires user prompting for the selection.
 *
 * <h2>Examples</h2>
 *
 * <h3>Autocomplete/Fetch API</h3>
 *
 * <p>Browser loads <code>https://example.com</code> which contains (for the purpose of this
 * example) elements resembling a login form, e.g.,
 *
 * <pre><code>
 *   &lt;form&gt;
 *     &lt;input type=&quot;text&quot; placeholder=&quot;username&quot;&gt;
 *     &lt;input type=&quot;password&quot; placeholder=&quot;password&quot;&gt;
 *     &lt;input type=&quot;submit&quot; value=&quot;submit&quot;&gt;
 *   &lt;/form&gt;
 * </code></pre>
 *
 * <p>With the document parsed and the login input fields identified, Browser dispatches a <code>
 * StorageDelegate.onLoginFetch(&quot;example.com&quot;)</code> request to fetch logins for the
 * given domain.
 *
 * <p>Based on the provided login entries, Browser will attempt to autofill the login input
 * fields, if there is only one suitable login entry option.
 *
 * <p>In the case of multiple valid login entry options, Browser dispatches a <code>
 * ISession.PromptDelegate.onLoginSelect</code> request, which allows for user-choice
 * delegation.
 *
 * <p>Based on the returned login entries, Browser will attempt to autofill/autocomplete the login
 * input fields.
 *
 * <h3>Update API</h3>
 *
 * <p>When the user submits some login input fields, Browser dispatches another <code>
 * StorageDelegate.onLoginFetch(&quot;example.com&quot;)</code> request to check whether the
 * submitted login exists or whether it's a new or updated login entry.
 *
 * <p>If the submitted login is already contained as-is in the collection returned by <code>
 * onLoginFetch</code>, then Browser dispatches <code>StorageDelegate.onLoginUsed</code> with the
 * submitted login entry.
 *
 * <p>If the submitted login is a new or updated entry, Browser dispatches a sequence of requests
 * to save/update the login entry, see the Save API example.
 *
 * <h3>Save API</h3>
 *
 * <p>The user enters new or updated (password) login credentials in some login input fields and
 * submits explicitely (submit action) or by navigation. Browser identifies the entered
 * credentials and dispatches a <code>ISession.PromptDelegate.onLoginSave(session, request)
 * </code> with the provided credentials.
 *
 * <p>The app may dismiss the prompt request via <code>
 * return IResult.fromValue(prompt.dismiss())</code> which terminates this saving request, or
 * confirm it via <code>return IResult.fromValue(prompt.confirm(login))</code> where <code>login
 * </code> either holds the credentials originally provided by the prompt request (<code>
 * prompt.logins[0]</code>) or a new or modified login entry.
 *
 * <p>The login entry returned in a confirmed save prompt is used to request for saving in the
 * runtime delegate via <code>StorageDelegate.onLoginSave(login)</code>. If the app has already
 * stored the entry during the prompt request handling, it may ignore this storage saving request.
 * <br>
 *
 * @see WRuntime#setAutocompleteStorageDelegate <br>
 * @see WSession#setPromptDelegate <br>
 * @see WSession.PromptDelegate#onLoginSave <br>
 * @see WSession.PromptDelegate#onLoginSelect
 */
public class WAutocomplete {
    protected WAutocomplete() {}

    /** Holds credit card information for a specific entry. */
    public static class CreditCard {
        private static final String GUID_KEY = "guid";
        private static final String NAME_KEY = "name";
        private static final String NUMBER_KEY = "number";
        private static final String EXP_MONTH_KEY = "expMonth";
        private static final String EXP_YEAR_KEY = "expYear";

        /** The unique identifier for this login entry. */
        public final @Nullable String guid;

        /** The full name as it appears on the credit card. */
        public final @NonNull String name;

        /** The credit card number. */
        public final @NonNull String number;

        /** The expiration month. */
        public final @NonNull String expirationMonth;

        /** The expiration year. */
        public final @NonNull String expirationYear;

        // For tests only.
        @AnyThread
        protected CreditCard() {
            guid = null;
            name = "";
            number = "";
            expirationMonth = "";
            expirationYear = "";
        }

        @AnyThread
            /* package */ CreditCard(final @NonNull Bundle bundle) {
            guid = bundle.getString(GUID_KEY);
            name = bundle.getString(NAME_KEY, "");
            number = bundle.getString(NUMBER_KEY, "");
            expirationMonth = bundle.getString(EXP_MONTH_KEY, "");
            expirationYear = bundle.getString(EXP_YEAR_KEY, "");
        }

        @Override
        @AnyThread
        public String toString() {
            final StringBuilder builder = new StringBuilder("CreditCard {");
            builder
                    .append("guid=")
                    .append(guid)
                    .append(", name=")
                    .append(name)
                    .append(", number=")
                    .append(number)
                    .append(", expirationMonth=")
                    .append(expirationMonth)
                    .append(", expirationYear=")
                    .append(expirationYear)
                    .append("}");
            return builder.toString();
        }

        @AnyThread
        /* package */ @NonNull
        Bundle toBundle() {
            final Bundle bundle = new Bundle(7);
            bundle.putString(GUID_KEY, guid);
            bundle.putString(NAME_KEY, name);
            bundle.putString(NUMBER_KEY, number);
            if (expirationMonth != null) {
                bundle.putString(EXP_MONTH_KEY, expirationMonth);
            }
            if (expirationYear != null) {
                bundle.putString(EXP_YEAR_KEY, expirationYear);
            }

            return bundle;
        }

        public static class Builder {
            private final Bundle mBundle;

            @AnyThread
                /* package */ Builder(final @NonNull Bundle bundle) {
                mBundle = new Bundle(bundle);
            }

            @AnyThread
            @SuppressWarnings("checkstyle:javadocmethod")
            public Builder() {
                mBundle = new Bundle(7);
            }

            /**
             * Finalize the {@link WAutocomplete.CreditCard} instance.
             *
             * @return The {@link WAutocomplete.CreditCard} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.CreditCard build() {
                return new WAutocomplete.CreditCard(mBundle);
            }

            /**
             * Set the unique identifier for this credit card entry.
             *
             * @param guid The unique identifier string.
             * @return This {@link WAutocomplete.CreditCard.Builder} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.CreditCard.Builder guid(final @Nullable String guid) {
                mBundle.putString(GUID_KEY, guid);
                return this;
            }

            /**
             * Set the name for this credit card entry.
             *
             * @param name The full name as it appears on the credit card.
             * @return This {@link WAutocomplete.CreditCard.Builder} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.CreditCard.Builder name(final @Nullable String name) {
                mBundle.putString(NAME_KEY, name);
                return this;
            }

            /**
             * Set the number for this credit card entry.
             *
             * @param number The credit card number string.
             * @return This {@link WAutocomplete.CreditCard.Builder} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.CreditCard.Builder number(final @Nullable String number) {
                mBundle.putString(NUMBER_KEY, number);
                return this;
            }

            /**
             * Set the expiration month for this credit card entry.
             *
             * @param expMonth The expiration month string.
             * @return This {@link WAutocomplete.CreditCard.Builder} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.CreditCard.Builder expirationMonth(final @Nullable String expMonth) {
                mBundle.putString(EXP_MONTH_KEY, expMonth);
                return this;
            }

            /**
             * Set the expiration year for this credit card entry.
             *
             * @param expYear The expiration year string.
             * @return This {@link WAutocomplete.CreditCard.Builder} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.CreditCard.Builder expirationYear(final @Nullable String expYear) {
                mBundle.putString(EXP_YEAR_KEY, expYear);
                return this;
            }
        }
    }

    /** Holds address information for a specific entry. */
    public static class Address {
        private static final String GUID_KEY = "guid";
        private static final String NAME_KEY = "name";
        private static final String GIVEN_NAME_KEY = "givenName";
        private static final String ADDITIONAL_NAME_KEY = "additionalName";
        private static final String FAMILY_NAME_KEY = "familyName";
        private static final String ORGANIZATION_KEY = "organization";
        private static final String STREET_ADDRESS_KEY = "streetAddress";
        private static final String ADDRESS_LEVEL1_KEY = "addressLevel1";
        private static final String ADDRESS_LEVEL2_KEY = "addressLevel2";
        private static final String ADDRESS_LEVEL3_KEY = "addressLevel3";
        private static final String POSTAL_CODE_KEY = "postalCode";
        private static final String COUNTRY_KEY = "country";
        private static final String TEL_KEY = "tel";
        private static final String EMAIL_KEY = "email";
        private static final byte bundleCapacity = 14;

        /** The unique identifier for this address entry. */
        public final @Nullable String guid;

        /** The full name. */
        public final @NonNull String name;

        /** The given (first) name. */
        public final @NonNull String givenName;

        /** An additional name, if available. */
        public final @NonNull String additionalName;

        /** The family name. */
        public final @NonNull String familyName;

        /** The name of the company, if applicable. */
        public final @NonNull String organization;

        /** The (multiline) street address. */
        public final @NonNull String streetAddress;

        /** The level 1 (province) address. Note: Only use if streetAddress is not provided. */
        public final @NonNull String addressLevel1;

        /** The level 2 (city/town) address. Note: Only use if streetAddress is not provided. */
        public final @NonNull String addressLevel2;

        /**
         * The level 3 (suburb/sublocality) address. Note: Only use if streetAddress is not provided.
         */
        public final @NonNull String addressLevel3;

        /** The postal code. */
        public final @NonNull String postalCode;

        /** The country string in ISO 3166. */
        public final @NonNull String country;

        /** The telephone number string. */
        public final @NonNull String tel;

        /** The email address. */
        public final @NonNull String email;

        // For tests only.
        @AnyThread
        protected Address() {
            guid = null;
            name = "";
            givenName = "";
            additionalName = "";
            familyName = "";
            organization = "";
            streetAddress = "";
            addressLevel1 = "";
            addressLevel2 = "";
            addressLevel3 = "";
            postalCode = "";
            country = "";
            tel = "";
            email = "";
        }

        @AnyThread
            /* package */ Address(final @NonNull Bundle bundle) {
            guid = bundle.getString(GUID_KEY);
            name = bundle.getString(NAME_KEY);
            givenName = bundle.getString(GIVEN_NAME_KEY);
            additionalName = bundle.getString(ADDITIONAL_NAME_KEY);
            familyName = bundle.getString(FAMILY_NAME_KEY);
            organization = bundle.getString(ORGANIZATION_KEY);
            streetAddress = bundle.getString(STREET_ADDRESS_KEY);
            addressLevel1 = bundle.getString(ADDRESS_LEVEL1_KEY);
            addressLevel2 = bundle.getString(ADDRESS_LEVEL2_KEY);
            addressLevel3 = bundle.getString(ADDRESS_LEVEL3_KEY);
            postalCode = bundle.getString(POSTAL_CODE_KEY);
            country = bundle.getString(COUNTRY_KEY);
            tel = bundle.getString(TEL_KEY);
            email = bundle.getString(EMAIL_KEY);
        }

        @Override
        @AnyThread
        public String toString() {
            final StringBuilder builder = new StringBuilder("Address {");
            builder
                    .append("guid=")
                    .append(guid)
                    .append(", givenName=")
                    .append(givenName)
                    .append(", additionalName=")
                    .append(additionalName)
                    .append(", familyName=")
                    .append(familyName)
                    .append(", organization=")
                    .append(organization)
                    .append(", streetAddress=")
                    .append(streetAddress)
                    .append(", addressLevel1=")
                    .append(addressLevel1)
                    .append(", addressLevel2=")
                    .append(addressLevel2)
                    .append(", addressLevel3=")
                    .append(addressLevel3)
                    .append(", postalCode=")
                    .append(postalCode)
                    .append(", country=")
                    .append(country)
                    .append(", tel=")
                    .append(tel)
                    .append(", email=")
                    .append(email)
                    .append("}");
            return builder.toString();
        }

        @AnyThread
        /* package */ @NonNull
        Bundle toBundle() {
            final Bundle bundle = new Bundle(bundleCapacity);
            bundle.putString(GUID_KEY, guid);
            bundle.putString(NAME_KEY, name);
            bundle.putString(GIVEN_NAME_KEY, givenName);
            bundle.putString(ADDITIONAL_NAME_KEY, additionalName);
            bundle.putString(FAMILY_NAME_KEY, familyName);
            bundle.putString(ORGANIZATION_KEY, organization);
            bundle.putString(STREET_ADDRESS_KEY, streetAddress);
            bundle.putString(ADDRESS_LEVEL1_KEY, addressLevel1);
            bundle.putString(ADDRESS_LEVEL2_KEY, addressLevel2);
            bundle.putString(ADDRESS_LEVEL3_KEY, addressLevel3);
            bundle.putString(POSTAL_CODE_KEY, postalCode);
            bundle.putString(COUNTRY_KEY, country);
            bundle.putString(TEL_KEY, tel);
            bundle.putString(EMAIL_KEY, email);

            return bundle;
        }

        public static class Builder {
            private final Bundle mBundle;

            @AnyThread
                /* package */ Builder(final @NonNull Bundle bundle) {
                mBundle = new Bundle(bundle);
            }

            @AnyThread
            @SuppressWarnings("checkstyle:javadocmethod")
            public Builder() {
                mBundle = new Bundle(bundleCapacity);
            }

            /**
             * Finalize the {@link WAutocomplete.Address} instance.
             *
             * @return The {@link WAutocomplete.Address} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.Address build() {
                return new WAutocomplete.Address(mBundle);
            }

            /**
             * Set the unique identifier for this address entry.
             *
             * @param guid The unique identifier string.
             * @return This {@link WAutocomplete.Address.Builder} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.Address.Builder guid(final @Nullable String guid) {
                mBundle.putString(GUID_KEY, guid);
                return this;
            }

            /**
             * Set the full name for this address entry.
             *
             * @param name The full name string.
             * @return This {@link WAutocomplete.Address.Builder} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.Address.Builder name(final @Nullable String name) {
                mBundle.putString(NAME_KEY, name);
                return this;
            }

            /**
             * Set the given name for this address entry.
             *
             * @param givenName The given name string.
             * @return This {@link WAutocomplete.Address.Builder} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.Address.Builder givenName(final @Nullable String givenName) {
                mBundle.putString(GIVEN_NAME_KEY, givenName);
                return this;
            }

            /**
             * Set the additional name for this address entry.
             *
             * @param additionalName The additional name string.
             * @return This {@link WAutocomplete.Address.Builder} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.Address.Builder additionalName(final @Nullable String additionalName) {
                mBundle.putString(ADDITIONAL_NAME_KEY, additionalName);
                return this;
            }

            /**
             * Set the family name for this address entry.
             *
             * @param familyName The family name string.
             * @return This {@link WAutocomplete.Address.Builder} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.Address.Builder familyName(final @Nullable String familyName) {
                mBundle.putString(FAMILY_NAME_KEY, familyName);
                return this;
            }

            /**
             * Set the company name for this address entry.
             *
             * @param organization The company name string.
             * @return This {@link WAutocomplete.Address.Builder} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.Address.Builder organization(final @Nullable String organization) {
                mBundle.putString(ORGANIZATION_KEY, organization);
                return this;
            }

            /**
             * Set the street address for this address entry.
             *
             * @param streetAddress The street address string.
             * @return This {@link WAutocomplete.Address.Builder} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.Address.Builder streetAddress(final @Nullable String streetAddress) {
                mBundle.putString(STREET_ADDRESS_KEY, streetAddress);
                return this;
            }

            /**
             * Set the level 1 address for this address entry.
             *
             * @param addressLevel1 The level 1 address string.
             * @return This {@link WAutocomplete.Address.Builder} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.Address.Builder addressLevel1(final @Nullable String addressLevel1) {
                mBundle.putString(ADDRESS_LEVEL1_KEY, addressLevel1);
                return this;
            }

            /**
             * Set the level 2 address for this address entry.
             *
             * @param addressLevel2 The level 2 address string.
             * @return This {@link WAutocomplete.Address.Builder} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.Address.Builder addressLevel2(final @Nullable String addressLevel2) {
                mBundle.putString(ADDRESS_LEVEL2_KEY, addressLevel2);
                return this;
            }

            /**
             * Set the level 3 address for this address entry.
             *
             * @param addressLevel3 The level 3 address string.
             * @return This {@link WAutocomplete.Address.Builder} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.Address.Builder addressLevel3(final @Nullable String addressLevel3) {
                mBundle.putString(ADDRESS_LEVEL3_KEY, addressLevel3);
                return this;
            }

            /**
             * Set the postal code for this address entry.
             *
             * @param postalCode The postal code string.
             * @return This {@link WAutocomplete.Address.Builder} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.Address.Builder postalCode(final @Nullable String postalCode) {
                mBundle.putString(POSTAL_CODE_KEY, postalCode);
                return this;
            }

            /**
             * Set the country code for this address entry.
             *
             * @param country The country string.
             * @return This {@link WAutocomplete.Address.Builder} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.Address.Builder country(final @Nullable String country) {
                mBundle.putString(COUNTRY_KEY, country);
                return this;
            }

            /**
             * Set the telephone number for this address entry.
             *
             * @param tel The telephone number string.
             * @return This {@link WAutocomplete.Address.Builder} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.Address.Builder tel(final @Nullable String tel) {
                mBundle.putString(TEL_KEY, tel);
                return this;
            }

            /**
             * Set the email address for this address entry.
             *
             * @param email The email address string.
             * @return This {@link WAutocomplete.Address.Builder} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.Address.Builder email(final @Nullable String email) {
                mBundle.putString(EMAIL_KEY, email);
                return this;
            }
        }
    }

    /** Holds login information for a specific entry. */
    public static class LoginEntry {
        private static final String GUID_KEY = "guid";
        private static final String ORIGIN_KEY = "origin";
        private static final String FORM_ACTION_ORIGIN_KEY = "formActionOrigin";
        private static final String HTTP_REALM_KEY = "httpRealm";
        private static final String USERNAME_KEY = "username";
        private static final String PASSWORD_KEY = "password";

        /** The unique identifier for this login entry. */
        public final @Nullable String guid;

        /** The origin this login entry applies to. */
        public final @NonNull String origin;

        /**
         * The origin this login entry was submitted to. This only applies to form-based login entries.
         * It's derived from the action attribute set on the form element.
         */
        public final @Nullable String formActionOrigin;

        /**
         * The HTTP realm this login entry was requested for. This only applies to non-form-based login
         * entries. It's derived from the WWW-Authenticate header set in a HTTP 401 response, see
         * RFC2617 for details.
         */
        public final @Nullable String httpRealm;

        /** The username for this login entry. */
        public final @NonNull String username;

        /** The password for this login entry. */
        public final @NonNull String password;

        // For tests only.
        @AnyThread
        protected LoginEntry() {
            guid = null;
            origin = "";
            formActionOrigin = null;
            httpRealm = null;
            username = "";
            password = "";
        }

        @AnyThread
            /* package */ LoginEntry(final @NonNull Bundle bundle) {
            guid = bundle.getString(GUID_KEY);
            origin = bundle.getString(ORIGIN_KEY);
            formActionOrigin = bundle.getString(FORM_ACTION_ORIGIN_KEY);
            httpRealm = bundle.getString(HTTP_REALM_KEY);
            username = bundle.getString(USERNAME_KEY, "");
            password = bundle.getString(PASSWORD_KEY, "");
        }

        @Override
        @AnyThread
        public String toString() {
            final StringBuilder builder = new StringBuilder("LoginEntry {");
            builder
                    .append("guid=")
                    .append(guid)
                    .append(", origin=")
                    .append(origin)
                    .append(", formActionOrigin=")
                    .append(formActionOrigin)
                    .append(", httpRealm=")
                    .append(httpRealm)
                    .append(", username=")
                    .append(username)
                    .append(", password=")
                    .append(password)
                    .append("}");
            return builder.toString();
        }

        @AnyThread
        /* package */ @NonNull
        Bundle toBundle() {
            final Bundle bundle = new Bundle(6);
            bundle.putString(GUID_KEY, guid);
            bundle.putString(ORIGIN_KEY, origin);
            bundle.putString(FORM_ACTION_ORIGIN_KEY, formActionOrigin);
            bundle.putString(HTTP_REALM_KEY, httpRealm);
            bundle.putString(USERNAME_KEY, username);
            bundle.putString(PASSWORD_KEY, password);

            return bundle;
        }

        public static class Builder {
            private final Bundle mBundle;

            @AnyThread
                /* package */ Builder(final @NonNull Bundle bundle) {
                mBundle = new Bundle(bundle);
            }

            @AnyThread
            @SuppressWarnings("checkstyle:javadocmethod")
            public Builder() {
                mBundle = new Bundle(6);
            }

            /**
             * Finalize the {@link WAutocomplete.LoginEntry} instance.
             *
             * @return The {@link WAutocomplete.LoginEntry} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.LoginEntry build() {
                return new WAutocomplete.LoginEntry(mBundle);
            }

            /**
             * Set the unique identifier for this login entry.
             *
             * @param guid The unique identifier string.
             * @return This {@link WAutocomplete.LoginEntry.Builder} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.LoginEntry.Builder guid(final @Nullable String guid) {
                mBundle.putString(GUID_KEY, guid);
                return this;
            }

            /**
             * Set the origin this login entry applies to.
             *
             * @param origin The origin string.
             * @return This {@link WAutocomplete.LoginEntry.Builder} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.LoginEntry.Builder origin(final @NonNull String origin) {
                mBundle.putString(ORIGIN_KEY, origin);
                return this;
            }

            /**
             * Set the origin this login entry was submitted to.
             *
             * @param formActionOrigin The form action origin string.
             * @return This {@link WAutocomplete.LoginEntry.Builder} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.LoginEntry.Builder formActionOrigin(final @Nullable String formActionOrigin) {
                mBundle.putString(FORM_ACTION_ORIGIN_KEY, formActionOrigin);
                return this;
            }

            /**
             * Set the HTTP realm this login entry was requested for.
             *
             * @param httpRealm The HTTP realm string.
             * @return This {@link WAutocomplete.LoginEntry.Builder} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.LoginEntry.Builder httpRealm(final @Nullable String httpRealm) {
                mBundle.putString(HTTP_REALM_KEY, httpRealm);
                return this;
            }

            /**
             * Set the username for this login entry.
             *
             * @param username The username string.
             * @return This {@link WAutocomplete.LoginEntry.Builder} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.LoginEntry.Builder username(final @NonNull String username) {
                mBundle.putString(USERNAME_KEY, username);
                return this;
            }

            /**
             * Set the password for this login entry.
             *
             * @param password The password string.
             * @return This {@link WAutocomplete.LoginEntry.Builder} instance.
             */
            @AnyThread
            public @NonNull
            WAutocomplete.LoginEntry.Builder password(final @NonNull String password) {
                mBundle.putString(PASSWORD_KEY, password);
                return this;
            }
        }
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            flag = true,
            value = {WAutocomplete.UsedField.PASSWORD})
            /* package */ @interface LSUsedField {}

    // Sync with UsedField in BrowserAutocomplete.jsm.
    /** Possible login entry field types for {@link WAutocomplete.StorageDelegate#onLoginUsed}. */
    public static class UsedField {
        /** The password field of a login entry. */
        public static final int PASSWORD = 1;

        protected UsedField() {}
    }

    /**
     * Implement this interface to handle runtime login storage requests. Login storage events include
     * login entry requests for autofill and autocompletion of login input fields. This delegate is
     * attached to the runtime via {@link WRuntime#setAutocompleteStorageDelegate}.
     */
    public interface StorageDelegate {
        /**
         * Request login entries for a given domain. While processing the web document, we have
         * identified elements resembling login input fields suitable for autofill. We will attempt to
         * match the provided login information to the identified input fields.
         *
         * @param domain The domain string for the requested logins.
         * @return A {@link WResult} that completes with an array of {@link WAutocomplete.LoginEntry} containing
         *     the existing logins for the given domain.
         */
        @UiThread
        default @Nullable
        WResult<LoginEntry[]> onLoginFetch(@NonNull final String domain) {
            return null;
        }

        /**
         * Request login entries for all domains.
         *
         * @return A {@link WResult} that completes with an array of {@link WAutocomplete.LoginEntry} containing
         *     the existing logins.
         */
        @UiThread
        default @Nullable
        WResult<LoginEntry[]> onLoginFetch() {
            return null;
        }

        /**
         * Request credit card entries. While processing the web document, we have identified elements
         * resembling credit card input fields suitable for autofill. We will attempt to match the
         * provided credit card information to the identified input fields.
         *
         * @return A {@link WResult} that completes with an array of {@link WAutocomplete.CreditCard} containing
         *     the existing credit cards.
         */
        @UiThread
        default @Nullable
        WResult<CreditCard[]> onCreditCardFetch() {
            return null;
        }

        /**
         * Request address entries. While processing the web document, we have identified elements
         * resembling address input fields suitable for autofill. We will attempt to match the provided
         * address information to the identified input fields.
         *
         * @return A {@link WResult} that completes with an array of {@link WAutocomplete.Address} containing the
         *     existing addresses.
         */
        @UiThread
        default @Nullable
        WResult<Address[]> onAddressFetch() {
            return null;
        }

        /**
         * Request saving or updating of the given login entry. This is triggered by confirming a {@link
         * WSession.PromptDelegate#onLoginSave onLoginSave} request.
         *
         * @param login The {@link WAutocomplete.LoginEntry} as confirmed by the prompt request.
         */
        @UiThread
        default void onLoginSave(@NonNull final WAutocomplete.LoginEntry login) {}

        /**
         * Request saving or updating of the given credit card entry. This is triggered by confirming a
         * {@link WSession.PromptDelegate#onCreditCardSave onCreditCardSave} request.
         *
         * @param creditCard The {@link WAutocomplete.CreditCard} as confirmed by the prompt request.
         */
        @UiThread
        default void onCreditCardSave(@NonNull WAutocomplete.CreditCard creditCard) {}

        /**
         * Request saving or updating of the given address entry. This is triggered by confirming a
         * {@link WSession.PromptDelegate#onAddressSave onAddressSave} request.
         *
         * @param address The {@link WAutocomplete.Address} as confirmed by the prompt request.
         */
        @UiThread
        default void onAddressSave(@NonNull WAutocomplete.Address address) {}

        /**
         * Notify that the given login was used to autofill login input fields. This is triggered by
         * autofilling elements with unmodified login entries as provided via {@link #onLoginFetch}.
         *
         * @param login The {@link WAutocomplete.LoginEntry} that was used for the autofilling.
         * @param usedFields The login entry fields used for autofilling. A combination of {@link
         *     WAutocomplete.UsedField}.
         */
        @UiThread
        default void onLoginUsed(@NonNull final WAutocomplete.LoginEntry login, @LSUsedField final int usedFields) {}
    }

    /**
     * Abstract base class for Autocomplete options. Extended by {@link WAutocomplete.SaveOption} and
     * {@link WAutocomplete.SelectOption}.
     */
    public abstract static class Option<T> {
        /* package */ static final String VALUE_KEY = "value";
        /* package */ static final String HINT_KEY = "hint";

        public final @NonNull T value;
        public final int hint;

        @SuppressWarnings("checkstyle:javadocmethod")
        public Option(final @NonNull T value, final int hint) {
            this.value = value;
            this.hint = hint;
        }

        @AnyThread
        /* package */ abstract @NonNull Bundle toBundle();
    }

    /** Abstract base class for saving options. Extended by {@link WAutocomplete.LoginSaveOption}. */
    public abstract static class SaveOption<T> extends WAutocomplete.Option<T> {
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(
                flag = true,
                value = {WAutocomplete.SaveOption.Hint.NONE, WAutocomplete.SaveOption.Hint.GENERATED, WAutocomplete.SaveOption.Hint.LOW_CONFIDENCE})
                /* package */ @interface SaveOptionHint {}

        /** Hint types for login saving requests. */
        public static class Hint {
            public static final int NONE = 0;

            /** Auto-generated password. Notify but do not prompt the user for saving. */
            public static final int GENERATED = 1 << 0;

            /**
             * Potentially non-login data. The form data entered may be not login credentials but other
             * forms of input like credit card numbers. Note that this could be valid login data in same
             * cases, e.g., some banks may expect credit card numbers in the username field.
             */
            public static final int LOW_CONFIDENCE = 1 << 1;

            protected Hint() {}
        }

        @SuppressWarnings("checkstyle:javadocmethod")
        public SaveOption(final @NonNull T value, final @SaveOptionHint int hint) {
            super(value, hint);
        }
    }

    /** Abstract base class for saving options. Extended by {@link WAutocomplete.LoginSelectOption}. */
    public abstract static class SelectOption<T> extends WAutocomplete.Option<T> {
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(
                flag = true,
                value = {
                        WAutocomplete.SelectOption.Hint.NONE,
                        WAutocomplete.SelectOption.Hint.GENERATED,
                        WAutocomplete.SelectOption.Hint.INSECURE_FORM,
                        WAutocomplete.SelectOption.Hint.DUPLICATE_USERNAME,
                        WAutocomplete.SelectOption.Hint.MATCHING_ORIGIN
                })
                /* package */ @interface SelectOptionHint {}

        /** Hint types for selection requests. */
        public static class Hint {
            public static final int NONE = 0;

            /**
             * Auto-generated password. A new password-only login entry containing a secure generated
             * password.
             */
            public static final int GENERATED = 1 << 0;

            /**
             * Insecure context. The form or transmission mechanics are considered insecure. This is the
             * case when the form is served via http or submitted insecurely.
             */
            public static final int INSECURE_FORM = 1 << 1;

            /**
             * The username is shared with another login entry. There are multiple login entries in the
             * options that share the same username. You may have to disambiguate the login entry, e.g.,
             * using the last date of modification and its origin.
             */
            public static final int DUPLICATE_USERNAME = 1 << 2;

            /**
             * The login entry's origin matches the login form origin. The login was saved from the same
             * origin it is being requested for, rather than for a subdomain.
             */
            public static final int MATCHING_ORIGIN = 1 << 3;
        }

        @SuppressWarnings("checkstyle:javadocmethod")
        public SelectOption(final @NonNull T value, final @SelectOptionHint int hint) {
            super(value, hint);
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder("SelectOption {");
            builder.append("value=").append(value).append(", ").append("hint=").append(hint).append("}");
            return builder.toString();
        }
    }

    /** Holds information required to process login saving requests. */
    public static class LoginSaveOption extends WAutocomplete.SaveOption<WAutocomplete.LoginEntry> {
        /**
         * Construct a login save option.
         *
         * @param value The {@link WAutocomplete.LoginEntry} login entry to be saved.
         * @param hint The {@link Hint} detailing the type of the option.
         */
        /* package */ LoginSaveOption(final @NonNull WAutocomplete.LoginEntry value, final @SaveOptionHint int hint) {
            super(value, hint);
        }

        /**
         * Construct a login save option.
         *
         * @param value The {@link WAutocomplete.LoginEntry} login entry to be saved.
         */
        public LoginSaveOption(final @NonNull WAutocomplete.LoginEntry value) {
            this(value, Hint.NONE);
        }

        @Override
        /* package */ @NonNull
        Bundle toBundle() {
            final Bundle bundle = new Bundle(2);
            bundle.putBundle(VALUE_KEY, value.toBundle());
            bundle.putInt(HINT_KEY, hint);
            return bundle;
        }
    }

    /** Holds information required to process address saving requests. */
    public static class AddressSaveOption extends WAutocomplete.SaveOption<WAutocomplete.Address> {
        /**
         * Construct a address save option.
         *
         * @param value The {@link WAutocomplete.Address} address entry to be saved.
         * @param hint The {@link Hint} detailing the type of the option.
         */
        /* package */ AddressSaveOption(final @NonNull WAutocomplete.Address value, final @SaveOptionHint int hint) {
            super(value, hint);
        }

        /**
         * Construct an address save option.
         *
         * @param value The {@link WAutocomplete.Address} address entry to be saved.
         */
        public AddressSaveOption(final @NonNull WAutocomplete.Address value) {
            this(value, Hint.NONE);
        }

        @Override
        /* package */ @NonNull
        Bundle toBundle() {
            final Bundle bundle = new Bundle(2);
            bundle.putBundle(VALUE_KEY, value.toBundle());
            bundle.putInt(HINT_KEY, hint);
            return bundle;
        }
    }

    /** Holds information required to process credit card saving requests. */
    public static class CreditCardSaveOption extends WAutocomplete.SaveOption<WAutocomplete.CreditCard> {
        /**
         * Construct a credit card save option.
         *
         * @param value The {@link WAutocomplete.CreditCard} credit card entry to be saved.
         * @param hint The {@link Hint} detailing the type of the option.
         */
        /* package */ CreditCardSaveOption(
                final @NonNull WAutocomplete.CreditCard value, final @SaveOptionHint int hint) {
            super(value, hint);
        }

        /**
         * Construct a credit card save option.
         *
         * @param value The {@link WAutocomplete.CreditCard} credit card entry to be saved.
         */
        public CreditCardSaveOption(final @NonNull WAutocomplete.CreditCard value) {
            this(value, Hint.NONE);
        }

        @Override
        /* package */ @NonNull
        Bundle toBundle() {
            final Bundle bundle = new Bundle(2);
            bundle.putBundle(VALUE_KEY, value.toBundle());
            bundle.putInt(HINT_KEY, hint);
            return bundle;
        }
    }

    /** Holds information required to process login selection requests. */
    public static class LoginSelectOption extends WAutocomplete.SelectOption<WAutocomplete.LoginEntry> {
        /**
         * Construct a login select option.
         *
         * @param value The {@link WAutocomplete.LoginEntry} login entry selection option.
         * @param hint The {@link Hint} detailing the type of the option.
         */
        /* package */ LoginSelectOption(
                final @NonNull WAutocomplete.LoginEntry value, final @SelectOptionHint int hint) {
            super(value, hint);
        }

        /**
         * Construct a login select option.
         *
         * @param value The {@link WAutocomplete.LoginEntry} login entry selection option.
         */
        public LoginSelectOption(final @NonNull WAutocomplete.LoginEntry value) {
            this(value, Hint.NONE);
        }

        /* package */ static @NonNull
        WAutocomplete.LoginSelectOption fromBundle(final @NonNull Bundle bundle) {
            final int hint = bundle.getInt("hint");
            final WAutocomplete.LoginEntry value = new WAutocomplete.LoginEntry(bundle.getBundle("value"));

            return new WAutocomplete.LoginSelectOption(value, hint);
        }

        @Override
        /* package */ @NonNull
        Bundle toBundle() {
            final Bundle bundle = new Bundle(2);
            bundle.putBundle(VALUE_KEY, value.toBundle());
            bundle.putInt(HINT_KEY, hint);
            return bundle;
        }
    }

    /** Holds information required to process credit card selection requests. */
    public static class CreditCardSelectOption extends WAutocomplete.SelectOption<WAutocomplete.CreditCard> {
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(
                flag = true,
                value = {WAutocomplete.CreditCardSelectOption.Hint.NONE, WAutocomplete.CreditCardSelectOption.Hint.INSECURE_FORM})
                /* package */ @interface CreditCardSelectHint {}

        /** Hint types for credit card selection requests. */
        public static class Hint {
            public static final int NONE = 0;

            /**
             * Insecure context. The form or transmission mechanics are considered insecure. This is the
             * case when the form is served via http or submitted insecurely.
             */
            public static final int INSECURE_FORM = 1 << 1;
        }

        /**
         * Construct a credit card select option.
         *
         * @param value The {@link WAutocomplete.LoginEntry} credit card entry selection option.
         * @param hint The {@link WAutocomplete.CreditCardSelectOption.Hint} detailing the type of the option.
         */
        /* package */ CreditCardSelectOption(
                final @NonNull WAutocomplete.CreditCard value, final @CreditCardSelectHint int hint) {
            super(value, hint);
        }

        /**
         * Construct a credit card select option.
         *
         * @param value The {@link WAutocomplete.CreditCard} credit card entry selection option.
         */
        public CreditCardSelectOption(final @NonNull WAutocomplete.CreditCard value) {
            this(value, WAutocomplete.CreditCardSelectOption.Hint.NONE);
        }

        /* package */ static @NonNull
        WAutocomplete.CreditCardSelectOption fromBundle(
                final @NonNull Bundle bundle) {
            final int hint = bundle.getInt("hint");
            final WAutocomplete.CreditCard value = new WAutocomplete.CreditCard(bundle.getBundle("value"));

            return new WAutocomplete.CreditCardSelectOption(value, hint);
        }

        @Override
        /* package */ @NonNull
        Bundle toBundle() {
            final Bundle bundle = new Bundle(2);
            bundle.putBundle(VALUE_KEY, value.toBundle());
            bundle.putInt(HINT_KEY, hint);
            return bundle;
        }
    }

    /** Holds information required to process address selection requests. */
    public static class AddressSelectOption extends WAutocomplete.SelectOption<WAutocomplete.Address> {
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(
                flag = true,
                value = {WAutocomplete.AddressSelectOption.Hint.NONE, WAutocomplete.AddressSelectOption.Hint.INSECURE_FORM})
                /* package */ @interface AddressSelectHint {}

        /** Hint types for credit card selection requests. */
        public static class Hint {
            public static final int NONE = 0;

            /**
             * Insecure context. The form or transmission mechanics are considered insecure. This is the
             * case when the form is served via http or submitted insecurely.
             */
            public static final int INSECURE_FORM = 1 << 1;
        }

        /**
         * Construct a credit card select option.
         *
         * @param value The {@link WAutocomplete.LoginEntry} credit card entry selection option.
         * @param hint The {@link WAutocomplete.AddressSelectOption.Hint} detailing the type of the option.
         */
        /* package */ AddressSelectOption(
                final @NonNull WAutocomplete.Address value, final @AddressSelectHint int hint) {
            super(value, hint);
        }

        /**
         * Construct a address select option.
         *
         * @param value The {@link WAutocomplete.Address} address entry selection option.
         */
        public AddressSelectOption(final @NonNull WAutocomplete.Address value) {
            this(value, WAutocomplete.AddressSelectOption.Hint.NONE);
        }

        /* package */ static @NonNull
        WAutocomplete.AddressSelectOption fromBundle(
                final @NonNull Bundle bundle) {
            final int hint = bundle.getInt("hint");
            final WAutocomplete.Address value = new WAutocomplete.Address(bundle.getBundle("value"));

            return new WAutocomplete.AddressSelectOption(value, hint);
        }

        @Override
        /* package */ @NonNull
        Bundle toBundle() {
            final Bundle bundle = new Bundle(2);
            bundle.putBundle(VALUE_KEY, value.toBundle());
            bundle.putInt(HINT_KEY, hint);
            return bundle;
        }
    }
}

