# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.

"""
This script talks to the taskcluster secrets service to obtain
secret data need to build
"""
import base64
import getopt;
import os
import sys
import taskcluster

def main(name, argv):
   secret_path = ''
   output_file = ''
   data_name = ''
   decode = False;
   try:
      opts, args = getopt.getopt(argv,"hs:o:n:d")
   except getopt.GetoptError:
      print name + '-s <secret> -o <filename> -d'
      sys.exit(2)
   for opt, arg in opts:
      if opt == '-h':
         print name + '-s <secret> -o <filename> -n <data name> -d (decode base64)'
         sys.exit()
      elif opt in ("-s"):
         secret_path = arg
      elif opt in ("-o"):
         output_file = arg
      elif opt in ("-n"):
         data_name = arg
      elif opt in ("-d"):
         decode = True
   if data_name == '':
      data_name = os.path.basename(secret_path)
   secrets = taskcluster.Secrets({'baseUrl': 'http://taskcluster/secrets/v1'})
   data = secrets.get(secret_path)
   data = data['secret'][data_name]
   if decode:
      data = base64.b64decode(data)
   with open(output_file, 'w') as output:
      output.write(data)

if __name__ == "__main__":
   main(sys.argv[0], sys.argv[1:])
