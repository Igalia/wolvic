#/bin/sh
git clone git@github.com:MozillaReality/symbolgenerator.git tools/taskcluster/symbols && cd tools/taskcluster/symbols && git rebase origin/master && cd ../../..

export PYTHONPATH="tools/taskcluster/symbols/third_party/python/redo:tools/taskcluster/symbols/third_party/python/requests"
export OBJCOPY="tools/taskcluster/symbols/bin/arm-linux-androideabi-objcopy"

python tools/taskcluster/symbols/symbolstore.py -c -s . tools/taskcluster/symbols/bin/dump_syms tools/taskcluster/symbols/crashreporter/crashreporter-symbols ./app/build/intermediates/cmake/googlevrArm/release/obj/armeabi-v7a/libnative-lib.so googlevr
python tools/taskcluster/symbols/symbolstore.py -c -s . tools/taskcluster/symbols/bin/dump_syms tools/taskcluster/symbols/crashreporter/crashreporter-symbols ./app/build/intermediates/cmake/noapiArm/release/obj/armeabi-v7a/libnative-lib.so noapi
python tools/taskcluster/symbols/symbolstore.py -c -s . tools/taskcluster/symbols/bin/dump_syms tools/taskcluster/symbols/crashreporter/crashreporter-symbols ./app/build/intermediates/cmake/wavevrArm/release/obj/armeabi-v7a/libnative-lib.so wavevr
python tools/taskcluster/symbols/symbolstore.py -c -s . tools/taskcluster/symbols/bin/dump_syms tools/taskcluster/symbols/crashreporter/crashreporter-symbols ./app/build/intermediates/cmake/svrArm/release/obj/armeabi-v7a/libnative-lib.so svr
python tools/taskcluster/symbols/symbolstore.py -c -s . tools/taskcluster/symbols/bin/dump_syms tools/taskcluster/symbols/crashreporter/crashreporter-symbols ./app/build/intermediates/cmake/oculusvrArm/release/obj/armeabi-v7a/libnative-lib.so oculusvr
