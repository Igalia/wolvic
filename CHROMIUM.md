# Wolvic Chromium backend

Please bear in mind that this is a work in progress and part of the process requires manual steps. We are working on automating this process. Patches are welcome!

## Setup

1. Clone [wolvic-chromium](https://github.com/Igalia/wolvic-chromium/)

Alternatively you can add wolvic-chromium as a new `git remote` to your existing checkout
```
git remote add wolvic-chromium https://github.com/Igalia/wolvic-chromium
git fetch wolvic-chromium
```
2. Switch to [wolvic](https://github.com/Igalia/wolvic-chromium/tree/wolvic/) branch

## Build Chromium

First, make sure that `target_os` is set appropriately in `.gclient`, like so:
```
solutions = [
    {
        ...
    }
]
target_os = ["android"]
```

_GN args (for a debug build):_

```
target_os = "android"
target_cpu = "arm64"
is_debug = true
is_component_build = false
use_allocator_shim = true
blink_symbol_level = 0
ffmpeg_branding="Chrome"
proprietary_codecs=true
```

_GN args (for a release build):_

```
target_os = "android"
target_cpu = "arm64"
is_official_build = true
ffmpeg_branding="Chrome"
proprietary_codecs=true
# exclude_unwind_tables = false # Optional, allows to get stack traces on release builds.
```

> **Note**: most AOSP devices are arm64, but you should use the architecture of your device and set the appropriate value for `target_cpu`. You can check the architecture of your device by running `adb shell getprop ro.product.cpu.abi`.

_Build Command:_

```
autoninja -C out/Default content_aar ui_aar
```

This might take a lot of time.

### Build issues since M124

After the recent upgrade to M124 the build fails at the very end with the following error
```
../../content/shell/browser/shell_devtools_manager_delegate.cc:33:10: fatal error: 'content/shell/grit/shell_resources.h' file not found
   33 | #include "content/shell/grit/shell_resources.h"

```

In order to fix this issue, you need to run the following command
```
autoninja -C out/Default content/shell:content_shell_resources_grit 
```
And then resume the build using the previous command.

## After build tasks

These prebuilt AARs should be copied under the path `chromium_aar` defined in `local.properties` file in the Wolvic sources.

Unfortunately, there are known issues to use AARs solely. This should be fixed in the future but until then please do as follows before copying AARs to wolvic.

```
# fix_aar.sh is contained in wolvic_chromium's `wolvic` branch.
./fix_aar.sh out/Default/Content.aar && ./fix_aar.sh out/Default/ChromiumUi.aar
```

This will generate the new correct AAR files in your `chromium/src` root directory (**not in `out/Default!`**).

Also, following assets are needed to copy in Wolvic. **NOTE**: you have to add the `_64` suffix to the first resource file.

```
mkdir -p ${wolvic}/app/src/chromium/assets/
cp out/Default/snapshot_blob.bin ${wolvic}/app/src/chromium/assets/snapshot_blob_64.bin && cp out/Default/icudtl.dat ${wolvic}/app/src/chromium/assets/ && cp out/Default/wolvic.pak ${wolvic}/app/src/chromium/assets/
```

## Build Wolvic

Follow the steps in the [README](README.md) to build Wolvic skipping everything related to Gecko. You'd only need to tweak a few things.

_Set `local.properties`:_

```
chromium_aar = ${WHERE_PREBUILT_AARS_ARE}
```

_Choose build variant:_

After adding `chromium_aar` in user.properties and syncing gradle, you can see various build variants with Chromium suffix. Please choose one among them.

**... Build and Run! Enjoy!**
