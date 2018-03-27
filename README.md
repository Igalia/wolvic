## Setup instructions

* Clone VRBrowser
```bash
git clone git@github.com:MozillaReality/VRBrowser.git
```
* Fetch git submodules. You may need https://blog.github.com/2013-09-03-two-factor-authentication/#how-does-it-work-for-command-line-git
```bash
git submodule update --init --recursive
```

* You can build VRBrowser for different devices:
    * googlevr => Daydream
    * noapi => Runs on standard Android phone without headset (intended for testing)
    * oculusvr => GearVR & Oculus Go
    * svr => Qualcomm & ODG glasses
    * wavevr => Vive Focus

* Building for Oculus Mobile, SVR, and WaveVR require access to their respective SDKs which are not included.

* If you want to build VRBrowser for Daydream/googlevr, you need to run:
```bash
cd gvr-android-sdk && ./gradlew :extractNdk
```

* Finally, open the project with [Android Studio](https://developer.android.com/studio/index.html), build and run it.

* Building using a local build of GeckoView requires creating a file called user.settings in the top level project directory:
    * add a variable called geckoViewLocal and set it to the location of your locally built AAR:
```
 geckoViewLocal=/path/to/your/build/obj-arm-linux-androideabi/gradle/build/mobile/android/geckoview/outputs/aar/geckoview-local-withGeckoBinaries-noMinApi-debug.aar
```
