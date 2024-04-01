package com.igalia.wolvic.browser.api.impl;

import androidx.annotation.NonNull;

import com.igalia.wolvic.browser.api.WAutocomplete;
import com.igalia.wolvic.browser.api.WResult;

import java.util.ArrayList;

import org.chromium.wolvic.PasswordForm;
import org.chromium.wolvic.PasswordStoreBackend;

public class AutocompleteStorageProxy implements PasswordStoreBackend.Bridge {
    private AutocompleteDelegateWrapper mDelegate;
    private PasswordStoreBackend.Listener mListener;

    private class FactoryImpl implements PasswordStoreBackend.Factory {
        @Override
        public PasswordStoreBackend.Bridge create(PasswordStoreBackend.Listener listener) {
            AutocompleteStorageProxy.this.mListener = listener;
            return AutocompleteStorageProxy.this;
        }
    }

    public AutocompleteStorageProxy() {
        PasswordStoreBackend.setFactory(new FactoryImpl());
    }

    private @NonNull PasswordForm toPasswordForm(@NonNull WAutocomplete.LoginEntry entry) {
        return new PasswordForm(entry.username, entry.password, entry.origin,
                entry.formActionOrigin, entry.httpRealm, entry.guid);
    }

    private @NonNull WAutocomplete.LoginEntry fromPasswordForm(@NonNull PasswordForm form) {
        return new WAutocomplete.LoginEntry.Builder()
                .formActionOrigin(form.getFormActionOrigin())
                .guid(form.getGuid())
                .httpRealm(form.getHttpRealm())
                .origin(form.getOrigin())
                .password(form.getPassword())
                .username(form.getUsername())
                .build();
    }

    @Override
    public void addLogin(int replyId, PasswordForm form) {
        updateLogin(replyId, form);
    }

    @Override
    public void updateLogin(int replyId, PasswordForm form) {
        try {
            mDelegate.onLoginSave(fromPasswordForm(form));
            mListener.onLoginChanged(replyId);
        } catch (Exception e) {
            mListener.onError(replyId, e);
        }
    }

    @Override
    public void removeLogin(int replyId, PasswordForm form) {
        // Not implemented. Removing UI is only placed in GeckoView that accesses directly to
        // LogingStorage for removing Login entry.
        mListener.onError(replyId, new RuntimeException("Not implemented"));
    }

    @Override
    public void getAllLogins(int replyId) {
        final WResult<WAutocomplete.LoginEntry[]> result = mDelegate.onLoginFetch();
        if (result == null) {
            mListener.onCompleteWithLogins(replyId, null);
            return;
        }
        result.then(entries -> {
            PasswordForm[] forms = new PasswordForm[entries.length];
            for (int i = 0; i < entries.length; ++i) {
                forms[i] = toPasswordForm(entries[i]);
            }
            mListener.onCompleteWithLogins(replyId, forms);
            return null;
        }).exceptionally(ex -> {
            mListener.onError(replyId, (Exception)ex);
            return null;
        });
    }

    @Override
    public void getLoginsForSignonRealm(int replyId, String signonRealm) {
        final WResult<WAutocomplete.LoginEntry[]> result = mDelegate.onLoginFetch(signonRealm);
        if (result == null) {
            mListener.onCompleteWithLogins(replyId, null);
            return;
        }
        result.then(entries -> {
            ArrayList<PasswordForm> forms = new ArrayList<>();
            for (int i = 0; i < entries.length; ++i) {
                forms.add(toPasswordForm(entries[i]));
            }

            mListener.onCompleteWithLogins(replyId, forms.toArray(new PasswordForm[forms.size()]));
            return null;
        }).exceptionally(ex -> {
            mListener.onError(replyId, (Exception) ex);
            return null;
        });
    }

    @Override
    public void getAutofillableLogins(int replyId) {
        getAllLogins(replyId);
    }

    public void onLoginUsed(PasswordForm form) {
        mDelegate.onLoginUsed(fromPasswordForm(form), WAutocomplete.UsedField.PASSWORD);
    }

    public WResult<Boolean> checkLoginIfAlreadySaved(PasswordForm form) {
        final WResult<WAutocomplete.LoginEntry> found = mDelegate.findLoginByGuid(form.getGuid());
        if (found == null) {
            return WResult.fromValue(false);
        }
        return found.then(result -> {
            return WResult.fromValue(result != null ? true : false);
        });
    }

    public void setDelegate(AutocompleteDelegateWrapper delegate) {
        mDelegate = delegate;
    }
}
