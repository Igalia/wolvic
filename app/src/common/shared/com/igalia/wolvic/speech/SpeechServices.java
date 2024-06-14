package com.igalia.wolvic.speech;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringDef;

import com.igalia.wolvic.BuildConfig;
import com.igalia.wolvic.R;

import java.lang.reflect.Constructor;

public abstract class SpeechServices {
    // Full names of the classes that implement the connection to each speech recognition service.
    // These classes must implement the SpeechRecognizer interface and provide a constructor
    // that takes a Context as its only parameter.
    public static final String MEETKAI = "com.igalia.wolvic.speech.MKSpeechRecognizer";
    public static final String HUAWEI_ASR = "com.igalia.wolvic.speech.HVRSpeechRecognizer";

    @StringDef(value = {MEETKAI, HUAWEI_ASR})
    @interface Service {
    }

    public static final @Service
    String DEFAULT = BuildConfig.SPEECH_SERVICES[0];

    /**
     * @param aContext
     * @param service
     * @return A new instance of the chosen speech recognition service.
     * @throws ReflectiveOperationException
     */
    public static SpeechRecognizer getInstance(@NonNull Context aContext, @NonNull @Service String service)
            throws ReflectiveOperationException {
        Class<?> recognizerClass = Class.forName(service);
        Constructor<?> constructor = recognizerClass.getConstructor(Context.class);
        return (SpeechRecognizer) constructor.newInstance(new Object[]{aContext});
    }

    /**
     * @param service
     * @return the String resource ID corresponding to the service's display name
     */
    public static int getNameResource(@NonNull @Service String service) {
        switch (service) {
            case MEETKAI:
                return R.string.voice_service_metkai;
            case HUAWEI_ASR:
                return R.string.voice_service_huawei_asr;
            default:
                return android.R.string.unknownName;
        }
    }
}
