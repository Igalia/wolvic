# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.

"""
This script aligns as signs the apks using the options passed in.
"""
import getopt;
import glob
import os
import subprocess
import sys

def main(name, argv):
   token = ''
   sign_url = 'https://autograph-edge.stage.mozaws.net/sign'
   release = False
   try:
      opts, args = getopt.getopt(argv,"ht:r")
   except getopt.GetoptError:
      print name + '-t <token file name> -r'
      sys.exit(2)
   for opt, arg in opts:
      if opt == '-h':
         print name + '-t <token file name> -r'
         sys.exit()
      elif opt in ("-t"):
         token = arg
      elif opt in ('-r'):
         sign_url = 'https://autograph-edge.prod.mozaws.net/sign'
         release = True

   build_output_path = './app/build/outputs/apk'

   # Run zipalign
   for apk in glob.glob(build_output_path + "/*/*/*-unsigned.apk"):
      split = os.path.splitext(apk)
      print subprocess.check_output(["zipalign", "-f", "-v", "-p", "4", apk, split[0] + "-aligned" + split[1]])

   # Sign APKs
   for apk in glob.glob(build_output_path + "/*/*/*-aligned.apk"):
      print "Signing", apk
      if not release:
         apk = apk.replace('release', 'staging')
      print subprocess.check_output([
           "curl",
            "-F", "input=@" + apk,
            "-o", apk.replace('unsigned', 'signed'),
            "-H", "Authorization: " + token,
            sign_url])

   # Create folder for saving build artifacts
   artifacts_path = './builds'
   if not os.path.exists(artifacts_path):
      os.makedirs(artifacts_path)

   # Verify signature and move APK to artifact path
   for apk in glob.glob(build_output_path + "/*/*/*-signed-*.apk"):
      print "Verifying", apk
      print subprocess.check_output(['apksigner', 'verify', apk])

      print "Archiving", apk
      os.rename(apk, artifacts_path + "/" + os.path.basename(apk))

if __name__ == "__main__":
   main(sys.argv[0], sys.argv[1:])
