# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.

"""
This script publishes the debug apks.
"""
import getopt;
import glob
import os
import subprocess
import sys

def main(name, argv):
   build_output_path = './app/build/outputs/apk'
   artifacts_path = './builds'

   # Run zipalign
   for apk in glob.glob(build_output_path + "/*/*/*-debug.apk"):
      print "Moving", apk, "to", artifacts_path + "/" + os.path.basename(apk)
      os.rename(apk, artifacts_path + "/" + os.path.basename(apk))

if __name__ == "__main__":
   main(sys.argv[0], sys.argv[1:])
