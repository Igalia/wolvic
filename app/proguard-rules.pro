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
# Keep classes from FxR
# --------------------------------------------------------------------
-keep class org.mozilla.vrbrowser.ui.widgets.WidgetPlacement {*;} # Keep class used in JNI.
-keep class org.mozilla.vrbrowser.ui.widgets.Windows$** {*;} # Keep state clases used by gson.
-keep class org.mozilla.vrbrowser.browser.engine.** {*;} # Keep state clases used by gson.

# --------------------------------------------------------------------
# Keep classes from HTC SDK
# --------------------------------------------------------------------
-keep class com.htc.** {*;}
-keep class com.qualcomm.** {*;}

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
