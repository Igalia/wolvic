package com.igalia.wolvic.browser.api.impl;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WAllowOrDeny;
import com.igalia.wolvic.browser.api.WAutocomplete;
import com.igalia.wolvic.browser.api.WResult;
import com.igalia.wolvic.browser.api.WSession;

import org.mozilla.geckoview.Autocomplete;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

import java.util.Arrays;

class PromptDelegateImpl implements GeckoSession.PromptDelegate {
    WSession.PromptDelegate mDelegate;
    SessionImpl mSession;

    public PromptDelegateImpl(WSession.PromptDelegate mDelegate, SessionImpl mSession) {
        this.mDelegate = mDelegate;
        this.mSession = mSession;
    }

    private static class PromptResponseImpl implements WSession.PromptDelegate.PromptResponse {
        public PromptResponseImpl(PromptResponse mGeckoResponse) {
            this.mGeckoResponse = mGeckoResponse;
        }

        GeckoSession.PromptDelegate.PromptResponse mGeckoResponse;
    }

    private static abstract class BasePromptImpl<T extends GeckoSession.PromptDelegate.BasePrompt> implements WSession.PromptDelegate.BasePrompt {
        public BasePromptImpl(T geckoPrompt) {
            this.mGeckoPrompt = geckoPrompt;
        }

        protected T mGeckoPrompt;
        private WSession.PromptDelegate.PromptInstanceDelegate mDelegate;

        @Nullable
        @Override
        public String title() {
            return mGeckoPrompt.title;
        }

        @NonNull
        @Override
        public WSession.PromptDelegate.PromptResponse dismiss() {
            return new PromptResponseImpl(mGeckoPrompt.dismiss());
        }

        @Override
        public void setDelegate(@Nullable WSession.PromptDelegate.PromptInstanceDelegate delegate) {
            if (mDelegate == delegate) {
                return;
            }
            mDelegate = delegate;
            if (mDelegate == null) {
                mGeckoPrompt.setDelegate(null);
            } else {
                mGeckoPrompt.setDelegate(new PromptInstanceDelegate() {
                    @Override
                    public void onPromptDismiss(@NonNull BasePrompt prompt) {
                        mDelegate.onPromptDismiss(BasePromptImpl.this);
                    }
                });
            }
        }

        @Nullable
        @Override
        public WSession.PromptDelegate.PromptInstanceDelegate getDelegate() {
            return null;
        }

        @Override
        public boolean isComplete() {
            return mGeckoPrompt.isComplete();
        }
    }

    private static abstract class AlertPromptImpl extends BasePromptImpl<AlertPrompt> implements WSession.PromptDelegate.AlertPrompt {
        public AlertPromptImpl(AlertPrompt geckoPrompt) {
            super(geckoPrompt);
        }
    }

    private static abstract class BeforeUnloadPromptImpl extends BasePromptImpl<BeforeUnloadPrompt> implements WSession.PromptDelegate.BeforeUnloadPrompt {
        public BeforeUnloadPromptImpl(BeforeUnloadPrompt geckoPrompt) {
            super(geckoPrompt);
        }
    }

    private static abstract class RepostConfirmPromptImpl extends BasePromptImpl<RepostConfirmPrompt> implements WSession.PromptDelegate.RepostConfirmPrompt {
        public RepostConfirmPromptImpl(RepostConfirmPrompt geckoPrompt) {
            super(geckoPrompt);
        }
    }

    private static abstract class ButtonPromptImpl extends BasePromptImpl<ButtonPrompt> implements WSession.PromptDelegate.ButtonPrompt {
        public ButtonPromptImpl(ButtonPrompt geckoPrompt) {
            super(geckoPrompt);
        }
    }

    private static abstract class TextPromptImpl extends BasePromptImpl<TextPrompt> implements WSession.PromptDelegate.TextPrompt {
        public TextPromptImpl(TextPrompt geckoPrompt) {
            super(geckoPrompt);
        }
    }

    private static abstract class AuthPromptImpl extends BasePromptImpl<AuthPrompt> implements WSession.PromptDelegate.AuthPrompt {
        public AuthPromptImpl(AuthPrompt geckoPrompt) {
            super(geckoPrompt);
        }
    }

    private static abstract class ChoicePromptImpl extends BasePromptImpl<ChoicePrompt> implements WSession.PromptDelegate.ChoicePrompt {
        public ChoicePromptImpl(ChoicePrompt geckoPrompt) {
            super(geckoPrompt);
        }
    }

