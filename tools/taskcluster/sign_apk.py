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
   sign_url = 'https://edge.stage.autograph.services.mozaws.net/sign'
   release = False
   feature_name = ""
   try:
      opts, args = getopt.getopt(argv,"hrt:f:")
   except getopt.GetoptError:
      print name + ' -t <token file name> -r -f <feature name>'
      sys.exit(2)
   for opt, arg in opts:
      if opt == '-h':
         print name + ' -t <token file name> -r -f <feature name>'
         sys.exit()
      elif opt in ("-t"):
         with open(arg, 'r') as tokenfile:
            token = tokenfile.read().rstrip()
      elif opt in ('-r'):
         sign_url = 'https://edge.prod.autograph.services.mozaws.net/sign'
         release = True
      elif opt in ('-f'):
         feature_name = arg.replace('/','-') + '-'


   build_output_path = './app/build/outputs/apk'
   # Create folder for saving build artifacts
   artifacts_path = './builds'
   if not os.path.exists(artifacts_path):
      os.makedirs(artifacts_path)

   # Sign APKs
   for apk in glob.glob(build_output_path + "/*/*/*-unsigned.apk"):
      target = apk.replace('-unsigned', '-signed')
      if not release:
         target = target.replace('-release-', '-staging-' + feature_name)
      print "Signing", apk
      print "Target ", target
      print subprocess.check_output([
           "curl",
            "-F", "input=@" + apk,
            "-o", target,
            "-H", "Authorization: " + token,
            sign_url])
      print "Verifying", target
      print subprocess.check_output(['apksigner', 'verify', target])
      print "Archiving", target
      os.rename(target, artifacts_path + "/" + os.path.basename(target))

if __name__ == "__main__":
   main(sys.argv[0], sys.argv[1:])
