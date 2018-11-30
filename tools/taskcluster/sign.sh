#!/bin/sh
python tools/taskcluster/fetch_secret.py -s project/firefoxreality/$1 -o token -n token
python tools/taskcluster/sign_apk.py -t token $2
python tools/taskcluster/archive_debug_apk.py
