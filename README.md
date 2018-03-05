## Setup instructions

* Clone VRBrowser
```bash
git clone git@github.com:MozillaReality/VRBrowser.git
```
* Fetch git submodules. You may need https://blog.github.com/2013-09-03-two-factor-authentication/#how-does-it-work-for-command-line-git
```bash
git submodule update --init --recursive
```

* [Build GeckoView](https://developer.mozilla.org/en-US/docs/Mozilla/Developer_guide/Build_Instructions/Simple_Firefox_for_Android_build). Check out the [GECKO_REVISION](GECKO_REVISION) file to know the latest mozilla-inbound revision with which VRBrowser is known to build successfully.

* Until https://bugzilla.mozilla.org/show_bug.cgi?id=1384231 is fixed you probably need to do:

```bash
rustup target add armv7-linux-androideabi
```

* After building GeckoView, run:
```bash
./mach package
./mach gradle assembleLocalWithGeckoBinariesNoMinApi
```

* Copy the resulting `.aar` file to your VRBrowser clone.
```bash
cp objdir-android/gradle/build/mobile/android/geckoview/outputs/aar/geckoview-local-withGeckoBinaries-noMinApi-debug.aar VRBrowser/geckoview-withGeckoBinaries/
```

* You can build VRBrowser for different devices:
    * googlevr => Daydream
    * oculusvr => GearVR & Oculus Go
    * svr => Qualcomm & ODG glasses
    * wavevr => Vive Focus

* If you want to build VRBrowser for Daydream/googlevr, you need to run:
```bash
cd gvr-android-sdk && ./gradlew extractNdk
```

* Finally, open the project with [Android Studio](https://developer.android.com/studio/index.html), build and run it.

