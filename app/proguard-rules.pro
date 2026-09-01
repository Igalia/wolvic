# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --------------------------------------------------------------------
# REMOVE all Log messages except warnings and errors
# --------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int d(...);
}

# --------------------------------------------------------------------
# Keep speech recognizer classes (loaded via Class.forName() in SpeechServices)
# --------------------------------------------------------------------
-keep class com.igalia.wolvic.speech.VoskSpeechRecognizer { *; }
-keep class com.igalia.wolvic.speech.VoskModelManager { *; }
-keep class com.igalia.wolvic.speech.HVRSpeechRecognizer { *; }

# Keep Vosk library classes — the native libvosk.so calls back into these via JNI,
# and the AAR ships no consumer ProGuard rules of its own.
-keep class org.vosk.** { *; }

# ====================================================================
# BACKEND-SPECIFIC RULES
#
# This file is shared by every backend: build.gradle applies it to the
# release build type, not to a flavor, so these rules are also parsed in
# builds that do not contain the classes they name. That is harmless -- a
# -keep for an absent class is a no-op -- so the grouping below is about
# readability, not scoping.
# ====================================================================

# --------------------------------------------------------------------
# GeckoView backend
# --------------------------------------------------------------------
# --------------------------------------------------------------------
# REMOVE android speech dependency from GV
# --------------------------------------------------------------------
-assumenosideeffects class org.mozilla.gecko.SpeechSynthesisService {
    private static void initSynthInternal();
    private static void stopInternal();
    private static void speakInternal(java.lang.String, java.lang.String, float, float, float, java.lang.String, java.util.concurrent.atomic.AtomicBoolean);
    private static void setUtteranceListener();
    private static void stopInternal();
}

-assumenosideeffects class org.mozilla.gecko.util.InputOptionsUtils {
    public static boolean supportsVoiceRecognizer(android.content.Context, java.lang.String);
    public static android.content.Intent createVoiceRecognizerIntent(java.lang.String);
}

# --------------------------------------------------------------------
# Keep everything under org.mozilla.gecko.**. GeckoView ships a consumer rule
# that keeps org.mozilla.geckoview.** but only @WrapForJNI-annotated members of
# org.mozilla.gecko.**. R8 then strips public/protected members of internals
# like GeckoProcessManager, GeckoAppShell, and XPCOMEventTarget's helpers that
# Gecko native code reaches via JNI/reflection at startup. The first observable
# symptom is a MOZ_CRASH on the launcher thread inside
# mozilla::jni::Accessor::EndAccess after dispatching XPCOMEventTarget.JNIRunnable
# — see commit 0aa9413e8 (where the MeetKai AAR's implicit global keep rule was
# removed).
# --------------------------------------------------------------------
-keep class org.mozilla.gecko.** { *; }

# --------------------------------------------------------------------
# Chromium backend
# --------------------------------------------------------------------
# --------------------------------------------------------------------
# AndroidX Window extensions
#
# Chromium's WindowLayoutInfoListener passes a lambda implementing the
# platform provided Consumer interface to the system. R8 could not see that
# the desugared lambda's accept(Object) overrides anything and stripped it,
# so startup crashes with AbstractMethodError. No -keep can fix that, since
# R8 synthesises the lambda class after keep rules are matched. The fix
# is the androidx.window.extensions.core:core dependency in build.gradle.
# --------------------------------------------------------------------
-keep interface androidx.window.extensions.core.util.function.Consumer {
    <methods>;
}

# --------------------------------------------------------------------
# Keep the fields of the chromium glue objects.
#
# Objects like TabWebContentsDelegate and TabWebContentsObserver are handed to
# Chromium's native side, which keeps only a *weak* global ref to them. The Java
# field that stores them (e.g. TabImpl.mTabWebContentsDelegate) is never read
# back, so R8's optimizer removes it as write-only -- the object then has no
# strong referrer, gets collected, and every native->Java callback on it turns
# into a silent no-op. Observed symptom: video fullscreen never engages, because
# WebContentsDelegateAndroid::EnterFullscreenModeForTab finds a dead weak ref and
# returns without ever calling enterFullscreenModeForTab(). No exception, no log.
# Pinning the fields keeps the referents alive.
# --------------------------------------------------------------------
-keepclassmembers class com.igalia.wolvic.browser.api.impl.** {
    <fields>;
}

# ====================================================================
# End of backend-specific rules
# ====================================================================

# --------------------------------------------------------------------
# Keep classes from FxR
# --------------------------------------------------------------------
-keep class com.igalia.wolvic.ui.widgets.WidgetPlacement {*;} # Keep class used in JNI.
-keep class com.igalia.wolvic.ui.widgets.Windows$** {*;} # Keep state clases used by gson.
-keep class com.igalia.wolvic.browser.engine.** {*;} # Keep state clases used by gson.
-keep class com.igalia.wolvic.utils.RemoteProperties {*;} # Keep state clases used by gson.
-keep class com.igalia.wolvic.utils.Environment {*;} # Keep state clases used by gson.
-keep class com.igalia.wolvic.utils.RemoteExperiences {*;} # Keep remote experience classes used by gson.
-keep class com.igalia.wolvic.utils.Category {*;} # Keep remote experience classes used by gson.
-keep class com.igalia.wolvic.utils.Experience {*;} # Keep remote experience classes used by gson.
-keep class com.igalia.wolvic.utils.RemoteAnnouncements {*;} # Keep announcement classes used by gson.
-keep class com.igalia.wolvic.utils.Announcement {*;} # Keep announcement classes used by gson.
-keep class com.google.gson.reflect.TypeToken { *; }    # Keep this specific gson class
-keep class * extends com.google.gson.reflect.TypeToken # and its descendants.

# --------------------------------------------------------------------
# Keep classes from HTC SDK
# --------------------------------------------------------------------
-keep class com.htc.** {*;}
-keep class com.qualcomm.** {*;}

# --------------------------------------------------------------------
# AppServices & Components
# --------------------------------------------------------------------
-keep class mozilla.appservices.** {*;}
-keep class mozilla.components.concept.engine.manifest.** {*;}

# --------------------------------------------------------------------
# Android ViewModel
# --------------------------------------------------------------------
-keep class * extends androidx.lifecycle.ViewModel {
    <init>();
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}

# ---------------------------------------------------------------------
#HVR SDK
#----------------------------------------------------------------------
-keep class com.huawei.agconnect.**{*;}
-keep class com.huawei.hms.analytics.**{*;}
-keep class com.huawei.hms.push.**{*;}
-keep class com.huawei.usblib.**{*;}
-keep class com.huawei.hvr.**{*;}
-keep class com.huawei.hmf.**{*;}

# ---------------------------------------------------------------------
# UIWidget.createChild() instantiates widgets reflectively via
# aChildClassName.getConstructor(new Class[]{ Context.class }).
#----------------------------------------------------------------------
-keepclassmembers class * extends com.igalia.wolvic.ui.widgets.UIWidget {
    public <init>(android.content.Context);
}

-dontwarn **
-target 1.7
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose
-dontobfuscate
-optimizations !code/simplification/arithmetic,!code/allocation/variable
-keepattributes *
-printconfiguration "build/outputs/mapping/configuration.txt"
