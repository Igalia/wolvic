package com.igalia.wolvic.speech;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.BuildConfig;
import com.igalia.wolvic.ui.widgets.dialogs.VoiceSearchWidget;
import com.igalia.wolvic.utils.LocaleUtils;
import com.igalia.wolvic.utils.StringUtils;
import com.igalia.wolvic.utils.SystemUtils;
import com.meetkai.speechlibrary.ISpeechRecognitionListener;
import com.meetkai.speechlibrary.MKSpeechService;
import com.meetkai.speechlibrary.STTResult;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class MKSpeechRecognizer implements SpeechRecognizer, ISpeechRecognitionListener {

    private Context mContext;

    protected final String LOGTAG = SystemUtils.createLogtag(this.getClass());
    private MKSpeechService mkSpeechService;
    private @Nullable SpeechRecognizer.Callback mCallback;
    private volatile boolean mIsActive = false;
    private static int MAX_CLIPPING = 10000;
    private static int MAX_DB = 130;
    private static int MIN_DB = 50;
    // temporary
    private static float FINAL_CONF = 1.0f;

    // TODO update automatically with the content of https://meetkai.ai/speech/beta/languages
    private static final List<String> mSupportedLanguages = Arrays.asList(
            LocaleUtils.DEFAULT_LANGUAGE_ID, "en", "zh", "ja", "fr", "de", "es", "pt",
            "ru", "ko", "it", "pl", "sv", "fi", "nl", "id", "th", "he", "ar"
    );
    private static final List<String> mSupportedLocales = Arrays.asList(
            "ar-BH", "ar-EG", "ar-IQ", "ar-IL", "ar-JO", "ar-KW", "ar-LB", "ar-LY", "ar-MA", "ar-OM",
            "ar-PS", "ar-QA", "ar-SA", "ar-SY", "ar-TN", "ar-AE", "ar-YE", "zh-HK", "zh-CN", "zh-TW",
            "nl-NL", "en-AU", "en-CA", "en-GH", "en-HK", "en-IN", "en-IE", "en-KE", "en-NZ", "en-NG",
            "en-PH", "en-SG", "en-ZA", "en-TZ", "en-GB", "en-US", "fi-FI", "fr-CA", "fr-FR", "fr-CH",
            "de-AT", "de-DE", "de-CH", "he-IL", "id-ID", "it-IT", "ja-JP", "ko-KR", "pl-PL", "pt-BR",
            "pt-PT", "ru-RU", "es-AR", "es-BO", "es-CL", "es-CO", "es-CR", "es-CU", "es-DO", "es-EC",
            "es-SV", "es-GQ", "es-GT", "es-HN", "es-MX", "es-NI", "es-PA", "es-PY", "es-PE", "es-PR",
            "es-ES", "es-UY", "es-US", "sv-SE", "th-TH"
    );

    private static final String DEBUG_API_KEY = "WOLVIC_DEBUG";

    public MKSpeechRecognizer(Context context) {
        mContext = context;
    }

    @Override
    public void start(@NonNull Settings settings, @NonNull Callback callback) {
        if (mIsActive) {
            Log.w(LOGTAG, "Recognition already active");
            callback.onError(Callback.ERROR_TOO_MANY_REQUESTS, "Voice recognition already in progress.");
            return;
        }
        mIsActive = true;

        mkSpeechService = MKSpeechService.getInstance();
        mCallback = callback;
        mkSpeechService.addListener(this);
        String key = BuildConfig.MK_API_KEY;
        if (StringUtils.isEmpty(key)) {
            key = DEBUG_API_KEY;
        }

        // locale set by the app's configuration
        Locale defaultLocale = mContext.getResources().getConfiguration().getLocales().get(0);
        if (defaultLocale == null) {
            // default locale for this JVM
            defaultLocale = Locale.getDefault();
        }

        Locale locale;
        if (!Objects.equals(settings.locale, LocaleUtils.DEFAULT_LANGUAGE_ID) &&
            mSupportedLanguages.contains(settings.locale)) {
            // the user has selected a specific language in the Settings UI
            // TODO use the device's location to find the country code
            locale = new Locale(settings.locale, defaultLocale.getCountry());
        } else {
            locale = new Locale(defaultLocale.getLanguage(), defaultLocale.getCountry());
        }
        settings.locale = LocaleUtils.getClosestLanguageForLocale(
                locale, mSupportedLocales, LocaleUtils.FALLBACK_LANGUAGE_TAG);

        if (!supportsASR(settings)) {
            callback.onError(Callback.ERROR_LANGUAGE_NOT_SUPPORTED, "language not supported");
            removeListener();
        } else {
            Log.w(LOGTAG, "Starting speech recognition, language = " + settings.locale);
            mkSpeechService.start(mContext, settings.locale, key);
        }
    }

    @Override
    public void stop() {
        mCallback = null;
        mkSpeechService.cancel();
        removeListener();
    }

    @Override
    public boolean isActive() {
        return mIsActive;
    }

    @Override
    public boolean shouldDisplayStoreDataPrompt() {
        return false;
    }

    @Override
    public boolean isSpeechError(int code) {
        return code == VoiceSearchWidget.State.SPEECH_ERROR.ordinal();
    }

    @Override
    public List<String> getSupportedLanguages() {
        return mSupportedLanguages;
    }

    // SpeechResultCallback
    public void onStartListen() {
        if (mCallback != null) {
            mCallback.onStartListening();
        }
    }

    public void onMicActivity(double fftsum) {
        if (mCallback != null) {
            double db = (double) fftsum * -1; // the higher the value, quieter the user/environment is
            db = db == Double.POSITIVE_INFINITY ? MAX_DB : db;
            int level = (int) (MAX_CLIPPING - (((db - MIN_DB) / (MAX_DB - MIN_DB)) * MAX_CLIPPING));
            Log.d(LOGTAG, "onMicActivity:  db = " + db + "  level = " + level);
            mCallback.onMicActivity(level);
        }
    }

    private void dispatch(Runnable runnable) {
        ((Activity) mContext).runOnUiThread(runnable);
    }

    private void removeListener() {
        mkSpeechService.removeListener(this);
        mIsActive = false;
    }

    @Override
    public boolean supportsASR(@NonNull Settings settings) {
        return mkSpeechService.supportsLocale(settings.locale);
    }

    @Override
    public void onSpeechStatusChanged(MKSpeechService.SpeechState aState, Object aPayload) {
        dispatch(() -> {

            switch (aState) {
                case MIC_ACTIVITY:
                    this.onMicActivity((double) aPayload);
                    break;
                case INTERIM_STT_RESULT:
                    if (mCallback != null) {
                        Log.w(LOGTAG, "INTERIM_STT_RESULT " + aPayload);
                        mCallback.onPartialResult((String) aPayload);
                    }
                    break;
                case STT_RESULT:
                    if (mCallback != null) {
                        STTResult result = (STTResult) aPayload;
                        Log.w(LOGTAG, "STT_RESULT " + result.transcript);
                        mCallback.onResult(result.transcript, FINAL_CONF);
                    }
                    removeListener();
                    break;
                case START_LISTEN:
                    onStartListen();
                    break;
                case NO_VOICE:
                    if (mCallback != null) {
                        mCallback.onNoVoice();
                    }
                    removeListener();
                    break;
                case CANCELED:
                    if (mCallback != null) {
                        mCallback.onCanceled();
                    }
                    removeListener();
                    break;
                case ERROR:
                    if (mCallback != null) {
                        mCallback.onError(SpeechRecognizer.Callback.SPEECH_ERROR,
                                (aPayload != null ? aPayload.toString() : "unknown error"));
                    }
                    removeListener();
                    break;
                default:
                    break;
            }
        });
    }
}
