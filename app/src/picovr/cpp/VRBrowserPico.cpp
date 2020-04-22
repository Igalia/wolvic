/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "VRBrowserPico.h"
#include "vrb/ConcreteClass.h"
#include "vrb/Logger.h"
#include "JNIUtil.h"

namespace {

const char* const sUpdateHapticsName = "updateHaptics";
const char* const sUpdateHapticsSignature = "(IFF)V";
const char* const sCancelAllHapticsName = "cancelAllHaptics";
const char* const sCancelAllHapticsSignature = "()V";
const char* const kGetGazeIndex = "getGazeIndex";
const char* const kGetGazeIndexSignature = "()I";

JNIEnv* sEnv = nullptr;
jclass sBrowserClass = nullptr;
jobject sActivity = nullptr;
jmethodID sUpdateHaptics = nullptr;
jmethodID sCancelAllHaptics = nullptr;
jmethodID sGetGazeIndex = nullptr;
}

namespace crow {

void
VRBrowserPico::InitializeJava(JNIEnv* aEnv, jobject aActivity) {
  if (aEnv == sEnv) {
    return;
  }
  sEnv = aEnv;
  if (!sEnv) {
    return;
  }
  sActivity = sEnv->NewGlobalRef(aActivity);
  sBrowserClass = sEnv->GetObjectClass(sActivity);
  if (!sBrowserClass) {
    return;
  }

  sGetGazeIndex = FindJNIMethodID(sEnv, sBrowserClass, kGetGazeIndex, kGetGazeIndexSignature);

  sUpdateHaptics = FindJNIMethodID(sEnv, sBrowserClass, sUpdateHapticsName, sUpdateHapticsSignature);
  sCancelAllHaptics = FindJNIMethodID(sEnv, sBrowserClass, sCancelAllHapticsName, sCancelAllHapticsSignature);
}

void
VRBrowserPico::ShutdownJava() {
  if (!sEnv) {
    return;
  }
  if (sActivity) {
    sEnv->DeleteGlobalRef(sActivity);
    sActivity = nullptr;
  }

  sBrowserClass = nullptr;
  sUpdateHaptics = nullptr;
  sCancelAllHaptics = nullptr;
  sEnv = nullptr;
}


void
VRBrowserPico::UpdateHaptics(jint aControllerIndex, jfloat aIntensity, jfloat aDuration) {
  if (!ValidateMethodID(sEnv, sActivity, sUpdateHaptics, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(sActivity, sUpdateHaptics, aControllerIndex, aIntensity, aDuration);
  CheckJNIException(sEnv, __FUNCTION__);
}
void
VRBrowserPico::CancelAllHaptics() {
  if (!ValidateMethodID(sEnv, sActivity, sCancelAllHaptics, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(sActivity, sCancelAllHaptics);
  CheckJNIException(sEnv, __FUNCTION__);
}

int32_t
VRBrowserPico::GetGazeIndex() {
  if (!ValidateMethodID(sEnv, sActivity, sGetGazeIndex, __FUNCTION__)) { return -1; }
  jint jGazeIndex = (jint) sEnv->CallIntMethod(sActivity, sGetGazeIndex);
  CheckJNIException(sEnv, __FUNCTION__);

  return (int32_t )jGazeIndex;
}

} // namespace crow
