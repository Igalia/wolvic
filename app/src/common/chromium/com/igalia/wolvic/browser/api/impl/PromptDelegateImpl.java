package com.igalia.wolvic.browser.api.impl;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.view.WindowManager;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.chromium.base.Callback;
import org.chromium.base.ThreadUtils;
import org.chromium.content.browser.input.DateTimeChooserAndroid;
import org.chromium.content.browser.input.SelectPopup;
import org.chromium.content.browser.input.SelectPopupItem;
import org.chromium.content.browser.picker.DateTimeSuggestion;
import org.chromium.content.browser.picker.InputDialogContainer;
import org.chromium.content.browser.picker.MonthPicker;
import org.chromium.content.browser.picker.WeekPicker;
import org.chromium.ui.base.ime.TextInputType;
import org.chromium.url.GURL;
import org.chromium.wolvic.AutofillManager;
import org.chromium.wolvic.ColorChooserManager;
import org.chromium.wolvic.FileSelectManager;
import org.chromium.wolvic.HttpAuthManager;
import org.chromium.wolvic.PasswordManager;
import org.chromium.wolvic.PasswordForm;
import org.chromium.wolvic.UserDialogManagerBridge;

import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.api.WAllowOrDeny;
import com.igalia.wolvic.browser.api.WAutocomplete;
import com.igalia.wolvic.browser.api.WResult;
import com.igalia.wolvic.browser.api.WSession;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

class PromptDelegateImpl implements UserDialogManagerBridge.Delegate {
    private final WSession.PromptDelegate mDelegate;
    private final SessionImpl mSession;

    public static class PromptResponseImpl implements WSession.PromptDelegate.PromptResponse {
        public PromptResponseImpl() {
        }

        public WAllowOrDeny allowOrDeny() {
            return WAllowOrDeny.DENY;
        }
    }

    public PromptDelegateImpl(WSession.PromptDelegate mDelegate, SessionImpl mSession) {
        this.mDelegate = mDelegate;
        this.mSession = mSession;

        SelectPopup.setFactory(new SelectPopupFactory());
        ColorChooserManager.setFactory(new ColorChooserFactory());
        DateTimeChooserAndroid.setFactory(new DateTimeChooserFactory());
        FileSelectManager.setFactory(new FileSelectFactory());
        HttpAuthManager.setFactory(new HttpAuthManagerFactory());

        AutocompleteFactory autocompleteFactory = new AutocompleteFactory();
        PasswordManager.setFactory(autocompleteFactory);
        AutofillManager.setFactory(autocompleteFactory);
    }

    public WSession.PromptDelegate getDelegate() { return this.mDelegate; }

    @Override
    public void onAlertDialog(@NonNull String message,
                              UserDialogManagerBridge.DialogCallback dialogCallback) {
        if (mDelegate != null) {
            mDelegate.onAlertPrompt(mSession, new AlertPrompt(dialogCallback, message));
        } else {
            dialogCallback.dismiss();
        }
    }

    @Override
    public void onConfirmDialog(@NonNull String message,
                                UserDialogManagerBridge.DialogCallback dialogCallback) {
        if (mDelegate != null) {
            mDelegate.onButtonPrompt(mSession, new ButtonPrompt(dialogCallback, message));
        } else {
            dialogCallback.dismiss();
        }
    }

    @Override
    public void onTextDialog(@NonNull String message, @NonNull String defaultUserInput,
                             UserDialogManagerBridge.DialogCallback dialogCallback) {
        if (mDelegate != null) {
            mDelegate.onTextPrompt(mSession, new TextPrompt(dialogCallback, message, defaultUserInput));
        } else {
            dialogCallback.dismiss();
        }
    }

    @Override
    public void onBeforeUnloadDialog(UserDialogManagerBridge.DialogCallback dialogCallback) {
        if (mDelegate != null) {
            mDelegate.onBeforeUnloadPrompt(mSession, new BeforeUnloadPrompt(dialogCallback));
        } else {
            dialogCallback.dismiss();
        }
    }

    public WResult<PromptResponseImpl> onRepostConfirmWarningDialog() {
        if (mDelegate == null)
            return WResult.fromValue(null);;
        return mDelegate.onRepostConfirmPrompt(mSession, new RepostConfirmPrompt()).then(result -> WResult.fromValue((PromptResponseImpl) result));
    }

    public static class BasePromptImpl implements WSession.PromptDelegate.BasePrompt {
        private WSession.PromptDelegate.PromptInstanceDelegate mDelegate;
        protected boolean mIsCompleted;

