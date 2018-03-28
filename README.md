# Firefox Reality

The goal of the Firefox Reality project is to create a full-featured browser exclusively for *stand-alone* AR and VR headsets.

Warning: We have a long way to go before this project can be considered a "full-featured" browser, so if you build from this repo you can expect:

- features will be missing or stubbed out
- slow performance on current generation hardware (we're developing on pre-release headsets!)
- temporary UI that will be replaced as new designs land

You can find us on Twitter [@MozillaReality](https://twitter.com/mozillareality) and at [mixedreality@mozilla.com](mailto:mixedreality@mozilla.com).

## Setup instructions

*Clone FirefoxReality*

```bash
git clone git@github.com:MozillaReality/FirefoxReality.git
```

*Fetch git submodules.*

You may need to set up [two factor auth](https://blog.github.com/2013-09-03-two-factor-authentication/#how-does-it-work-for-command-line-git) for the command line.

```bash
git submodule update --init --recursive
```

You can build for different devices:

- oculusvr => GearVR & Oculus Go
- svr => Qualcomm & ODG glasses
- wavevr => Vive Focus

These devices are only for testing:

- googlevr => Daydream
- noapi => Runs on standard Android phone without headset

Building for Oculus Mobile, SVR, and WaveVR requires access to their respective SDKs which are not included in this repo.

*If you want to build FirefoxReality for Daydream/googlevr, you need to run:*

```bash
cd gvr-android-sdk && ./gradlew :extractNdk
```

The command line version of `gradlew` requires [JDK 8 from Oracle](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html). If you see an error that gradle doesn't understand your Java version, check which version of you're using by running `java -showversion` or `java -version`. You're probably using JDK 9 or 10, which won't work.

If you get an error extracting the NDK, you might need to copy the local.properties file from the root project directory into the gvr-android-sdk directory. If this file doesn't exist at the top level either, open the top level project in Android Studio and it should be created.

*Open the project with [Android Studio](https://developer.android.com/studio/index.html)* then build and run it. Depending on what you already have installed in Android Studio, the build may fail and then may prompt you to install dependencies. Just keep doing as it suggests.

If you run the APK on an Android device outside of Daydream or GearVR, it will run in flat mode. To run in VR, put the device into a headset and run the app from the VR launcher.

## Using a custom GeckoView

Create a file called user.settings in the top level project directory. Add a variable called geckoViewLocal and set it to the location of your locally built AAR:

```
 geckoViewLocal=/path/to/your/build/obj-arm-linux-androideabi/gradle/build/mobile/android/geckoview/outputs/aar/geckoview-local-withGeckoBinaries-noMinApi-debug.aar
```


