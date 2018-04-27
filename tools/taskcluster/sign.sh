#!/bin/sh
python tools/taskcluster/fetch_secret.py -s project/firefoxreality/preview-keystore -d -o keystore.jks -n store
python tools/taskcluster/fetch_secret.py -s project/firefoxreality/keystore-password -o keystore_password -n password
python tools/taskcluster/fetch_secret.py -s project/firefoxreality/key-password -o key_password -n password
python tools/taskcluster/sign_apk.py -s keystore.jks -p keystore_password -k key_password -a FirefoxRealityTestKey