        @Override
        @Nullable
        public String title() { return null; }

        @NonNull
        @Override
        public WSession.PromptDelegate.PromptResponse dismiss() {
            markComplete();
            return new PromptResponseImpl();
        }

        @UiThread
        @Override
        public void setDelegate(final @Nullable WSession.PromptDelegate.PromptInstanceDelegate delegate) {
            mDelegate = delegate;
        }

        @UiThread
        @Override
        @Nullable
        public WSession.PromptDelegate.PromptInstanceDelegate getDelegate() {
            return mDelegate;
        }


        @Override
        public boolean isComplete() {
            return mIsCompleted;
        }

        public void markComplete() {
            mIsCompleted = true;
        }
    }

    public static class JavascriptPrompt extends BasePromptImpl {
        protected final UserDialogManagerBridge.DialogCallback mCallback;

        public JavascriptPrompt(UserDialogManagerBridge.DialogCallback callback) {
            this.mCallback = callback;
        }

        @NonNull
        @Override
        public WSession.PromptDelegate.PromptResponse dismiss() {
            mCallback.dismiss();
            markComplete();
            return new PromptResponseImpl();
        }
    }

    private static class AlertPrompt extends JavascriptPrompt implements WSession.PromptDelegate.AlertPrompt {
        private final String mMessage;

        private AlertPrompt(UserDialogManagerBridge.DialogCallback callback, String message) {
            super(callback);
            mMessage = message;
        }

        @Nullable
        @Override
        public String message() {
            return mMessage;
        }
    }


    private static class ButtonPrompt extends JavascriptPrompt implements WSession.PromptDelegate.ButtonPrompt {
        private final String mMessage;

        private ButtonPrompt(UserDialogManagerBridge.DialogCallback callback, String message) {
            super(callback);
            mMessage = message;
        }

        @Nullable
        @Override
        public String message() {
            return mMessage;
        }

        @NonNull
        @Override
        public WSession.PromptDelegate.PromptResponse confirm(int selection) {
            if (selection == Type.POSITIVE) {
                mCallback.confirm(null);
            } else {
                mCallback.dismiss();
            }
            markComplete();
            return new PromptResponseImpl();
        }
    }

    private static class TextPrompt extends JavascriptPrompt implements WSession.PromptDelegate.TextPrompt {
        private final String mMessage;
        private final String mDefaultUserInput;

        private TextPrompt(UserDialogManagerBridge.DialogCallback callback, String message,
                           String defaultUserInput) {
            super(callback);
            this.mMessage = message;
            this.mDefaultUserInput = defaultUserInput;
        }

        @Nullable
        @Override
        public String message() {
            return mMessage;
        }

        @Nullable
        @Override
        public String defaultValue() {
            return mDefaultUserInput;
        }

        @NonNull
        @Override
        public WSession.PromptDelegate.PromptResponse confirm(@NonNull String text) {
            mCallback.confirm(text);
            markComplete();
            return new PromptResponseImpl();
        }
    }

    private static class BeforeUnloadPrompt extends JavascriptPrompt implements WSession.PromptDelegate.BeforeUnloadPrompt {
        private BeforeUnloadPrompt(UserDialogManagerBridge.DialogCallback callback) {
            super(callback);
        }

        @NonNull
        @Override
        public WSession.PromptDelegate.PromptResponse confirm(@Nullable WAllowOrDeny allowOrDeny) {
            if (allowOrDeny == WAllowOrDeny.ALLOW) {
                mCallback.confirm(null);
            } else {
                mCallback.dismiss();
            }
            markComplete();
            return new PromptResponseImpl();
        }
    }

    private static class RepostConfirmPrompt extends BasePromptImpl implements WSession.PromptDelegate.RepostConfirmPrompt {
        @NonNull
        @Override
        public WSession.PromptDelegate.PromptResponse confirm(@Nullable WAllowOrDeny allowOrDeny) {
            return new PromptResponseImpl() {
                @Override
                public WAllowOrDeny allowOrDeny() {
                    return allowOrDeny;
                }
            };
        }
    }

    public class SelectPopupFactory implements SelectPopup.Factory {
        public SelectPopup.Ui create(Context windowContext, Callback<int[]> selectionChangedCallback,
                                     List<SelectPopupItem> items, boolean multiple, int[] selected) {
            return new ChoicePromptBridge(windowContext, selectionChangedCallback, items, multiple, selected);
        }
    }