    private static abstract class ColorPromptImpl extends BasePromptImpl<ColorPrompt> implements WSession.PromptDelegate.ColorPrompt {
        public ColorPromptImpl(ColorPrompt geckoPrompt) {
            super(geckoPrompt);
        }
    }

    private static abstract class DateTimePromptImpl extends BasePromptImpl<DateTimePrompt> implements WSession.PromptDelegate.DateTimePrompt {
        public DateTimePromptImpl(DateTimePrompt geckoPrompt) {
            super(geckoPrompt);
        }
    }

    private static abstract class FilePromptImpl extends BasePromptImpl<FilePrompt> implements WSession.PromptDelegate.FilePrompt {
        public FilePromptImpl(FilePrompt geckoPrompt) {
            super(geckoPrompt);
        }
    }

    private static abstract class PopupPromptImpl extends BasePromptImpl<PopupPrompt> implements WSession.PromptDelegate.PopupPrompt {
        public PopupPromptImpl(PopupPrompt geckoPrompt) {
            super(geckoPrompt);
        }
    }

    private static abstract class SharePromptImpl extends BasePromptImpl<SharePrompt> implements WSession.PromptDelegate.SharePrompt {
        public SharePromptImpl(SharePrompt geckoPrompt) {
            super(geckoPrompt);
        }
    }

    private static abstract class LoginSaveImpl extends BasePromptImpl<AutocompleteRequest<Autocomplete.LoginSaveOption>>
            implements WSession.PromptDelegate.AutocompleteRequest<WAutocomplete.LoginSaveOption> {
        public LoginSaveImpl(AutocompleteRequest<Autocomplete.LoginSaveOption> geckoPrompt) {
            super(geckoPrompt);
        }
    }

    private static abstract class LoginSelectImpl extends BasePromptImpl<AutocompleteRequest<Autocomplete.LoginSelectOption>>
            implements WSession.PromptDelegate.AutocompleteRequest<WAutocomplete.LoginSelectOption> {
        public LoginSelectImpl(AutocompleteRequest<Autocomplete.LoginSelectOption> geckoPrompt) {
            super(geckoPrompt);
        }
    }

    private static abstract class AddressSaveImpl extends BasePromptImpl<AutocompleteRequest<Autocomplete.AddressSaveOption>>
            implements WSession.PromptDelegate.AutocompleteRequest<WAutocomplete.AddressSaveOption> {
        public AddressSaveImpl(AutocompleteRequest<Autocomplete.AddressSaveOption> geckoPrompt) {
            super(geckoPrompt);
        }
    }

    private static abstract class AddressSelectImpl extends BasePromptImpl<AutocompleteRequest<Autocomplete.AddressSelectOption>>
            implements WSession.PromptDelegate.AutocompleteRequest<WAutocomplete.AddressSelectOption> {
        public AddressSelectImpl(AutocompleteRequest<Autocomplete.AddressSelectOption> geckoPrompt) {
            super(geckoPrompt);
        }
    }

    private static abstract class CreditCardSaveImpl extends BasePromptImpl<AutocompleteRequest<Autocomplete.CreditCardSaveOption>>
            implements WSession.PromptDelegate.AutocompleteRequest<WAutocomplete.CreditCardSaveOption> {
        public CreditCardSaveImpl(AutocompleteRequest<Autocomplete.CreditCardSaveOption> geckoPrompt) {
            super(geckoPrompt);
        }
    }

    private static abstract class CreditCardSelectImpl extends BasePromptImpl<AutocompleteRequest<Autocomplete.CreditCardSelectOption>>
            implements WSession.PromptDelegate.AutocompleteRequest<WAutocomplete.CreditCardSelectOption> {
        public CreditCardSelectImpl(AutocompleteRequest<Autocomplete.CreditCardSelectOption> geckoPrompt) {
            super(geckoPrompt);
        }
    }


