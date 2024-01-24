# Wolvic XR Browser

The goal of the Wolvic project is to create a full-featured browser exclusively AR and VR headsets.

You can find us in [wolvic.com](https://www.wolvic.com), Mastodon [@WolvicXR](https://floss.social/@WolvicXR), Twitter [@wolvicxr](https://twitter.com/wolvicxr), and at [info@wolvic.com](mailto:info@wolvic.com).

Want to learn more about Wolvic? Read our [FAQ](https://wolvic.com/en/faq)!

## Setup instructions

### GeckoView local substitution

After [PR #70](https://github.com/Igalia/wolvic/pull/70), WebXR sessions won't work with the prebuilt maven GeckoView libraries because that PR introduced a change in the GeckoView protocol. So you have to build GeckoView manually by applying patches at [this repository](https://github.com/Igalia/wolvic-gecko-patches).

This could be done either by using a [local maven repository](http://mozac.org/contributing/testing-components-inside-app) (quite cumbersome), or via Gradle's [dependency substitutions](https://docs.gradle.org/current/userguide/dependency_resolution.html) (not at all cumbersome!).

Currently, the substitution flow is streamlined for some of the core dependencies via configuration flags in `local.properties`. You can build against a local checkout of the following dependencies by specifying their local paths:
- [GeckoView](https://hg.mozilla.org/releases/mozilla-release/tags), specifying its path via `dependencySubstitutions.geckoviewTopsrcdir=/path/to/mozilla-release` (and, optionally, `dependencySubstitutions.geckoviewTopobjdir=/path/to/topobjdir`). See [Bug 1533465](https://bugzilla.mozilla.org/show_bug.cgi?id=1533465).
  - This assumes that you have built, packaged, and published your local GeckoView -- but don't worry, the dependency substitution script has the latest instructions for doing that.
  - If you want to build for different architectures at the same time, you can specify the aarch64 path via `dependencySubstitutions.geckoviewTopobjdir` while the x86_64 path via `dependencySubstitutions.geckoviewTopobjdirX64`

Do not forget to run a Gradle sync in Android Studio after changing `local.properties`. If you specified any substitutions, they will be reflected in the modules list, and you'll be able to modify them from a single Android Studio window.

For step by step guide check [here](https://github.com/Igalia/wolvic/wiki/Developer-workflow#building-gecko).

### Clone Wolvic

```bash
git clone git@github.com:Igalia/wolvic.git
cd wolvic
```

### Clone the third-party repo

If you're developing for the Oculus, Huawei, Pico, or VIVE, you need to clone the repo with third-party SDK files.

```bash
git clone https://github.com/Igalia/wolvic-third-parties.git third_party
```

This repo is only available to Igalia members. If you have access to the relevant SDK but not this repo, you can manually place them here:

 - `third_party/ovr_mobile/` for Oculus (should contain a `VrApi` folder)
 - `third_party/OVRPlatformSDK/` for Oculus (should contain a `Android` and `include` folders)
 - `third_party/ovr_openxr_mobile_sdk/` for Oculus (should contain an `OpenXR` folder)
 - `third_party/hvr/` for Huawei (should contain  `arm64-v8a`, `armeabi-v7a` and `include` folders)
 - `third_party/wavesdk/` for Vive (should contain a `build` folder, among other things)
 - `third_party/picoxr` [Pico OpenXR Mobile SDK](https://developer-global.pico-interactive.com/sdk?deviceId=1&platformId=3&itemId=11) (should contain `include` and `libs` folders, among other things that are not necessary for Wolvic)
 - `third_party/lynx` [for Lynx](https://portal.lynx-r.com)(should contain a `loader-release.aar` file)
 - `third_party/spaces` [for Snapdragon Spaces](https://spaces.qualcomm.com/)(should contain `libopenxr_loader.aar`)
 - `third_party/OpenXR-SDK/` [OpenXR-SDK](https://github.com/KhronosGroup/OpenXR-SDK) (should contain an `include` folder)
 - `third_party/aliceimu/` for [Huawei Vision Glass](https://consumer.huawei.com/cn/wearables/vision-glass/) (should contain an `.aar` file with the IMU library for the glasses)

The [repo in `third_party`](https://github.com/Igalia/wolvic-third-parties) can be updated like so:

```bash
pushd third_party && git fetch && git checkout main && git rebase origin/main && popd
```

### Fetch Git submodules

You may need to set up [two-factor authentication](https://blog.github.com/2013-09-03-two-factor-authentication/#how-does-it-work-for-command-line-git) for the command line.

```bash
git submodule update --init --recursive
```

You can build for different devices:

- **`oculusvr`**: Oculus Quest
- **`hvr`**: Huawei VR Glasses
- **`wavevr`**: VIVE Focus
- **`picoxr`**: Pico 4 and (untested) Pico Neo 3
- **`lynx`**: Lynx R1
- **`spaces`**: Lenovo A3
- **`visionglass`**: Huawei Vision Glass

For testing on a non-VR device:

- **`noapi`**: Runs on standard Android phones without a headset

Building for Oculus Mobile, Huawei and WaveVR requires access to their respective SDKs which are not included in this repo.

The command line version of `gradlew` requires JDK 11. If you see an error that Gradle doesn't understand your Java version, check which version of you're using by running `java -showversion` or `java -version`. You're probably using and older JDK, which won't work.

*Open the project with [Android Studio](https://developer.android.com/studio/index.html)* then build and run it. Depending on what you already have installed in Android Studio, the build may fail and then may prompt you to install dependencies. Just keep doing as it suggests. To select the device to build for, go to `Tool Windows > Build Variants` and select a build variant corresponding to your device.

*If you want to build Wolvic for WaveVR SDK:*

Download the [VIVE Wave SDK](https://developer.vive.com/resources/knowledgebase/wave-sdk/) from the [VIVE Developer Resources](https://developer.vive.com/resources/vive-wave/download/archive/320/), and unzip it. Then, from the top-level project directory, run:

```bash
mkdir -p third_party/wavesdk
cp wave_3.2.0_native_1/repo/com/htc/vr/wvr_client/3.2.0/wvr_client-3.2.0.aar third_party/wavesdk/wvr_client.aar
```

Make certain to set the build flavor to `wavevrDebug` in Android Studio before building the project.

## Local Development

> By default Wolvic will try to download prebuilt GeckoView libraries from [Mozilla's maven repositories](https://maven.mozilla.org/maven2/org/mozilla/geckoview/?prefix=maven2/org/mozilla/geckoview/), where WebXR won't work and that should be used just for testing the 2D browser or to download browser features. If you want to have this, just skip the *GeckoView local substitution* part of the [Setup instructions](#setup-instructions).

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

## Enable OpenXR builds for Meta devices
You can enable OpenXR API for Oculus by adding this property to your `user.properties` file:

```ini
openxr=true
```
## Locale support

For more info on localization, how it works in the Wolvic XR project, and how to correctly edit localizable text in the application, please see our [localization wiki page](https://github.com/Igalia/wolvic/wiki/Localization).

## Development troubleshooting

### `Device supports , but APK only supports armeabi-v7a[...]`

Enable [USB Remote Debugging](https://github.com/MozillaReality/FirefoxReality/wiki/Developer-Info#remote-debugging) on the device.

### **`Firefox > Web Developer > WebIDE > Performance`** gets stuck with greyed out "stop and show profile"

Restart Wolvic XR and close and re-open the WebIDE page.

### **`Tool Windows > Build Variants`** list is empty

1. If you're not on the latest version, update Android Studio from **`Android Studio > Check for Updates…`**.
2. Run **`File > Sync Project with Gradle Files`**.

## Debugging tips

- When using the native debugger you can ignore the first SIGSEGV: address access protected stop in GV thread. It's not a crash; you can click *Resume* to continue debugging.
- On some platforms such as Oculus Go the native debugger stops on each input event. You can set this LLDB post-attach command in Android Studio to fix the problem: `pro hand -p true -s false SIGILL`
- You can use `adb shell am start -a android.intent.action.VIEW -d "https://aframe.io" com.igalia.wolvic/com.igalia.wolvic.VRBrowserActivity` to load a URL from the command line
- You can use `adb shell am start -a android.intent.action.VIEW  -n com.igalia.wolvic/com.igalia.wolvic.VRBrowserActivity -e homepage "https://example.com"` to override the homepage
- You can use `adb shell setprop debug.oculus.enableVideoCapture 1` to record a video on the Oculus Go. Remember to run `adb shell setprop debug.oculus.enableVideoCapture 0` to stop recording the video.
    - You can also record videos on the Oculus Go by exiting to the system library, and from the Oculus tray menu (toggle with the Oculus button on the controller): **`Sharing > Record Video`**
- You can set `disableCrashRestart=true` in the gradle `user.properties` to disable app relaunch on crash.