    public class ChoicePrompt extends BasePromptImpl implements  WSession.PromptDelegate.ChoicePrompt {
        public class Choice implements WSession.PromptDelegate.ChoicePrompt.Choice {
            public final boolean disabled;
            public final @Nullable String icon;
            public final @NonNull String id;
            public final @NonNull String label;
            public boolean selected;
            public final boolean separator;
            public @Nullable Choice[] choices;

            /* package */ Choice(SelectPopupItem item, int id, boolean selected) {
                this.disabled = !item.isEnabled();
                this.id =  String.valueOf(id);
                this.icon = null;
                this.label = item.getLabel();
                this.separator = false;
                this.selected = selected;
                this.choices = null;
            }

            @Override
            public boolean disabled() {
                return this.disabled;
            }

            @Nullable
            @Override
            public String icon() {
                return this.icon;
            }

            @NonNull
            @Override
            public String id() {
                return this.id;
            }

            @NonNull
            @Override
            public String label() {
                return this.label;
            }

            @Override
            public boolean selected() {
                return this.selected;
            }

            @Override
            public boolean separator() {
                return this.separator;
            }

            @Override
            @Nullable
            public Choice[] items() { return this.choices; }

        }
        public final @Nullable String message;

        public final @WSession.PromptDelegate.ChoicePrompt.ChoiceType int type;

        public final @Nullable Choice[] choices;

        private final Callback<int[]> mSelectionChangedCallback;

        private Choice createChoice(List<SelectPopupItem> items, int[] position, int[] selected,
                                    int[] selectedIndex) {
            if (items.size() == position[0])
                return null;

            SelectPopupItem item = items.get(position[0]);

            boolean isSelected = false;
            if (selected.length > selectedIndex[0] && selected[selectedIndex[0]] == position[0]) {
                isSelected = true;
                selectedIndex[0]++;
            }

            Choice choice = new Choice(item, position[0], isSelected);
            if (item.isGroupHeader()) {
                ArrayList<Choice> choices = new ArrayList<>();
                int nextPosition = position[0]+1;
                while (items.size() > nextPosition) {
                    SelectPopupItem childItem = items.get(nextPosition);
                    if (childItem.isGroupHeader())
                        break;

                    position[0] = nextPosition;
                    Choice child = createChoice(items, position, selected, selectedIndex);
                    assert child != null;
                    choices.add(child);
                    nextPosition = position[0]+1;
                }
                choice.choices = choices.toArray(new Choice[choices.size()]);
            }
            return choice;
        }

        public ChoicePrompt(List<SelectPopupItem> items, boolean multiple, int[] selected,
                            Callback<int[]> selectionChangedCallback) {
            if (items == null) {
                this.choices = null;
            } else {
                ArrayList<Choice> choices = new ArrayList<>();
                int[] position = new int[]{0};
                int[] selectedIndex = new int[]{0};
                while (position[0] < items.size()) {
                    Choice child = createChoice(items, position, selected, selectedIndex);
                    assert child != null;
                    choices.add(child);
                    position[0]++;
                }
                this.choices = choices.toArray(new Choice[choices.size()]);
            }

            message = null;
            type = multiple ? WSession.PromptDelegate.ChoicePrompt.Type.MULTIPLE :
                    WSession.PromptDelegate.ChoicePrompt.Type.SINGLE;
            mSelectionChangedCallback = selectionChangedCallback;
        }

        private <T> boolean isValidArgument(T[] selectedChoices) {
            if ((WSession.PromptDelegate.ChoicePrompt.Type.MENU == type ||
                    WSession.PromptDelegate.ChoicePrompt.Type.SINGLE == type)
                    && (selectedChoices == null || selectedChoices.length != 1)) {
                throw new IllegalArgumentException();
            }
            return true;
        }

        @NonNull
        public WSession.PromptDelegate.PromptResponse confirm(final int[] selectedIds) {
            if (!isComplete())
                mSelectionChangedCallback.onResult(selectedIds);
            markComplete();
            return new PromptResponseImpl();
        }

        @UiThread
        @Override
        @NonNull
        public WSession.PromptDelegate.PromptResponse confirm(@NonNull final String selectedId) {
            try {
                int id = Integer.parseInt(selectedId);
                return confirm(new int[] {id});
            } catch (NumberFormatException nfe) {
                return confirm((int[]) null);
            }
        }

