package org.mozilla.vrbrowser.speech;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mozilla.speechlibrary.SpeechResultCallback;
import com.mozilla.speechlibrary.SpeechService;
import com.mozilla.speechlibrary.SpeechServiceSettings;
import com.mozilla.speechlibrary.stt.STTResult;

import org.mozilla.geckoview.GeckoWebExecutor;
import org.mozilla.vrbrowser.browser.engine.EngineProvider;
import org.mozilla.vrbrowser.ui.widgets.dialogs.VoiceSearchWidget;
import org.mozilla.vrbrowser.utils.SystemUtils;

public class MozillaSpeechRecognizer implements SpeechRecognizer, SpeechResultCallback {
    protected final String LOGTAG = SystemUtils.createLogtag(this.getClass());
    private SpeechService mMozillaSpeechService;
    private Context mContext;
    private @Nullable SpeechRecognizer.Callback mCallback;
    private static int MAX_CLIPPING = 10000;
    private static int MAX_DB = 130;
    private static int MIN_DB = 50;

    public MozillaSpeechRecognizer(Context context) {
        mContext = context;
        mMozillaSpeechService = new SpeechService(context);
    }

    @Override
    public void start(@NonNull Settings settings, @Nullable GeckoWebExecutor executor, @NonNull Callback callback) {
        SpeechServiceSettings.Builder builder = new SpeechServiceSettings.Builder()
                .withLanguage(settings.locale)
                .withStoreSamples(settings.storeData)
                .withStoreTranscriptions(settings.storeData)
                .withProductTag(settings.productTag);
        mCallback = callback;
        mMozillaSpeechService.start(builder.build(), EngineProvider.INSTANCE.getDefaultGeckoWebExecutor(mContext), this);
    }

    @Override
    public void stop() {
        mMozillaSpeechService.stop();
        mCallback = null;
    }

    @Override
    public boolean shouldDisplayStoreDataPrompt() {
        return true;
    }

    @Override
    public boolean isSpeechError(int code) {
        return code == VoiceSearchWidget.State.SPEECH_ERROR.ordinal();
    }

    // SpeechResultCallback
    @Override
    public void onStartListen() {
        if (mCallback != null) {
            mCallback.onStartListening();
        }
    }

    @Override
    public void onMicActivity(double fftsum) {
        if (mCallback != null) {
            double db = (double)fftsum * -1; // the higher the value, quieter the user/environment is
            db = db == Double.POSITIVE_INFINITY ? MAX_DB : db;
            int level = (int)(MAX_CLIPPING - (((db - MIN_DB) / (MAX_DB - MIN_DB)) * MAX_CLIPPING));
            Log.d(LOGTAG, "===> db:      " + db);
            Log.d(LOGTAG, "===> level    " + level);
            mCallback.onMicActivity(level);
        }
    }

    @Override
    public void onDecoding() {
        if (mCallback != null) {
            mCallback.onDecoding();
        }
    }

    @Override
    public void onSTTResult(@Nullable @org.jetbrains.annotations.Nullable STTResult result) {
        if (mCallback == null) {
            return;
        }

        if (result != null) {
            mCallback.onResult(result.mTranscription, result.mConfidence);
        } else {
            mCallback.onResult("", 0);
        }
    }

    @Override
    public void onNoVoice() {
        if (mCallback != null) {
            mCallback.onNoVoice();
        }
    }

    @Override
    public void onError(int errorType, @Nullable @org.jetbrains.annotations.Nullable String error) {
        if (mCallback != null) {
            mCallback.onError(errorType, error);
        }
    }
}
