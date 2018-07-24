/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_VRBROWSER_H
#define VRBROWSER_VRBROWSER_H

#include "vrb/MacroUtils.h"

#include <memory>
#include <string>
#include <jni.h>

namespace crow {

namespace VRBrowser {
void InitializeJava(JNIEnv* aEnv, jobject aActivity);
void ShutdownJava();
void DispatchCreateWidget(jint aWidgetHandle, jobject aSurface, jint aWidth, jint aHeight);
void HandleMotionEvent(jint aWidgetHandle, jint aController, jboolean aPressed, jfloat aX, jfloat aY);
void HandleScrollEvent(jint aWidgetHandle, jint aController, jfloat aX, jfloat aY);
void HandleAudioPose(jfloat qx, jfloat qy, jfloat qz, jfloat qw, jfloat px, jfloat py, jfloat pz);
void HandleGesture(jint aType);
void HandleResize(jint aWidgetHandle, jfloat aWorldWidth, jfloat aWorldHeight);
void HandleTrayEvent(jint aType);
void RegisterExternalContext(jlong aContext);
void PauseCompositor();
void ResumeCompositor();
std::string GetStorageAbsolutePath(const std::string& aRelativePath);
} // namespace VRBrowser;

} // namespace crow

#endif //VRBROWSER_VRBROWSER_H