        @UiThread
        @Override
        @NonNull
        public WSession.PromptDelegate.PromptResponse confirm(@NonNull final String[] selectedIds) {
            if (!isValidArgument(selectedIds)) {
                return confirm((int[]) null);
            }

            int length = 0;
            int[] ids = new int[selectedIds.length];
            for (int i = 0; i < selectedIds.length; i++) {
                try {
                    ids[length++] = Integer.parseInt(selectedIds[i]);
                } catch (NumberFormatException nfe) {
                }
            }

            ids = Arrays.copyOf(ids, length);
            return confirm(ids);
        }

        @UiThread
        @Override
        @NonNull
        public WSession.PromptDelegate.PromptResponse confirm(
                @NonNull final WSession.PromptDelegate.ChoicePrompt.Choice selectedChoice) {
            return confirm(selectedChoice == null ? null : selectedChoice.id());
        }

        @UiThread
        @Override
        @NonNull
        public WSession.PromptDelegate.PromptResponse confirm(
                @NonNull final WSession.PromptDelegate.ChoicePrompt.Choice[] selectedChoices) {
            if (!isValidArgument(selectedChoices)) {
                return confirm((int[]) null);
            }

            int[] ids = new int[selectedChoices.length];
            int length = 0;
            for (int i = 0; i < ids.length; i++) {
                if (selectedChoices[i] != null) {
                    try {
                        ids[length++] = Integer.parseInt(selectedChoices[i].id());
                    } catch (NumberFormatException nfe) {
                    }
                }
            }
            ids = Arrays.copyOf(ids, length);
            return confirm(ids);
        }

        @NonNull
        @Override
        public WSession.PromptDelegate.PromptResponse dismiss() {
            if (!isComplete())
                mSelectionChangedCallback.onResult(null);
            return super.dismiss();
        }

        @Override
        @NonNull
        public WSession.PromptDelegate.ChoicePrompt.Choice[] choices() {
            return this.choices;
        }

        @Override
        @Nullable
        public String message() {
            return this.message;
        }

        @Override
        @ChoiceType public int type() {
            return this.type;
        }
    }

    public class ChoicePromptBridge implements SelectPopup.Ui {

        private final ChoicePrompt mChoicePrompt;

        public ChoicePromptBridge(Context windowContext, Callback<int[]> selectionChangedCallback,
            List<SelectPopupItem> items, boolean multiple, int[] selected) {
            mChoicePrompt = new ChoicePrompt(items, multiple, selected, selectionChangedCallback);
        }

        @Override
        public void show() {
            try {
                if (mDelegate != null) {
                    mDelegate.onChoicePrompt(mSession, mChoicePrompt);
                } else {
                    mChoicePrompt.dismiss();
                }
            } catch (WindowManager.BadTokenException e) {
                mChoicePrompt.dismiss();
            }
        }

        @Override
        public void hide(boolean sendsCancelMessage) {
            mChoicePrompt.dismiss();
        }
    }

    public class ColorChooserFactory implements ColorChooserManager.Factory {
        public ColorChooserManager.Bridge create(int initialColor, ColorChooserManager.Listener listener) {
            return new ColorPromptBridge(initialColor, listener);
        }
    }

    public class ColorPromptBridge implements ColorChooserManager.Bridge {

        private final ColorPrompt mColorPrompt;

        public ColorPromptBridge(int initialColor, ColorChooserManager.Listener listener) {
            mColorPrompt = new ColorPrompt(initialColor, listener);
        }

        @Override
        public void show() {
            try {
                if (mDelegate != null) {
                    mDelegate.onColorPrompt(mSession, mColorPrompt);
                } else {
                    mColorPrompt.dismiss();
                }
            } catch (WindowManager.BadTokenException e) {
                mColorPrompt.dismiss();
            }
        }

        @Override
        public void close() {
            if (!mColorPrompt.isComplete())
                mColorPrompt.dismiss();
        }
    }

    public static class ColorPrompt extends BasePromptImpl implements WSession.PromptDelegate.ColorPrompt {
        private final String mDefaultColor;
        private final ColorChooserManager.Listener mListener;

        public ColorPrompt(int defaultColor, ColorChooserManager.Listener listener) {
            mDefaultColor = "#" + Integer.toHexString(defaultColor);
            mListener = listener;
        }

        @Nullable
        @Override
        public String defaultValue() { return mDefaultColor; }

        @UiThread
        @NonNull
        @Override
        public WSession.PromptDelegate.PromptResponse confirm(@NonNull final String color) {
            markComplete();
            mListener.onColorChanged(Color.parseColor(color));
            return new PromptResponseImpl();
        }

