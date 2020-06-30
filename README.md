# Firefox Reality

The goal of the Firefox Reality project is to create a full-featured browser exclusively for *standalone* AR and VR headsets.

You can find us in [#fxr:mozilla.org on the Matrix](https://chat.mozilla.org/#/room/#fxr:mozilla.org) ([Instructions for joining](https://wiki.mozilla.org/Matrix)), Twitter [@MozillaReality](https://twitter.com/mozillareality), and at [mixedreality@mozilla.com](mailto:mixedreality@mozilla.com).

## Download developer APKs

**[Download](https://community-tc.services.mozilla.com/tasks/index/project.firefoxreality/main)** developer APKs for Firefox Reality generated from latest [main](https://github.com/MozillaReality/FirefoxReality/commits/main).

## Locale support

Firefox Reality supports Chinese (China), Chinese (Taiwan), English, French, German, Italian, Japanese, Russian, and Spanish for user interface, voice search, and text entry. Localization is provided by Mozilla's amazing community of volunteer localizers.

For more info on localization, how it works in the Firefox Reality project, and how to correctly edit localizable text in the application, please see the [localization wiki page](https://github.com/MozillaReality/FirefoxReality/wiki/Localization).

## Setup instructions

*Clone FirefoxReality.*

```bash
git clone git@github.com:MozillaReality/FirefoxReality.git
cd FirefoxReality
```

*Clone the third-party repo.*

If you're developing for the Oculus, Snapdragon VR, or VIVE, you need to clone the repo with third-party SDK files.

```bash
git clone git@github.com:MozillaReality/FirefoxReality-android-third-party.git third_party
```

This repo is only available to Mozilla employees. If you have access to the relevant SDK but not this repo, you can manually place them here:

 - `third_party/ovr_mobile/` for Oculus (should contain a `VrApi` folder)
 - `third_party/wavesdk/` for Vive (should contain a `build` folder, among other things)

The [repo in `third_party`](https://github.com/MozillaReality/FirefoxReality-android-third-party) can be updated like so:

```bash
pushd third_party && git fetch && git checkout main && git rebase origin/main && popd
```

*Fetch Git submodules.*

You may need to set up [two-factor authentication](https://blog.github.com/2013-09-03-two-factor-authentication/#how-does-it-work-for-command-line-git) for the command line.

```bash
git submodule update --init --recursive
```

You can build for different devices:

- **`oculusvr`**: Samsung Gear VR & Oculus Go
- **`wavevr`**: VIVE Focus

For testing on a non-VR device:

- **`noapi`**: Runs on standard Android phones without a headset

Building for Oculus Mobile, and WaveVR requires access to their respective SDKs which are not included in this repo.

The command line version of `gradlew` requires [JDK 8 from Oracle](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html). If you see an error that Gradle doesn't understand your Java version, check which version of you're using by running `java -showversion` or `java -version`. You're probably using JDK 9 or 10, which won't work.

*Open the project with [Android Studio](https://developer.android.com/studio/index.html)* then build and run it. Depending on what you already have installed in Android Studio, the build may fail and then may prompt you to install dependencies. Just keep doing as it suggests. To select the device to build for, go to `Tool Windows > Build Variants` and select a build variant corresponding to your device.

*If you want to build FirefoxReality for WaveVR SDK:*

Download the [VIVE Wave SDK](https://developer.vive.com/resources/knowledgebase/wave-sdk/) from the [VIVE Developer Resources](https://vivedeveloper.com/), and unzip it. Then, from the top-level project directory, run:

```bash
mkdir -p third_party/wavesdk
cp /path/to/the/sdk/2.0.32/SDK/libs/wvr_client.aar third_party/wavesdk
cp ./extra/wavesdk/build.gradle ./third_party/wavesdk
```

Make certain to set the build flavor to `wavevrDebug` in Android Studio before building the project.

## Local Development

### Dependency substitutions

You might be interested in building this project against local versions of some of the dependencies.
This could be done either by using a [local maven repository](https://mozilla-mobile.github.io/android-components/contributing/testing-components-inside-app) (quite cumbersome), or via Gradle's [dependency substitutions](https://docs.gradle.org/current/userguide/customizing_dependency_resolution_behavior.html) (not at all cumbersome!).

Currently, the substitution flow is streamlined for some of the core dependencies via configuration flags in `local.properties`. You can build against a local checkout of the following dependencies by specifying their local paths:
- [GeckoView](https://hg.mozilla.org/mozilla-central), specifying its path via `dependencySubstitutions.geckoviewTopsrcdir=/path/to/mozilla-central` (and, optionally, `dependencySubstitutions.geckoviewTopobjdir=/path/to/topobjdir`). See [Bug 1533465](https://bugzilla.mozilla.org/show_bug.cgi?id=1533465).
  - This assumes that you have built, packaged, and published your local GeckoView -- but don't worry, the dependency substitution script has the latest instructions for doing that.

Do not forget to run a Gradle sync in Android Studio after changing `local.properties`. If you specified any substitutions, they will be reflected in the modules list, and you'll be able to modify them from a single Android Studio window.


## Install dev and production builds on device simultaneously

You can enable a dev applicationID sufix to install both dev and production builds simultaneously. You just need to add this property to your `user.properties` file:

```ini
simultaneousDevProduction=true
```
## Locally generate Android release builds

Local release builds can be useful to measure performance or debug issues only happening in release builds. Insead of dealing with release keys you can make the testing easier just adding this property to your `user.properties` file:

```ini
useDebugSigningOnRelease=true
```

Note: the release APKs generated with a debug keystore can't be used for production.

## Compress assets

ETC2 compression is used to improve performance and memory usage. Raw assets are placed in the `uncompressed_assets` folder. You can generate the compressed textures using the compressor utility in `tools/compressor`. You need to set up [etc2comp](https://github.com/google/etc2comp) and make it available on your PATH before running the script. Run this command to generate the compressed assets:

```bash
cd tools/compressor
npm install
npm run compress
```

## Enable OpenXR builds
You can enable OpenXR API for Oculus by adding this property to your `user.properties` file:

```ini
openxr=true
```

## Development troubleshooting

### `Device supports , but APK only supports armeabi-v7a[...]`

Enable [USB Remote Debugging](https://github.com/MozillaReality/FirefoxReality/wiki/Developer-Info#remote-debugging) on the device.

### **`Firefox > Web Developer > WebIDE > Performance`** gets stuck with greyed out "stop and show profile"

Restart FxR and close and re-open the WebIDE page.

### **`Tool Windows > Build Variants`** list is empty

1. If you're not on the latest version, update Android Studio from **`Android Studio > Check for Updates…`**.
2. Run **`File > Sync Project with Gradle Files`**.

## Debugging tips

- When using the native debugger you can ignore the first SIGSEGV: address access protected stop in GV thread. It's not a crash; you can click *Resume* to continue debugging.
- On some platforms such as Oculus Go the native debugger stops on each input event. You can set this LLDB post-attach command in Android Studio to fix the problem: `pro hand -p true -s false SIGILL`
- You can use `adb shell am start -a android.intent.action.VIEW -d "https://aframe.io" org.mozilla.vrbrowser/org.mozilla.vrbrowser.VRBrowserActivity` to load a URL from the command line
- You can use `adb shell am start -a android.intent.action.VIEW  -n org.mozilla.vrbrowser/org.mozilla.vrbrowser.VRBrowserActivity -e homepage "https://example.com"` to override the homepage
- You can use `adb shell setprop debug.oculus.enableVideoCapture 1` to record a video on the Oculus Go. Remember to run `adb shell setprop debug.oculus.enableVideoCapture 0` to stop recording the video.
    - You can also record videos on the Oculus Go by exiting to the system library, and from the Oculus tray menu (toggle with the Oculus button on the controller): **`Sharing > Record Video`**
- You can set `disableCrashRestart=true` in the gradle `user.properties` to disable app relaunch on crash.

## Experimental Servo support

To compile with Servo support, create a file called `user.properties` in the top-level project directory and add `enableServo=1`. Then to enable Servo in Firefox Reality, go the Developer Options panel in the Settings, and toggle the Servo option. Then a new button will be added to the navigation bar. Clicking that button will reload the current page with Servo.
