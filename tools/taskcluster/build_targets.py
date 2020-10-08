# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.

"""
This script configures which APKs are built using the options passed in
from the taskcluster {{ event.version }}.

{{ event.version }} should be in the form: <tag name>=[all,release,debug]+<platform name>=[arm64,x86_64]
Some examples of {{ event.version }} and the resultant output from this script.

This is the default behaviour with no options. Only the Release build of each
architecture for each supported platform is built:
$ python build_targets.py 1.1.4a
assembleNoapiArm64Release assembleNoapiX86_64Release assembleOculusvrArm64Release assembleWavevrstoreArm64Release assemblePicovrArm64Release assemblePicovrStoreArm64Release assembleOculusvrstoreArm64Release assembleWavevrArm64Release

Specifies only build the OculusVR platform:
$ python build_targets.py 1.1.4b+oculusvr
assembleOculusvrArm64Release

Specifies all build types including Release and Debug:
$ python build_targets.py 1.1.4c=all
assembleNoapiArm64 assembleNoapiX86_64 assembleOculusvrArm64 assembleWavevrstoreArm64 assemblePicovrArm64 assemblePicovrStoreArm64 assembleOculusvrstoreArm64 assembleWavevrArm64

Specifies Release builds of Arm64 OculusVR, Arm64 WaveVR, and x86_64 NoAPI:
$ python build_targets.py 1.1.4d+oculusvr+wavevr+noapi=x86_64
assembleOculusvrArm64Release assembleWavevrArm64Release assembleNoapiX86_64Release

Specifies Release and Debug builds of Arm64 OculusVR, Arm64 WaveVR, and x86_64 NoAPI:
$ python build_targets.py 1.1.4e=all+oculusvr+wavevr+noapi=x86_64
assembleOculusvrArm64 assembleWavevrArm64 assembleNoapiX86_64
"""
import sys

platforms = {
   'oculusvr': ['arm64'],
   'oculusvrStore': ['arm64'],
   'wavevr': ['arm64'],
   'wavevrStore': ['arm64'],
   'picovr': ['arm64'],
   'picovrStore': ['arm64'],
   'noapi': ['arm64', 'x86_64'],
}

def findMode(arg):
   values = arg.split('=')
   if len(values) > 1:
     mode = values[1].lower()
     if mode == 'r' or mode == 'release':
        return 'Release'
     elif mode == 'd' or mode == 'debug':
        return 'Debug'
     elif mode == 'a' or mode == 'all':
        return ''
   return 'Release'

def findArch(mode):
   archList = platforms.get(mode[0], ['arm64'])
   if len(mode) > 1:
      value = mode[1]
      if value == 'a' or value == 'all':
         return archList
      elif value.lower() in archList:
         return [value.lower()]
   return [archList[0]]

def main(name, argv):
   targets = ['tag']
   if len(argv) > 0:
      targets = argv[0].split('+')
   mode = findMode(targets[0])
   size = len(targets)
   if size == 1:
      for value in platforms.keys():
         targets.append(value + '=all')
   command = []
   for item in targets[1:]:
      itemList = item.split('=')
      if itemList[0] not in platforms:
         print >> sys.stderr, 'Error: "%s" is not a supported platform' % itemList[0]
         continue
      archList = findArch(itemList)
      for arch in archList:
         command.append('assemble' + itemList[0].capitalize() + arch.capitalize() + mode)

   print ' '.join(command)

if __name__ == '__main__':
   main(sys.argv[0], sys.argv[1:])
