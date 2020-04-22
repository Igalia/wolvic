/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_PICO_H
#define VRBROWSER_PICO_H

#include <jni.h>

namespace crow {

namespace VRBrowserPico {
void InitializeJava(JNIEnv* aEnv, jobject aActivity);
void ShutdownJava();
void UpdateHaptics(jint aControllerIndex, jfloat aIntensity, jfloat aDuration);
void CancelAllHaptics();
int32_t GetGazeIndex();
} // namespace VRBrowser;

} // namespace crow

#endif //  VRBROWSER_PICO_H
