#/bin/sh
git clone git@github.com:MozillaReality/symbolgenerator.git tools/taskcluster/symbols && cd tools/taskcluster/symbols && git rebase origin/master && cd ../../..

export PYTHONPATH="tools/taskcluster/symbols/third_party/python/redo:tools/taskcluster/symbols/third_party/python/requests"

python tools/taskcluster/symbols/find_symbols.py