        @NonNull
        @Override
        public WSession.PromptDelegate.PromptResponse dismiss() {
            if (!isComplete()) {
                markComplete();
                mListener.onColorChanged(Color.parseColor(mDefaultColor));
            }
            return new PromptResponseImpl();
        }
    }

    public class DateTimeChooserFactory implements DateTimeChooserAndroid.Factory {
        public DateTimeChooserBridge create(Context context, InputDialogContainer.InputActionDelegate delegate) {
            return new DateTimeChooserBridge(context, delegate);
        }
    }

    public class DateTimeChooserBridge extends InputDialogContainer {
        private final DateTimePrompt mDatePrompt;

        public DateTimeChooserBridge(Context context, InputDialogContainer.InputActionDelegate delegate) {
            super(context, delegate);
            mDatePrompt = new DateTimePrompt(delegate);
        }

        @Override
        public void showDialog(final int type, final double value,
                               double min, double max, double step,
                               DateTimeSuggestion[] suggestions) {
            // Not implemented for |suggestions| and |step| yet.
            mDatePrompt.setDateTime(type, value, min, max);

            try {
                if (mDelegate != null) {
                    mDelegate.onDateTimePrompt(mSession, mDatePrompt);
                } else {
                    mDatePrompt.dismiss();
                }
            } catch (WindowManager.BadTokenException e) {
                mDatePrompt.dismiss();
            }
        }

        @Override
        public void dismissDialog() {
            if (!mDatePrompt.isComplete())
                mDatePrompt.dismiss();
        }
    }

    public static class DateTimePrompt extends BasePromptImpl implements WSession.PromptDelegate.DateTimePrompt {
        private final InputDialogContainer.InputActionDelegate mDelegate;
        @DatetimeType int mType;
        String mDefaultValue;
        String mMinValue;
        String mMaxValue;

        SimpleDateFormat mFormatter;

        public DateTimePrompt(InputDialogContainer.InputActionDelegate delegate) {
            mDelegate = delegate;
        }

        public void setDateTime(int type, double value, double min, double max) {
            String format;
            if (type == TextInputType.DATE) {
                mType = Type.DATE;
                format = "yyyy-MM-dd";
            } else if (type == TextInputType.TIME) {
                mType = Type.TIME;
                format = "HH:mm";
            } else if (type == TextInputType.DATE_TIME || type == TextInputType.DATE_TIME_LOCAL) {
                mType = Type.DATETIME_LOCAL;
                format = "yyyy-MM-dd'T'HH:mm";
            } else if (type == TextInputType.MONTH) {
                mType = Type.MONTH;
                format = "yyyy-MM";
                value = MonthPicker.createDateFromValue(value).getTimeInMillis();
            } else if (type == TextInputType.WEEK) {
                mType = Type.WEEK;
                format = "yyyy-'W'ww";
                value = WeekPicker.createDateFromValue(value).getTimeInMillis();
            } else {
                throw new IllegalArgumentException();
            }

            Date defaultDate;
            if (Double.isNaN(value)) {
                defaultDate = new Date(System.currentTimeMillis());
            } else {
                defaultDate = new Date((long) value);
            }

            mFormatter = new SimpleDateFormat(format, Locale.ROOT);
            mFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
            mDefaultValue = mFormatter.format(defaultDate);
            mMinValue = mFormatter.format(new Date((long) min));
            mMaxValue = mFormatter.format(new Date((long) max));
        }

        @Override
        @DatetimeType
        public int type() { return mType; }

        @Override
        @Nullable
        public String defaultValue() { return mDefaultValue; }

        @Override
        @Nullable
        public String minValue() { return mMinValue; }

        @Override
        @Nullable
        public String maxValue() { return mMaxValue; }

        @UiThread
        @NonNull
        @Override
        public WSession.PromptDelegate.PromptResponse confirm(@NonNull final String datetime) {
            markComplete();
            try {
                if (datetime != null && !datetime.isEmpty()) {
                    Date date = mFormatter.parse(datetime);
                    Calendar cal = mFormatter.getCalendar();
                    cal.setTime(date);
                    mDelegate.replaceDateTime(cal.getTimeInMillis());
                } else {
                    mDelegate.replaceDateTime(Double.NaN);
                }
            } catch (final ParseException e) {
            }
            return new PromptResponseImpl();
        }

        @NonNull
        @Override
        public WSession.PromptDelegate.PromptResponse dismiss() {
            if (!isComplete()) {
                markComplete();
                mDelegate.cancelDateTimeDialog();
            }
            return new PromptResponseImpl();
        }
    }