    @Nullable
    @Override
    public GeckoResult<PromptResponse> onAlertPrompt(@NonNull GeckoSession session, @NonNull AlertPrompt prompt) {
        return map(mDelegate.onAlertPrompt(mSession, new AlertPromptImpl(prompt) {
            @Nullable
            @Override
            public String message() {
                return mGeckoPrompt.message;
            }
        }));
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onBeforeUnloadPrompt(@NonNull GeckoSession session, @NonNull BeforeUnloadPrompt prompt) {
        return map(mDelegate.onBeforeUnloadPrompt(mSession, new BeforeUnloadPromptImpl(prompt) {
            @NonNull
            @Override
            public WSession.PromptDelegate.PromptResponse confirm(@Nullable WAllowOrDeny allowOrDeny) {
                return new PromptResponseImpl(mGeckoPrompt.confirm(toGecko(allowOrDeny)));
            }
        }));
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onRepostConfirmPrompt(@NonNull GeckoSession session, @NonNull RepostConfirmPrompt prompt) {
        return map(mDelegate.onRepostConfirmPrompt(mSession, new RepostConfirmPromptImpl(prompt) {
            @NonNull
            @Override
            public WSession.PromptDelegate.PromptResponse confirm(@Nullable WAllowOrDeny allowOrDeny) {
                return new PromptResponseImpl(mGeckoPrompt.confirm(toGecko(allowOrDeny)));
            }
        }));
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onButtonPrompt(@NonNull GeckoSession session, @NonNull ButtonPrompt prompt) {
        return map(mDelegate.onButtonPrompt(mSession, new ButtonPromptImpl(prompt) {
            @Nullable
            @Override
            public String message() {
                return mGeckoPrompt.message;
            }

            @NonNull
            @Override
            public WSession.PromptDelegate.PromptResponse confirm(int selection) {
                return new PromptResponseImpl(mGeckoPrompt.confirm(selection));
            }
        }));
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onTextPrompt(@NonNull GeckoSession session, @NonNull TextPrompt prompt) {
        return map(mDelegate.onTextPrompt(mSession, new TextPromptImpl(prompt) {
            @Nullable
            @Override
            public String message() {
                return mGeckoPrompt.message;
            }

            @Nullable
            @Override
            public String defaultValue() {
                return mGeckoPrompt.defaultValue;
            }

            @NonNull
            @Override
            public WSession.PromptDelegate.PromptResponse confirm(@NonNull String text) {
                return new PromptResponseImpl(mGeckoPrompt.confirm(text));
            }
        }));
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onAuthPrompt(@NonNull GeckoSession session, @NonNull AuthPrompt prompt) {
        return map(mDelegate.onAuthPrompt(mSession, new AuthPromptImpl(prompt) {
            @Nullable
            @Override
            public String message() {
                return mGeckoPrompt.message;
            }

            @NonNull
            @Override
            public AuthOptions authOptions() {
                int flags = 0;
                if ((mGeckoPrompt.authOptions.flags & AuthPrompt.AuthOptions.Flags.CROSS_ORIGIN_SUB_RESOURCE) != 0) {
                    flags |= AuthOptions.Flags.CROSS_ORIGIN_SUB_RESOURCE;
                }
                if ((mGeckoPrompt.authOptions.flags & AuthPrompt.AuthOptions.Flags.HOST) != 0) {
                    flags |= AuthOptions.Flags.HOST;
                }
                if ((mGeckoPrompt.authOptions.flags & AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD) != 0) {
                    flags |= AuthOptions.Flags.ONLY_PASSWORD;
                }
                if ((mGeckoPrompt.authOptions.flags & AuthPrompt.AuthOptions.Flags.PREVIOUS_FAILED) != 0) {
                    flags |= AuthOptions.Flags.PREVIOUS_FAILED;
                }
                if ((mGeckoPrompt.authOptions.flags & AuthPrompt.AuthOptions.Flags.PROXY) != 0) {
                    flags |= AuthOptions.Flags.PROXY;
                }

                int level = 0;
                switch (mGeckoPrompt.authOptions.level) {
                    case AuthPrompt.AuthOptions.Level.NONE:
                        level = AuthOptions.Level.NONE;
                        break;
                    case AuthPrompt.AuthOptions.Level.PW_ENCRYPTED:
                        level = AuthOptions.Level.PW_ENCRYPTED;
                        break;
                    case AuthPrompt.AuthOptions.Level.SECURE:
                        level = AuthOptions.Level.SECURE;
                        break;
                }

                return new AuthOptions(flags, mGeckoPrompt.authOptions.uri, level, mGeckoPrompt.authOptions.username, mGeckoPrompt.authOptions.password);
            }

            @NonNull
            @Override
            public WSession.PromptDelegate.PromptResponse confirm(@NonNull String password) {
                return new PromptResponseImpl(mGeckoPrompt.confirm(password));
            }

            @NonNull
            @Override
            public WSession.PromptDelegate.PromptResponse confirm(@NonNull String username, @NonNull String password) {
                return new PromptResponseImpl(mGeckoPrompt.confirm(username, password));
            }
        }));
    }

    private static class ChoiceImpl implements WSession.PromptDelegate.ChoicePrompt.Choice {
        public ChoiceImpl(ChoicePrompt.Choice mGeckoChoice) {
            this.mGeckoChoice = mGeckoChoice;
        }

        ChoicePrompt.Choice mGeckoChoice;

        @Override
        public boolean disabled() {
            return mGeckoChoice.disabled;
        }

        @Nullable
        @Override
        public String icon() {
            return mGeckoChoice.icon;
        }

        @NonNull
        @Override
        public String id() {
            return mGeckoChoice.id;
        }

        @Nullable
        @Override
        public WSession.PromptDelegate.ChoicePrompt.Choice[] items() {
            if (mGeckoChoice.items == null) {
                return null;
            }
            return Arrays.stream(mGeckoChoice.items).map(ChoiceImpl::new).toArray(WSession.PromptDelegate.ChoicePrompt.Choice[]::new);
        }

        @NonNull
        @Override
        public String label() {
            return mGeckoChoice.label;
        }

        @Override
        public boolean selected() {
            return mGeckoChoice.selected;
        }

        @Override
        public boolean separator() {
            return mGeckoChoice.separator;
        }

        static ChoicePrompt.Choice toGecko(WSession.PromptDelegate.ChoicePrompt.Choice choice) {
            return ((ChoiceImpl)choice).mGeckoChoice;
        }
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onChoicePrompt(@NonNull GeckoSession session, @NonNull ChoicePrompt prompt) {
        return map(mDelegate.onChoicePrompt(mSession, new ChoicePromptImpl(prompt) {
            @Nullable
            @Override
            public String message() {
                return mGeckoPrompt.message;
            }

            @Override
            public int type() {
                switch (mGeckoPrompt.type) {
                    case ChoicePrompt.Type.MENU:
                        return Type.MENU;
                    case ChoicePrompt.Type.MULTIPLE:
                        return Type.MULTIPLE;
                    case ChoicePrompt.Type.SINGLE:
                    default:
                        return Type.SINGLE;
                }
            }

            @NonNull
            @Override
            public Choice[] choices() {
                return Arrays.stream(mGeckoPrompt.choices).map(ChoiceImpl::new).toArray(Choice[]::new);
            }

            @NonNull
            @Override
            public WSession.PromptDelegate.PromptResponse confirm(@NonNull String selectedId) {
                return new PromptResponseImpl(mGeckoPrompt.confirm(selectedId));
            }

            @NonNull
            @Override
            public WSession.PromptDelegate.PromptResponse confirm(@NonNull String[] selectedIds) {
                return new PromptResponseImpl(mGeckoPrompt.confirm(selectedIds));
            }

            @NonNull
            @Override
            public WSession.PromptDelegate.PromptResponse confirm(@NonNull Choice selectedChoice) {
                return new PromptResponseImpl(mGeckoPrompt.confirm(ChoiceImpl.toGecko(selectedChoice)));
            }

            @NonNull
            @Override
            public WSession.PromptDelegate.PromptResponse confirm(@NonNull Choice[] selectedChoices) {
                return new PromptResponseImpl(mGeckoPrompt.confirm(Arrays.
                        stream(selectedChoices).
                        map(ChoiceImpl::toGecko).
                        toArray(ChoicePrompt.Choice[]::new)));
            }
        }));
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onColorPrompt(@NonNull GeckoSession session, @NonNull ColorPrompt prompt) {
        return map(mDelegate.onColorPrompt(mSession, new ColorPromptImpl(prompt) {
            @Nullable
            @Override
            public String defaultValue() {
                return mGeckoPrompt.defaultValue;
            }

            @NonNull
            @Override
            public WSession.PromptDelegate.PromptResponse confirm(@NonNull String color) {
                return new PromptResponseImpl(mGeckoPrompt.confirm(color));
            }
        }));
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onDateTimePrompt(@NonNull GeckoSession session, @NonNull DateTimePrompt prompt) {
        return map(mDelegate.onDateTimePrompt(mSession, new DateTimePromptImpl(prompt) {
            @Override
            public int type() {
                switch (mGeckoPrompt.type) {
                    case DateTimePrompt.Type.DATE:
                        return Type.DATE;
                    case DateTimePrompt.Type.DATETIME_LOCAL:
                        return Type.DATETIME_LOCAL;
                    case DateTimePrompt.Type.MONTH:
                        return Type.MONTH;
                    case DateTimePrompt.Type.TIME:
                        return Type.TIME;
                    case DateTimePrompt.Type.WEEK:
                        return Type.WEEK;
                }

                throw new RuntimeException("Unhandled DateTimePrompt.Type value");
            }

            @Nullable
            @Override
            public String defaultValue() {
                return mGeckoPrompt.defaultValue;
            }

            @Nullable
            @Override
            public String minValue() {
                return mGeckoPrompt.minValue;
            }

            @Nullable
            @Override
            public String maxValue() {
                return mGeckoPrompt.maxValue;
            }

            @NonNull
            @Override
            public WSession.PromptDelegate.PromptResponse confirm(@NonNull String datetime) {
                return new PromptResponseImpl(mGeckoPrompt.confirm(datetime));
            }
        }));
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onFilePrompt(@NonNull GeckoSession session, @NonNull FilePrompt prompt) {
        return map(mDelegate.onFilePrompt(mSession, new FilePromptImpl(prompt) {
            @Override
            public int type() {
                switch (mGeckoPrompt.type) {
                    case FilePrompt.Type.MULTIPLE:
                        return Type.MULTIPLE;
                    case FilePrompt.Type.SINGLE:
                        return Type.SINGLE;
                }
                throw new RuntimeException("Unhandled FilePrompt.Type value");
            }

            @Nullable
            @Override
            public String[] mimeTypes() {
                return mGeckoPrompt.mimeTypes;
            }

            @Override
            public int captureType() {
                switch (mGeckoPrompt.capture) {
                    case FilePrompt.Capture.ANY:
                        return Capture.ANY;
                    case FilePrompt.Capture.ENVIRONMENT:
                        return Capture.ENVIRONMENT;
                    case FilePrompt.Capture.NONE:
                        return Capture.NONE;
                    case FilePrompt.Capture.USER:
                        return Capture.USER;
                }
                throw new RuntimeException("Unhandled FilePrompt.Capture value");
            }

            @NonNull
            @Override
            public WSession.PromptDelegate.PromptResponse confirm(@NonNull Context context, @NonNull Uri uri) {
                return new PromptResponseImpl(mGeckoPrompt.confirm(context, uri));
            }

            @NonNull
            @Override
            public WSession.PromptDelegate.PromptResponse confirm(@NonNull Context context, @NonNull Uri[] uris) {
                return new PromptResponseImpl(mGeckoPrompt.confirm(context, uris));
            }
        }));
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onPopupPrompt(@NonNull GeckoSession session, @NonNull PopupPrompt prompt) {
        return map(mDelegate.onPopupPrompt(mSession, new PopupPromptImpl(prompt) {

            @Nullable
            @Override
            public String targetUri() {
                return mGeckoPrompt.targetUri;
            }

            @NonNull
            @Override
            public WSession.PromptDelegate.PromptResponse confirm(@NonNull WAllowOrDeny response) {
                return new PromptResponseImpl(mGeckoPrompt.confirm(toGecko(response)));
            }
        }));
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onSharePrompt(@NonNull GeckoSession session, @NonNull SharePrompt prompt) {
        return map(mDelegate.onSharePrompt(mSession, new SharePromptImpl(prompt) {

            @Nullable
            @Override
            public String text() {
                return mGeckoPrompt.text;
            }

            @Nullable
            @Override
            public String uri() {
                return mGeckoPrompt.uri;
            }

            @NonNull
            @Override
            public WSession.PromptDelegate.PromptResponse confirm(int response) {
                return new PromptResponseImpl(mGeckoPrompt.confirm(response));
            }
        }));
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onLoginSave(@NonNull GeckoSession session, @NonNull AutocompleteRequest<Autocomplete.LoginSaveOption> request) {
        return map(mDelegate.onLoginSave(mSession, new LoginSaveImpl(request) {
            @NonNull
            @Override
            public WAutocomplete.LoginSaveOption[] options() {
                return Arrays.stream(request.options)
                        .map(opt -> new WAutocomplete.LoginSaveOption(fromGecko(opt.value)))
                        .toArray(WAutocomplete.LoginSaveOption[]::new);
            }

            @NonNull
            @Override
            public WSession.PromptDelegate.PromptResponse confirm(@NonNull WAutocomplete.Option<?> selection) {
                return new PromptResponseImpl(mGeckoPrompt.confirm(
                        new Autocomplete.LoginSaveOption(toGecko((WAutocomplete.LoginEntry) selection.value)))
                );
            }
        }));
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onAddressSave(@NonNull GeckoSession session, @NonNull AutocompleteRequest<Autocomplete.AddressSaveOption> request) {
        return map(mDelegate.onAddressSave(mSession, new AddressSaveImpl(request) {
            @NonNull
            @Override
            public WAutocomplete.AddressSaveOption[] options() {
                return Arrays.stream(request.options)
                        .map(opt -> new WAutocomplete.AddressSaveOption(fromGecko(opt.value)))
                        .toArray(WAutocomplete.AddressSaveOption[]::new);
            }

            @NonNull
            @Override
            public WSession.PromptDelegate.PromptResponse confirm(@NonNull WAutocomplete.Option<?> selection) {
                return new PromptResponseImpl(mGeckoPrompt.confirm(
                        new Autocomplete.AddressSaveOption(toGecko((WAutocomplete.Address) selection.value)))
                );
            }
        }));
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onCreditCardSave(@NonNull GeckoSession session, @NonNull AutocompleteRequest<Autocomplete.CreditCardSaveOption> request) {
        return map(mDelegate.onCreditCardSave(mSession, new CreditCardSaveImpl(request) {
            @NonNull
            @Override
            public WAutocomplete.CreditCardSaveOption[] options() {
                return Arrays.stream(request.options)
                        .map(opt -> new WAutocomplete.CreditCardSaveOption(fromGecko(opt.value)))
                        .toArray(WAutocomplete.CreditCardSaveOption[]::new);
            }

            @NonNull
            @Override
            public WSession.PromptDelegate.PromptResponse confirm(@NonNull WAutocomplete.Option<?> selection) {
                return new PromptResponseImpl(mGeckoPrompt.confirm(
                        new Autocomplete.CreditCardSaveOption(toGecko((WAutocomplete.CreditCard) selection.value)))
                );
            }
        }));
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onLoginSelect(@NonNull GeckoSession session, @NonNull AutocompleteRequest<Autocomplete.LoginSelectOption> request) {
        return map(mDelegate.onLoginSelect(mSession, new LoginSelectImpl(request) {
            @NonNull
            @Override
            public WAutocomplete.LoginSelectOption[] options() {
                return Arrays.stream(request.options)
                        .map(opt -> new WAutocomplete.LoginSelectOption(fromGecko(opt.value)))
                        .toArray(WAutocomplete.LoginSelectOption[]::new);
            }

            @NonNull
            @Override
            public WSession.PromptDelegate.PromptResponse confirm(@NonNull WAutocomplete.Option<?> selection) {
                return new PromptResponseImpl(mGeckoPrompt.confirm(
                        new Autocomplete.LoginSelectOption(toGecko((WAutocomplete.LoginEntry) selection.value)))
                );
            }
        }));
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onCreditCardSelect(@NonNull GeckoSession session, @NonNull AutocompleteRequest<Autocomplete.CreditCardSelectOption> request) {
        return map(mDelegate.onCreditCardSelect(mSession, new CreditCardSelectImpl(request) {
            @NonNull
            @Override
            public WAutocomplete.CreditCardSelectOption[] options() {
                return Arrays.stream(request.options)
                        .map(opt -> new WAutocomplete.CreditCardSelectOption(fromGecko(opt.value)))
                        .toArray(WAutocomplete.CreditCardSelectOption[]::new);
            }

            @NonNull
            @Override
            public WSession.PromptDelegate.PromptResponse confirm(@NonNull WAutocomplete.Option<?> selection) {
                return new PromptResponseImpl(mGeckoPrompt.confirm(
                        new Autocomplete.CreditCardSelectOption(toGecko((WAutocomplete.CreditCard) selection.value)))
                );
            }
        }));
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onAddressSelect(@NonNull GeckoSession session, @NonNull AutocompleteRequest<Autocomplete.AddressSelectOption> request) {
        return map(mDelegate.onAddressSelect(mSession, new AddressSelectImpl(request) {
            @NonNull
            @Override
            public WAutocomplete.AddressSelectOption[] options() {
                return Arrays.stream(request.options)
                        .map(opt -> new WAutocomplete.AddressSelectOption(fromGecko(opt.value)))
                        .toArray(WAutocomplete.AddressSelectOption[]::new);
            }

            @NonNull
            @Override
            public WSession.PromptDelegate.PromptResponse confirm(@NonNull WAutocomplete.Option<?> selection) {
                return new PromptResponseImpl(mGeckoPrompt.confirm(
                        new Autocomplete.AddressSelectOption(toGecko((WAutocomplete.Address) selection.value)))
                );
            }
        }));
    }

    private GeckoResult<PromptResponse> map(WResult<WSession.PromptDelegate.PromptResponse> result) {
        return ResultImpl.from(result).map(value -> {
            if (value == null) {
                return null;
            }
            return ((PromptResponseImpl)value).mGeckoResponse;
        });
    }

    private @Nullable org.mozilla.geckoview.AllowOrDeny toGecko(@Nullable WAllowOrDeny value) {
        if (value == null) {
            return null;
        }
        switch (value) {
            case ALLOW: return org.mozilla.geckoview.AllowOrDeny.ALLOW;
            case DENY: return org.mozilla.geckoview.AllowOrDeny.DENY;
        }

        return null;
    }

    private @NonNull Autocomplete.LoginEntry toGecko(@NonNull WAutocomplete.LoginEntry entry) {
        return new Autocomplete.LoginEntry.Builder()
                .formActionOrigin(entry.formActionOrigin)
                .guid(entry.guid)
                .httpRealm(entry.httpRealm)
                .origin(entry.origin)
                .password(entry.password)
                .username(entry.username)
                .build();
    }

    private @NonNull WAutocomplete.LoginEntry fromGecko(@NonNull Autocomplete.LoginEntry entry) {
        return new WAutocomplete.LoginEntry.Builder()
                .formActionOrigin(entry.formActionOrigin)
                .guid(entry.guid)
                .httpRealm(entry.httpRealm)
                .origin(entry.origin)
                .password(entry.password)
                .username(entry.username)
                .build();
    }

    private @NonNull Autocomplete.Address toGecko(@NonNull WAutocomplete.Address address) {
        return new Autocomplete.Address.Builder()
                .guid(address.guid)
                .additionalName(address.additionalName)
                .addressLevel1(address.addressLevel1)
                .addressLevel2(address.addressLevel2)
                .addressLevel3(address.addressLevel3)
                .country(address.country)
                .email(address.email)
                .familyName(address.familyName)
                .name(address.name)
                .givenName(address.givenName)
                .organization(address.organization)
                .postalCode(address.postalCode)
                .streetAddress(address.streetAddress)
                .tel(address.tel)
                .build();
    }

    private @NonNull WAutocomplete.Address  fromGecko(@NonNull Autocomplete.Address address) {
        return new WAutocomplete.Address.Builder()
                .guid(address.guid)
                .additionalName(address.additionalName)
                .addressLevel1(address.addressLevel1)
                .addressLevel2(address.addressLevel2)
                .addressLevel3(address.addressLevel3)
                .country(address.country)
                .email(address.email)
                .familyName(address.familyName)
                .name(address.name)
                .givenName(address.givenName)
                .organization(address.organization)
                .postalCode(address.postalCode)
                .streetAddress(address.streetAddress)
                .tel(address.tel)
                .build();
    }

    private @NonNull Autocomplete.CreditCard toGecko(@NonNull WAutocomplete.CreditCard card) {
        return new Autocomplete.CreditCard.Builder()
                .guid(card.guid)
                .expirationMonth(card.expirationMonth)
                .expirationYear(card.expirationYear)
                .name(card.name)
                .number(card.number)
                .build();
    }

    private @NonNull WAutocomplete.CreditCard fromGecko(@NonNull Autocomplete.CreditCard card) {
        return new WAutocomplete.CreditCard.Builder()
                .guid(card.guid)
                .expirationMonth(card.expirationMonth)
                .expirationYear(card.expirationYear)
                .name(card.name)
                .number(card.number)
                .build();
    }
}
