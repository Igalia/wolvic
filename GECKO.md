# Building GeckoView for Wolvic

**Target Version:** Gecko 128.5.1esr

**Target Platform:** Android AArch64

**Host OS:** Ubuntu 20.04

This guide addresses building the Gecko engine for use in the Wolvic XR browser, as the default Gecko from maven doesn't support WebXR. Because the official Mozilla toolchain artifacts for this version are no longer available, this guide uses a manual system toolchain approach, bypassing `./mach bootstrap`.

## System Prerequisites

Since we cannot use the bootstrap script, we must install the OS-level dependencies manually.

### Core Build Tools

Run the following to install compilers, libraries, and build tools:

```
sudo apt-get update
sudo apt-get install build-essential python3-dev libgtk-3-dev libdbus-glib-1-dev libpulse-dev clang llvm libxml2-dev libx11-xcb-dev cargo mercurial
```

### Node.js (Version 18+)

Gecko 128 requires a newer Node.js than Ubuntu 20.04 provides.

To install Node.js 20, run, as your local user (not root):

```
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash
source ~/.bashrc
nvm install 20
nvm use 20
```

### Rust

Install the Rust dependencies, as your local user (not root).

```
cargo install cbindgen --locked
```

## Get and patch Gecko sources

These are updated instructions from [Igalia's Wiki](https://github.com/Igalia/wolvic/wiki/Developer-workflow#building-gecko).

```
VERSION=128.5.1esr
curl -O https://ftp.mozilla.org/pub/firefox/releases/$VERSION/source/firefox-$VERSION.source.tar.xz
tar -xf firefox-$VERSION.source.tar.xz

git clone https://github.com/Igalia/wolvic-gecko-patches.git

VERSION=128.5.1
cd firefox-$VERSION
find ../wolvic-gecko-patches/gecko-$VERSION/ -type f -name "*patch" -print0 | sort -z | xargs -t -n1 -0 patch -p1 -i
```

## Configure and Build Gecko

Create a file named `mozconfig` in the root of your `firefox-$VERSION` directory. This configuration targets the Android aarch64 release build, and disables the problematic WASI sandboxing.

```
ac_add_options --enable-project=mobile/android
ac_add_options --target=aarch64 --enable-linker=lld
ac_add_options --enable-release --enable-optimize --disable-debug --disable-tests
ac_add_options --without-wasm-sandboxed-libraries
```

Ensure the `$ANDROID_HOME` environment variable points to the Android Sdk location.
Then, we need to prepare the build. This command will download and setup additional build dependencies.

```
./mach bootstrap
```

Then select this option:

```
4. GeckoView/Firefox for Android
```

The bootstrap process will eventually fail, but we don't need to fix it (as long as the error is not related to a missing dependency).
We can continue with the configuration step. It checks wether build dependencies are missing, and if the NDK and SDK paths are correctly set.
When it is OK, we can start the build.

```
./mach configure
./mach build
```

## Integrating with Wolvic

Once the build finishes, you will find the .aar file in your object directory (usually obj-aarch64-linux-android/dist/gradle/geckoview/outputs/aar/).

To use this in the Wolvic source code, add this to local.properties in the Wolvic root:

```
dependencySubstitutions.geckoviewTopsrcdir=/path/to/your/gecko-source
dependencySubstitutions.geckoviewTopobjdir=/path/to/your/gecko-source/obj-aarch64-linux-android
```

To build Wolvic in Release signed mode, create a `user.properties` file at the root of the project, with the following content:

```
useDebugSigningOnRelease=true
```

Rebuild Wolvic using Gradle. It should now pick up your custom-built Gecko engine with WebXR support.

```
./gradlew assembleLynxArm64GeckoGenericRelease
```