    public class HttpAuthManagerFactory implements HttpAuthManager.Factory {
        public HttpAuthManager.Bridge create(GURL url, boolean isProxy, boolean firstAuthAttempt,
                                             HttpAuthManager.Listener listener) {
            return new AuthPromptBridge(url, isProxy, firstAuthAttempt, listener);
        }
    }

    public class AuthPromptBridge implements HttpAuthManager.Bridge {
        private final AuthPrompt mAuthPrompt;

        public AuthPromptBridge(GURL url, boolean isProxy, boolean firstAuthAttempt,
                                HttpAuthManager.Listener listener) {
            mAuthPrompt = new AuthPrompt(url, isProxy, firstAuthAttempt, listener);
        }

        @Override
        public void show() {
            try {
                if (mDelegate != null) {
                    mDelegate.onAuthPrompt(mSession, mAuthPrompt);
                } else {
                    mAuthPrompt.dismiss();
                }
            } catch (WindowManager.BadTokenException e) {
                mAuthPrompt.dismiss();
            }
        }

        @Override
        public void close() {
            if (!mAuthPrompt.isComplete())
                mAuthPrompt.dismiss();
        }
    }

    public static class AuthPrompt extends BasePromptImpl implements WSession.PromptDelegate.AuthPrompt {
        private final String mMessage;
        private final HttpAuthManager.Listener mListener;
        private final AuthPrompt.AuthOptions mOptions;

        public AuthPrompt(GURL url, boolean isProxy, boolean firstAuthAttempt,
                          HttpAuthManager.Listener listener) {
            mMessage = url.getHost();
            mListener = listener;
            int flags = isProxy ? AuthOptions.Flags.PROXY : AuthOptions.Flags.HOST;
            if (!firstAuthAttempt) {
                flags |= AuthOptions.Flags.PREVIOUS_FAILED;
            }
            String userName = TextUtils.isEmpty(url.getUsername()) ? null : url.getUsername();
            String password = TextUtils.isEmpty(url.getPassword()) ? null : url.getPassword();
            mOptions = new AuthOptions(flags, url.getSpec(), AuthOptions.Level.NONE, userName, password);
        }

        @Nullable
        @Override
        public String message() { return mMessage; }

        @NonNull
        @Override
        public AuthOptions authOptions() { return mOptions; }

        @UiThread
        @NonNull
        @Override
        public WSession.PromptDelegate.PromptResponse confirm(@NonNull final String password) {
            markComplete();
            mListener.proceed("", password);
            return new PromptResponseImpl();
        }

        @UiThread
        @NonNull
        @Override
        public WSession.PromptDelegate.PromptResponse confirm(@NonNull final String username, @NonNull final String password) {
            markComplete();
            mListener.proceed(username, password);
            return new PromptResponseImpl();
        }

        @NonNull
        @Override
        public WSession.PromptDelegate.PromptResponse dismiss() {
            if (!isComplete()) {
                markComplete();
                mListener.cancel();
            }
            return new PromptResponseImpl();
        }
    }

    public class FileSelectFactory implements FileSelectManager.Factory {
        public FileSelectManager.Bridge create(FileSelectManager.Listener listener) {
            return new FileSelectPromptBridge(listener);
        }
    }

    public class FileSelectPromptBridge implements FileSelectManager.Bridge {

        private final FilePrompt mFilePrompt;


        public FileSelectPromptBridge(FileSelectManager.Listener listener) {
            mFilePrompt = new FilePrompt(listener);
        }

        @Override
        public void selectFile(String[] acceptMimeTypes, boolean capture, boolean allowMultiple) {
            try {
                if (mDelegate != null) {
                    mFilePrompt.setSelectFileInfo(acceptMimeTypes, capture, allowMultiple);
                    mDelegate.onFilePrompt(mSession, mFilePrompt);
                } else {
                    mFilePrompt.dismiss();
                }
            } catch (WindowManager.BadTokenException e) {
                mFilePrompt.dismiss();
            }
        }

        @Override
        public void close() {
            if (!mFilePrompt.isComplete()) {
                mFilePrompt.dismiss();
            }
        }
    }

    public static class FilePrompt extends BasePromptImpl implements WSession.PromptDelegate.FilePrompt {
        private final FileSelectManager.Listener mListener;
        private String[] mMimeTypes;
        @CaptureType
        private int mCapture;
        @FileType
        private int mType;

