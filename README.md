# Firefox Reality

The goal of the Firefox Reality project is to create a full-featured browser exclusively for *standalone* AR and VR headsets.

_Warning:_ We have a long way to go before this project can be considered a "full-featured" browser, so if you build from this repo you can expect:

- features will be missing or stubbed out
- slow performance on current generation hardware (we're developing on pre-release headsets!)
- temporary UI that will be replaced as new designs land

You can find us on Twitter [@MozillaReality](https://twitter.com/mozillareality) and at [mixedreality@mozilla.com](mailto:mixedreality@mozilla.com).

## Setup instructions

*Clone FirefoxReality.*

```bash
git clone git@github.com:MozillaReality/FirefoxReality.git
```

*Fetch Git submodules.*

You may need to set up [two-factor authentication](https://blog.github.com/2013-09-03-two-factor-authentication/#how-does-it-work-for-command-line-git) for the command line.

```bash
git submodule update --init --recursive
```

You can build for different devices:

- **`oculusvr`**: Samsung Gear VR & Oculus Go
- **`svr`**: Qualcomm & ODG glasses
- **`wavevr`**: VIVE Focus

These devices are for only testing:

- **`googlevr`**: Google Daydream
- **`noapi`**: Runs on standard Android phones without a headset

Building for Oculus Mobile, SVR, and WaveVR requires access to their respective SDKs which are not included in this repo.

*If you want to build FirefoxReality for Google Daydream (`googlevr`), you need to run:*

```bash
cd gvr-android-sdk && ./gradlew :extractNdk
```

The command line version of `gradlew` requires [JDK 8 from Oracle](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html). If you see an error that Gradle doesn't understand your Java version, check which version of you're using by running `java -showversion` or `java -version`. You're probably using JDK 9 or 10, which won't work.

If you get an error extracting the NDK, you might need to copy the `local.properties file` from the top-level project directory into the `gvr-android-sdk` directory. If this file doesn't exist at the top-level directory either, open the top-level directory in Android Studio, and it should be created.

*Open the project with [Android Studio](https://developer.android.com/studio/index.html)* then build and run it. Depending on what you already have installed in Android Studio, the build may fail and then may prompt you to install dependencies. Just keep doing as it suggests.

If you run the APK on an Android device outside of Daydream or Gear VR, it will run in flat mode. To run in VR, put the device into a headset, and run the app from the VR launcher.

*If you want to build FirefoxReality for WaveVR SDK*

Download the [VIVE Wave SDK](https://developer.vive.com/resources/knowledgebase/wave-sdk/) from the [VIVE Developer Resources](https://vivedeveloper.com/), and unzip it. Then, from the top-level project directory, run:

```bash
mkdir -p third_party/wavesdk
cp /path/to/the/sdk/2.0.32/SDK/libs/wvr_client.aar third_party/wavesdk
cp ./extra/wavesdk/build.gradle ./third_party/wavesdk
```

Make certain to set the build flavor to `wavevrDebug` in Android Studio before building the project.

## Using a custom GeckoView

Create a file called `user.properties` in the top-level project directory. Add a variable called `geckoViewLocal` and set it to the location of your locally built AAR:

```ini
geckoViewLocal=/path/to/your/build/obj-arm-linux-androideabi/gradle/build/mobile/android/geckoview/outputs/aar/geckoview-local-withGeckoBinaries-noMinApi-debug.aar
```

## Debugging tips

- When using the native debugger you can ignore the first SIGSEGV: address access protected stop in GV thread. It's not a crash; you can click *Resume* to continue debugging.
- On some platforms such as Oculus Go the native debugger stops on each input event. You can set this LLDB post-attach command in Android Studio to fix the problem: `pro hand -p true -s false SIGILL`


[![Task Status](https://github.taskcluster.net/v1/repository/MozillaReality/FirefoxReality/master/badge.svg)](https://github.taskcluster.net/v1/repository/MozillaReality/FirefoxReality/master/latest) [Build results](https://github.taskcluster.net/v1/repository/MozillaReality/FirefoxReality/master/latest)
