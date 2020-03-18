/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#pragma once
#include "vrb/MacroUtils.h"

#include <memory>
#include <string>
#include <jni.h>
#include <functional>

namespace {
const char *kGetGazeIndex = "getGazeIndex";
const char *kGetGazeIndexSignature = "()I";

jmethodID sGetGazeIndex = nullptr;
}

namespace crow {

  namespace VRBrowserPico {
    void InitializeJava(JNIEnv* aEnv, jobject aActivity);
    void ShutdownJava();
    void UpdateHaptics(jint aControllerIndex, jfloat aIntensity, jfloat aDuration);
    void CancelAllHaptics();
    int32_t GetGazeIndex();
  } // namespace VRBrowser;

} // namespace crow