        public FilePrompt(FileSelectManager.Listener listener) {
            mListener = listener;
        }

        public void setSelectFileInfo(String[] acceptMimeTypes, boolean capture, boolean allowMultiple) {
            mMimeTypes = acceptMimeTypes;
            mCapture = capture ? Capture.ANY : Capture.NONE;
            mType = allowMultiple ? Type.MULTIPLE : Type.SINGLE;
        }

        @Override
        @FileType
        public int type() { return mType; }

        @Override
        @Nullable
        public String[] mimeTypes() { return mMimeTypes; }

        @Override
        @CaptureType
        public int captureType() { return mCapture; }

        @Override
        @UiThread
        @NonNull
        public WSession.PromptDelegate.PromptResponse confirm(@NonNull final Context context, @NonNull final Uri uri) {
            markComplete();
            mListener.onFileSelected(uri.getSchemeSpecificPart());
            return new PromptResponseImpl();
        }

        @Override
        @UiThread
        public @NonNull
        WSession.PromptDelegate.PromptResponse confirm(@NonNull final Context context, @NonNull final Uri[] uris) {
            String[] filePathArray = new String[uris.length];
            for (int i = 0; i < uris.length; i++) {
                filePathArray[i] = uris[i].getSchemeSpecificPart();
            }

            markComplete();
            mListener.onMultipleFilesSelected(filePathArray);
            return new PromptResponseImpl();
        }

        @Override
        @NonNull
        public WSession.PromptDelegate.PromptResponse dismiss() {
            if (!isComplete()) {
                markComplete();
                mListener.onCanceled();
            }
            return new PromptResponseImpl();
        }
    }


    public class AutocompleteFactory implements PasswordManager.Factory, AutofillManager.Factory {
        AutocompleteBridge mAutocompleteBridge = new AutocompleteBridge();
        public PasswordManager.Bridge create(PasswordManager.Listener listener) {
            mAutocompleteBridge.setListener(listener);
            return mAutocompleteBridge;
        }
        public AutofillManager.Bridge create(AutofillManager.Listener listener) {
            mAutocompleteBridge.setListener(listener);
            return mAutocompleteBridge;
        }
    }

    public class AutocompleteBridge implements PasswordManager.Bridge, AutofillManager.Bridge {
        private PasswordManager.Listener mPasswordManagerListener;
        private AutofillManager.Listener mAutofillManagerListener;

        private LoginSavePrompt mLoginSavePrompt;
        private LoginSelectPrompt mLoginSelectPrompt;

        public void setListener(PasswordManager.Listener listener) {
            mPasswordManagerListener = listener;
        }

        public void setListener(AutofillManager.Listener listener) {
            mAutofillManagerListener = listener;
        }

        @Override
        public boolean isAutocompleteEnabled(Context context) {
            return SettingsStore.getInstance(context).isAutocompleteEnabled();
        }

        @Override
        public boolean isAutoFillEnabled(Context context) {
            return SettingsStore.getInstance(context).isAutoFillEnabled();
        }

        @Override
        public boolean isPasswordManagerEnabled(Context context) {
            return SettingsStore.getInstance(context).isLoginAutocompleteEnabled();
        }

        @Override
        public boolean saveOrUpdatePassword(PasswordForm form) {
            dismiss();
            assert mPasswordManagerListener != null;

            mSession.checkLoginIfAlreadySaved(form).then(saved -> {
                if (!saved) {
                    WAutocomplete.LoginSaveOption[] options = new WAutocomplete.LoginSaveOption[1];
                    options[0] = new WAutocomplete.LoginSaveOption(fromPasswordForm(form));
                    mLoginSavePrompt = new LoginSavePrompt(mPasswordManagerListener, options);
                    ThreadUtils.postOnUiThread(() -> {
                        try {
                            if (mDelegate != null) {
                                mDelegate.onLoginSave(mSession, mLoginSavePrompt);
                            } else {
                                resetLoginSavePrompt();
                            }
                        } catch (WindowManager.BadTokenException e) {
                            resetLoginSavePrompt();
                        }
                    });
                }
                return null;
            });

            return true;
        }

