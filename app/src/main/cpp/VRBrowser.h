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
#include <functional>

namespace crow {

namespace VRBrowser {
void InitializeJava(JNIEnv* aEnv, jobject aActivity);
JNIEnv * Env();
void ShutdownJava();
void DispatchCreateWidget(jint aWidgetHandle, jobject aSurfaceTexture, jint aWidth, jint aHeight);
void DispatchCreateWidgetLayer(jint aWidgetHandle, jobject aSurface, jint aWidth, jint aHeight, const std::function<void()>& aFirstCompositeCallback);
void HandleMotionEvent(jint aWidgetHandle, jint aController, jboolean aFocused, jboolean aPressed, jfloat aX, jfloat aY);
void HandleScrollEvent(jint aWidgetHandle, jint aController, jfloat aX, jfloat aY);
void HandleAudioPose(jfloat qx, jfloat qy, jfloat qz, jfloat qw, jfloat px, jfloat py, jfloat pz);
void HandleGesture(jint aType);
void HandleResize(jint aWidgetHandle, jfloat aWorldWidth, jfloat aWorldHeight);
void HandleMoveEnd(jint aWidgetHandle, jfloat aX, jfloat aY, jfloat aZ, jfloat aRotation);
void HandleBack();
void RegisterExternalContext(jlong aContext);
void OnEnterWebXR();
void OnExitWebXR(const std::function<void()>& aCallback);
void OnDismissWebXRInterstitial();
void OnWebXRRenderStateChange(const bool aRendering);
void RenderPointerLayer(jobject aSurface, const std::function<void()>& aFirstCompositeCallback);
std::string GetStorageAbsolutePath(const std::string& aRelativePath);
bool isOverrideEnvPathEnabled();
std::string GetActiveEnvironment();
int32_t GetPointerColor();
bool AreLayersEnabled();
void SetDeviceType(const jint aType);
void HaltActivity(const jint aReason);
void HandlePoorPerformance();
void OnAppLink(const std::string& aJSON);
void DisableLayers();
void AppendAppNotesToCrashLog(const std::string& aNotes);
void UpdateControllerBatteryLevels(const jint aLeftBatteryLevel, const jint aRightBatteryLevel);
} // namespace VRBrowser;

} // namespace crow

#endif //VRBROWSER_VRBROWSER_H
