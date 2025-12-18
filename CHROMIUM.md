# Wolvic Chromium backend

Please bear in mind that this is a work in progress and part of the process requires manual steps. We are working on
automating this process. Patches are welcome!

## References

- https://chromium.googlesource.com/chromium/src/+/main/docs/linux/build_instructions.md
- https://chromium.googlesource.com/chromium/src/+/HEAD/docs/android_build_instructions.md

## Setup

The first step is to have a working development environment for Chromium.

### Fresh installation

This section will provide a very brief guide, check the references above for more details.

1.

install [`depot_tools`](https://chromium.googlesource.com/chromium/src/+/main/docs/linux/build_instructions.md#install)

2. `mkdir chromium && cd chromium`
3. `fetch --nohooks --no-history chromium`
4. edit the file `./.gclient` like so:

```
solutions = [
  {
    "name": "src",
    "url": "https://chromium.googlesource.com/chromium/src.git",
    "managed": False,
    "custom_deps": {},
    "custom_vars": {
      "checkout_pgo_profiles": True, 
    },
  },
]
target_os = ["android"]
```

5. `cd src`
6. `./build/install-build-deps.sh --android`
7. `gclient runhooks`
8. `gclient sync`
9. Add wolvic-chromium as a new `git remote` to your existing checkout

```
git remote add wolvic-chromium https://github.com/Igalia/wolvic-chromium
git fetch --depth=1 wolvic-chromium
```

10. Switch to the [wolvic](https://github.com/Igalia/wolvic-chromium/tree/wolvic/) branch: `git switch wolvic`

> **Note**: you can omit the options `--no-history` and `--depth=1` if you want to download the entire history
> from the repository, but be aware that this might take significantly longer.

11. Finally, run `gclient sync` again

### Existing installation

Assuming that you already have everything set up to compile Chrome for Android, you just need to add `wolvic-chromium`
as a new `git remote` to your existing checkout:

```
git remote add wolvic-chromium https://github.com/Igalia/wolvic-chromium
git fetch --depth=1 wolvic-chromium
```

Then, switch to the [wolvic](https://github.com/Igalia/wolvic-chromium/tree/wolvic/) branch.

```
git switch wolvic
```

Finally, make sure that `target_os` is set to Android in `./.gclient`, like so:

```
solutions = [
    ...
]
target_os = ["android"]
```

## Build Chromium

Prepare a build directory, for example `out/Default`:

```
gn gen out/Default
```

For a debug build, edit the file `./out/Default/args.gn` like this:

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

For a release build, edit the file `./out/Default/args.gn` like this:

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

If the `autoninja` command fails, you can try to build directly with `ninja`:
```
ninja -C out/Default content_aar ui_aar
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

The prebuilt AARs `Content.aar` and `ChromiumUi.aar` should be copied at the path `chromium_aar` defined
in `local.properties` file in the Wolvic sources. We will call this location `WHERE_PREBUILT_AARS_ARE` from now on.

Unfortunately, there are known issues to use AARs solely. This should be fixed in the future but until then please do as
follows before copying AARs to wolvic.

```
# fix_aar.sh is contained in wolvic_chromium's `wolvic` branch.
./fix_aar.sh out/Default/Content.aar && ./fix_aar.sh out/Default/ChromiumUi.aar
```

This will generate the new correct AAR files in your `chromium/src` root directory (**not in `out/Default!`**).

The fixed `Content.aar` and `ChromiumUi.aar` need to be copied to the location `WHERE_PREBUILT_AARS_ARE`, where they can
be found by the Wolvic building process.

The following assets also need to be copied into the Wolvic repository located at `${WOLVIC_REPOSITORY}`.

```
mkdir -p ${WOLVIC_REPOSITORY}/app/src/chromium/assets/
cp out/Default/snapshot_blob.bin ${WOLVIC_REPOSITORY}/app/src/chromium/assets/snapshot_blob_64.bin
cp out/Default/icudtl.dat ${WOLVIC_REPOSITORY}/app/src/chromium/assets/
cp out/Default/wolvic.pak ${WOLVIC_REPOSITORY}/app/src/chromium/assets/
```

> **Note**: you have to add the `_64` suffix to the first resource file.

## Build Wolvic

Follow the steps in the [README](README.md) to build Wolvic skipping everything related to Gecko. You'd only need to
tweak a few things.

1. Set the `chromium_aar` variable in `local.properties`:

```
chromium_aar=WHERE_PREBUILT_AARS_ARE
```

2. Sync the Wolvic project with Gradle files

2. Choose build variant:

After adding `chromium_aar` in `local.properties` and syncing gradle, you will see new build variants with the
"Chromium" suffix. Please choose one among them.

**... Build and Run! Enjoy!**