        @Override
        public boolean onLoginSelect(PasswordForm[] forms) {
            dismiss();
            assert mPasswordManagerListener != null;

            WAutocomplete.LoginSelectOption[] options =
                    new WAutocomplete.LoginSelectOption[forms.length];
            for (int i = 0; i < forms.length; i++) {
                options[i] = new WAutocomplete.LoginSelectOption(fromPasswordForm(forms[i]));
            }

            mLoginSelectPrompt = new LoginSelectPrompt<PasswordManager.Listener>(
                    mPasswordManagerListener, options);
            try {
                if (mDelegate != null) {
                    mDelegate.onLoginSelect(mSession, mLoginSelectPrompt);
                } else {
                    resetLoginSelectPrompt();
                    return false;
                }
            } catch (WindowManager.BadTokenException e) {
                resetLoginSelectPrompt();
                return false;
            }
            return true;
        }

        @Override
        public boolean onLoginSelect(String[] username) {
            dismiss();
            assert mAutofillManagerListener != null;

            WAutocomplete.LoginSelectOption[] options =
                    new WAutocomplete.LoginSelectOption[username.length];
            for (int i = 0; i < username.length; i++) {
                options[i] = new WAutocomplete.LoginSelectOption(fromUsername(i, username[i]));
            }

            mLoginSelectPrompt = new LoginSelectPrompt<AutofillManager.Listener>(
                    mAutofillManagerListener, options);
            try {
                if (mDelegate != null) {
                    mDelegate.onLoginSelect(mSession, mLoginSelectPrompt);
                } else {
                    resetLoginSelectPrompt();
                    return false;
                }
            } catch (WindowManager.BadTokenException e) {
                resetLoginSelectPrompt();
                return false;
            }
            return true;
        }


        @Override
        public void onLoginUsed(PasswordForm form) {
            try {
                if (mDelegate != null) {
                    mSession.onLoginUsed(form);
                }
            } catch (WindowManager.BadTokenException e) {
            }
        }

        @Override
        public void dismiss() {
            resetLoginSavePrompt();
            resetLoginSelectPrompt();
        }

        private void resetLoginSavePrompt() {
            if (mLoginSavePrompt != null) {
                mLoginSavePrompt.dismiss();
                mLoginSavePrompt = null;
            }
        }

        private void resetLoginSelectPrompt() {
            if (mLoginSelectPrompt != null) {
                mLoginSelectPrompt.dismiss();
                mLoginSelectPrompt = null;
            }
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

        private @NonNull WAutocomplete.LoginEntry fromUsername(int index, @NonNull String username) {
            return new WAutocomplete.LoginEntry.Builder()
                    .guid(Integer.toHexString(index))
                    .username(username)
                    .build();
        }

        public class LoginSavePrompt extends BasePromptImpl
                implements WSession.PromptDelegate.AutocompleteRequest<WAutocomplete.LoginSaveOption> {
            private final PasswordManager.Listener mListener;
            private final WAutocomplete.LoginSaveOption[] mOptions;
            public LoginSavePrompt(PasswordManager.Listener listener,
                                   WAutocomplete.LoginSaveOption[] options) {
                mListener = listener;
                mOptions = options;
            }

            @NonNull
            @Override
            public WAutocomplete.LoginSaveOption[] options() {
                return mOptions;
            }

            @NonNull
            @Override
            public WSession.PromptDelegate.PromptResponse confirm(
                    @NonNull WAutocomplete.Option<?> selection) {
                mListener.onLoginSaved(toPasswordForm((WAutocomplete.LoginEntry) selection.value));
                return new PromptResponseImpl();
            }
        }

        public class LoginSelectPrompt<T> extends BasePromptImpl
                implements WSession.PromptDelegate.AutocompleteRequest<WAutocomplete.LoginSelectOption> {
            private final T mListener;
            private final WAutocomplete.LoginSelectOption[] mOptions;
            public LoginSelectPrompt(T listener,
                                     WAutocomplete.LoginSelectOption[] options) {
                mListener = listener;
                mOptions = options;
            }

            @NonNull
            @Override
            public WAutocomplete.LoginSelectOption[] options() {
                return mOptions;
            }

            @NonNull
            @Override
            public WSession.PromptDelegate.PromptResponse confirm(
                    @NonNull WAutocomplete.Option<?> selection) {
                if (mListener instanceof PasswordManager.Listener) {
                    ((PasswordManager.Listener) mListener).onLoginSelected(
                            toPasswordForm((WAutocomplete.LoginEntry) selection.value));
                } else if (mListener instanceof AutofillManager.Listener) {
                    ((AutofillManager.Listener) mListener).onLoginSelected(
                            Integer.valueOf(((WAutocomplete.LoginEntry) selection.value).guid));
                }
                return new PromptResponseImpl();
            }
        }
    }
}
