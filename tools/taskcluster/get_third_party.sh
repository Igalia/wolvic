#/bin/sh
mkdir ~/.ssh
chmod go-rwx ~/.ssh
cp tools/ssh/config ~/.ssh
python tools/taskcluster/fetch_secret.py -s project/firefoxreality/github-deploy-key -o ~/.ssh/deploymentkey_rsa -n key
chmod go-rwx ~/.ssh/deploymentkey_rsa
git clone git@github.com:MozillaReality/FirefoxReality-android-third-party.git third_party
pushd third_party && git co -b target-branch `cat ../third_party_hash` && popd
