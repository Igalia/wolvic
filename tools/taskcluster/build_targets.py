# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.

"""
This script configures which APKs are built using the options passed in
from the taskcluster {{ event.version }}.

{{ event.version }} should be in the form: <tag name>=[all,release,debug]+<platform name>=[arm,arm64,x86]
Some examples of {{ event.version }} and the resultant output from this script.

This is the default behaviour with no options. Only the Release build of each
architecture for each supported platform is built:
$ python build_targets.py 1.1.4a
assembleWavevrArmRelease assembleNoapiArmRelease assembleNoapiArm64Release assembleNoapiX86Release assembleSvrArmRelease assembleSvrArm64Release assembleOculusvrArmRelease assembleOculusvrArm64Release assembleGooglevrArmRelease assembleGooglevrArm64Release

Specifies only build the Arm64 version of the OculusVR platform:
$ python build_targets.py 1.1.4b+oculusvr=arm64
assembleOculusvrArm64Release

Specifies all build types including Release and Debug:
$ python build_targets.py 1.1.4c=all
assembleWavevrArm assembleNoapiArm assembleNoapiArm64 assembleNoapiX86 assembleSvrArm assembleSvrArm64 assembleOculusvrArm assembleOculusvrArm64 assembleGooglevrArm assembleGooglevrArm64

Specifies Release builds of Arm64 OculusVR, Arm WaveVR, and x86 NoAPI:
$ python build_targets.py 1.1.4d+oculusvr=arm64+wavevr=arm+noapi=x86
assembleOculusvrArm64Release assembleWavevrArmRelease assembleNoapiX86Release

Specifies Release and Debug builds of Arm64 OculusVR, Arm WaveVR, and x86 NoAPI:
$ python build_targets.py 1.1.4e=all+oculusvr=arm64+wavevr=arm+noapi=x86
assembleOculusvrArm64 assembleWavevrArm assembleNoapiX86
"""
import sys

platforms = {
   'oculusvr': ['arm', 'arm64'],
   'oculusvr3dof': ['arm', 'arm64'],
   'wavevr': ['arm'],
   'googlevr': ['arm', 'arm64'],
   'noapi': ['arm', 'arm64', 'x86'],
   'svr': ['arm', 'arm64'],
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
   archList = platforms.get(mode[0], ['arm'])
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
