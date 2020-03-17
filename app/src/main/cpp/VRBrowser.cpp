/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "VRBrowser.h"
#include "vrb/ConcreteClass.h"
#include "vrb/Logger.h"
#include "JNIUtil.h"

namespace {

const char* kDispatchCreateWidgetName = "dispatchCreateWidget";
const char* kDispatchCreateWidgetSignature = "(ILandroid/graphics/SurfaceTexture;II)V";
const char* kDispatchCreateWidgetLayerName = "dispatchCreateWidgetLayer";
const char* kDispatchCreateWidgetLayerSignature = "(ILandroid/view/Surface;IIJ)V";
const char* kHandleMotionEventName = "handleMotionEvent";
const char* kHandleMotionEventSignature = "(IIZZFF)V";
const char* kHandleScrollEventName = "handleScrollEvent";
const char* kHandleScrollEventSignature = "(IIFF)V";
const char* kHandleAudioPoseName = "handleAudioPose";
const char* kHandleAudioPoseSignature = "(FFFFFFF)V";
const char* kHandleGestureName = "handleGesture";
const char* kHandleGestureSignature = "(I)V";
const char* kHandleResizeName = "handleResize";
const char* kHandleResizeSignature = "(IFF)V";
const char* kHandleMoveEndName = "handleMoveEnd";
const char* kHandleMoveEndSignature = "(IFFFF)V";
const char* kHandleBackEventName = "handleBack";
const char* kHandleBackEventSignature = "()V";
const char* kRegisterExternalContextName = "registerExternalContext";
const char* kRegisterExternalContextSignature = "(J)V";
const char* kPauseCompositorName = "pauseGeckoViewCompositor";
const char* kPauseCompositorSignature = "()V";
const char* kResumeCompositorName = "resumeGeckoViewCompositor";
const char* kResumeCompositorSignature = "()V";
const char* kRenderPointerLayerName = "renderPointerLayer";
const char* kRenderPointerLayerSignature = "(Landroid/view/Surface;J)V";
const char* kGetStorageAbsolutePathName = "getStorageAbsolutePath";
const char* kGetStorageAbsolutePathSignature = "()Ljava/lang/String;";
const char* kIsOverrideEnvPathEnabledName = "isOverrideEnvPathEnabled";
const char* kIsOverrideEnvPathEnabledSignature = "()Z";
const char* kGetActiveEnvironment = "getActiveEnvironment";
const char* kGetActiveEnvironmentSignature = "()Ljava/lang/String;";
const char* kGetPointerColor = "getPointerColor";
const char* kGetPointerColorSignature = "()I";
const char* kAreLayersEnabled = "areLayersEnabled";
const char* kAreLayersEnabledSignature = "()Z";
const char* kSetDeviceType = "setDeviceType";
const char* kSetDeviceTypeSignature = "(I)V";
const char* kHaltActivity = "haltActivity";
const char* kHaltActivitySignature = "(I)V";
const char* kHandlePoorPerformance = "handlePoorPerformance";
const char* kHandlePoorPerformanceSignature = "()V";

JNIEnv* sEnv = nullptr;
jclass sBrowserClass = nullptr;
jobject sActivity = nullptr;
jmethodID sDispatchCreateWidget = nullptr;
jmethodID sDispatchCreateWidgetLayer = nullptr;
jmethodID sHandleMotionEvent = nullptr;
jmethodID sHandleScrollEvent = nullptr;
jmethodID sHandleAudioPose = nullptr;
jmethodID sHandleGesture = nullptr;
jmethodID sHandleResize = nullptr;
jmethodID sHandleMoveEnd = nullptr;
jmethodID sHandleBack = nullptr;
jmethodID sRegisterExternalContext = nullptr;
jmethodID sPauseCompositor = nullptr;
jmethodID sResumeCompositor = nullptr;
jmethodID sRenderPointerLayer = nullptr;
jmethodID sGetStorageAbsolutePath = nullptr;
jmethodID sIsOverrideEnvPathEnabled = nullptr;
jmethodID sGetActiveEnvironment = nullptr;
jmethodID sGetPointerColor = nullptr;
jmethodID sAreLayersEnabled = nullptr;
jmethodID sSetDeviceType = nullptr;
jmethodID sHaltActivity = nullptr;
jmethodID sHandlePoorPerformance = nullptr;
}

namespace crow {

void
VRBrowser::InitializeJava(JNIEnv* aEnv, jobject aActivity) {
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

  sDispatchCreateWidget = FindJNIMethodID(sEnv, sBrowserClass, kDispatchCreateWidgetName, kDispatchCreateWidgetSignature);
  sDispatchCreateWidgetLayer = FindJNIMethodID(sEnv, sBrowserClass, kDispatchCreateWidgetLayerName, kDispatchCreateWidgetLayerSignature);
  sHandleMotionEvent = FindJNIMethodID(sEnv, sBrowserClass, kHandleMotionEventName, kHandleMotionEventSignature);
  sHandleScrollEvent = FindJNIMethodID(sEnv, sBrowserClass, kHandleScrollEventName, kHandleScrollEventSignature);
  sHandleAudioPose = FindJNIMethodID(sEnv, sBrowserClass, kHandleAudioPoseName, kHandleAudioPoseSignature);
  sHandleGesture = FindJNIMethodID(sEnv, sBrowserClass, kHandleGestureName, kHandleGestureSignature);
  sHandleResize = FindJNIMethodID(sEnv, sBrowserClass, kHandleResizeName, kHandleResizeSignature);
  sHandleMoveEnd = FindJNIMethodID(sEnv, sBrowserClass, kHandleMoveEndName, kHandleMoveEndSignature);
  sHandleBack = FindJNIMethodID(sEnv, sBrowserClass, kHandleBackEventName, kHandleBackEventSignature);
  sRegisterExternalContext = FindJNIMethodID(sEnv, sBrowserClass, kRegisterExternalContextName, kRegisterExternalContextSignature);
  sPauseCompositor = FindJNIMethodID(sEnv, sBrowserClass, kPauseCompositorName, kPauseCompositorSignature);
  sResumeCompositor = FindJNIMethodID(sEnv, sBrowserClass, kResumeCompositorName, kResumeCompositorSignature);
  sRenderPointerLayer = FindJNIMethodID(sEnv, sBrowserClass, kRenderPointerLayerName, kRenderPointerLayerSignature);
  sGetStorageAbsolutePath = FindJNIMethodID(sEnv, sBrowserClass, kGetStorageAbsolutePathName, kGetStorageAbsolutePathSignature);
  sIsOverrideEnvPathEnabled = FindJNIMethodID(sEnv, sBrowserClass, kIsOverrideEnvPathEnabledName, kIsOverrideEnvPathEnabledSignature);
  sGetActiveEnvironment = FindJNIMethodID(sEnv, sBrowserClass, kGetActiveEnvironment, kGetActiveEnvironmentSignature);
  sGetPointerColor = FindJNIMethodID(sEnv, sBrowserClass, kGetPointerColor, kGetPointerColorSignature);
  sAreLayersEnabled = FindJNIMethodID(sEnv, sBrowserClass, kAreLayersEnabled, kAreLayersEnabledSignature);
  sSetDeviceType = FindJNIMethodID(sEnv, sBrowserClass, kSetDeviceType, kSetDeviceTypeSignature);
  sHaltActivity = FindJNIMethodID(sEnv, sBrowserClass, kHaltActivity, kHaltActivitySignature);
  sHandlePoorPerformance = FindJNIMethodID(sEnv, sBrowserClass, kHandlePoorPerformance, kHandlePoorPerformanceSignature);
}

void
VRBrowser::ShutdownJava() {
  if (!sEnv) {
    return;
  }
  if (sActivity) {
    sEnv->DeleteGlobalRef(sActivity);
    sActivity = nullptr;
  }

  sBrowserClass = nullptr;

  sDispatchCreateWidget = nullptr;
  sDispatchCreateWidgetLayer = nullptr;
  sHandleMotionEvent = nullptr;
  sHandleScrollEvent = nullptr;
  sHandleAudioPose = nullptr;
  sHandleGesture = nullptr;
  sHandleResize = nullptr;
  sHandleMoveEnd = nullptr;
  sHandleBack = nullptr;
  sRegisterExternalContext = nullptr;
  sPauseCompositor = nullptr;
  sResumeCompositor = nullptr;
  sRenderPointerLayer = nullptr;
  sGetStorageAbsolutePath = nullptr;
  sIsOverrideEnvPathEnabled = nullptr;
  sGetActiveEnvironment = nullptr;
  sGetPointerColor = nullptr;
  sAreLayersEnabled = nullptr;
  sSetDeviceType = nullptr;
  sHaltActivity = nullptr;
  sEnv = nullptr;
}

void
VRBrowser::DispatchCreateWidget(jint aWidgetHandle, jobject aSurface, jint aWidth, jint aHeight) {
  if (!ValidateMethodID(sEnv, sActivity, sDispatchCreateWidget, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(sActivity, sDispatchCreateWidget, aWidgetHandle, aSurface, aWidth, aHeight);
  CheckJNIException(sEnv, __FUNCTION__);
}


void
VRBrowser::DispatchCreateWidgetLayer(jint aWidgetHandle, jobject aSurface, jint aWidth, jint aHeight, const std::function<void()>& aFirstCompositeCallback) {
  if (!ValidateMethodID(sEnv, sActivity, sDispatchCreateWidgetLayer, __FUNCTION__)) { return; }
  jlong callback = 0;
  if (aFirstCompositeCallback) {
    callback = reinterpret_cast<jlong>(new std::function<void()>(aFirstCompositeCallback));
  }
  sEnv->CallVoidMethod(sActivity, sDispatchCreateWidgetLayer, aWidgetHandle, aSurface, aWidth, aHeight, callback);
  CheckJNIException(sEnv, __FUNCTION__);
}


void
VRBrowser::HandleMotionEvent(jint aWidgetHandle, jint aController, jboolean aFocused, jboolean aPressed, jfloat aX, jfloat aY) {
  if (!ValidateMethodID(sEnv, sActivity, sHandleMotionEvent, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(sActivity, sHandleMotionEvent, aWidgetHandle, aController, aFocused, aPressed, aX, aY);
  CheckJNIException(sEnv, __FUNCTION__);
}

void
VRBrowser::HandleScrollEvent(jint aWidgetHandle, jint aController, jfloat aX, jfloat aY) {
  if (!ValidateMethodID(sEnv, sActivity, sHandleScrollEvent, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(sActivity, sHandleScrollEvent, aWidgetHandle, aController, aX, aY);
  CheckJNIException(sEnv, __FUNCTION__);
}

void
VRBrowser::HandleAudioPose(jfloat qx, jfloat qy, jfloat qz, jfloat qw, jfloat px, jfloat py, jfloat pz) {
  if (!ValidateMethodID(sEnv, sActivity, sHandleAudioPose, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(sActivity, sHandleAudioPose, qx, qy, qz, qw, px, py, pz);
  CheckJNIException(sEnv, __FUNCTION__);
}

void
VRBrowser::HandleGesture(jint aType) {
  if (!ValidateMethodID(sEnv, sActivity, sHandleGesture, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(sActivity, sHandleGesture, aType);
  CheckJNIException(sEnv, __FUNCTION__);
}

void
VRBrowser::HandleResize(jint aWidgetHandle, jfloat aWorldWidth, jfloat aWorldHeight) {
  if (!ValidateMethodID(sEnv, sActivity, sHandleResize, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(sActivity, sHandleResize, aWidgetHandle, aWorldWidth, aWorldHeight);
  CheckJNIException(sEnv, __FUNCTION__);
}

void
VRBrowser::HandleMoveEnd(jint aWidgetHandle, jfloat aX, jfloat aY, jfloat aZ, jfloat aRotation) {
  if (!ValidateMethodID(sEnv, sActivity, sHandleMoveEnd, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(sActivity, sHandleMoveEnd, aWidgetHandle, aX, aY, aZ, aRotation);
  CheckJNIException(sEnv, __FUNCTION__);
}

void
VRBrowser::HandleBack() {
  if (!ValidateMethodID(sEnv, sActivity, sHandleBack, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(sActivity, sHandleBack);
  CheckJNIException(sEnv, __FUNCTION__);
}

void
VRBrowser::RegisterExternalContext(jlong aContext) {
  if (!ValidateMethodID(sEnv, sActivity, sRegisterExternalContext, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(sActivity, sRegisterExternalContext, aContext);
  CheckJNIException(sEnv, __FUNCTION__);
}

void
VRBrowser::PauseCompositor() {
  if (!ValidateMethodID(sEnv, sActivity, sPauseCompositor, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(sActivity, sPauseCompositor);
  CheckJNIException(sEnv, __FUNCTION__);
}

void
VRBrowser::ResumeCompositor() {
  if (!ValidateMethodID(sEnv, sActivity, sResumeCompositor, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(sActivity, sResumeCompositor);
  CheckJNIException(sEnv, __FUNCTION__);
}

void
VRBrowser::RenderPointerLayer(jobject aSurface, const std::function<void()>& aFirstCompositeCallback) {
  if (!ValidateMethodID(sEnv, sActivity, sRenderPointerLayer, __FUNCTION__)) { return; }
  jlong callback = 0;
  if (aFirstCompositeCallback) {
    callback = reinterpret_cast<jlong>(new std::function<void()>(aFirstCompositeCallback));
  }
  sEnv->CallVoidMethod(sActivity, sRenderPointerLayer, aSurface, callback);
  CheckJNIException(sEnv, __FUNCTION__);
}

std::string
VRBrowser::GetStorageAbsolutePath(const std::string& aRelativePath) {
  if (!ValidateMethodID(sEnv, sActivity, sGetStorageAbsolutePath, __FUNCTION__)) { return ""; }
  jstring jStr = (jstring) sEnv->CallObjectMethod(sActivity, sGetStorageAbsolutePath);
  CheckJNIException(sEnv, __FUNCTION__);
  if (!jStr) {
    return aRelativePath;
  }

  const char *cstr = sEnv->GetStringUTFChars(jStr, nullptr);
  std::string str = std::string(cstr);
  sEnv->ReleaseStringUTFChars(jStr, cstr);

  if (aRelativePath.empty()) {
    return str;
  } else {
    return str + "/" + aRelativePath;
  }
}

bool
VRBrowser::isOverrideEnvPathEnabled() {
  if (!ValidateMethodID(sEnv, sActivity, sIsOverrideEnvPathEnabled, __FUNCTION__)) { return false; }
  jboolean jBool = sEnv->CallBooleanMethod(sActivity, sIsOverrideEnvPathEnabled);
  CheckJNIException(sEnv, __FUNCTION__);

  return jBool;
}

std::string
VRBrowser::GetActiveEnvironment() {
  if (!ValidateMethodID(sEnv, sActivity, sGetActiveEnvironment, __FUNCTION__)) { return ""; }
  jstring jStr = (jstring) sEnv->CallObjectMethod(sActivity, sGetActiveEnvironment);
  CheckJNIException(sEnv, __FUNCTION__);
  if (!jStr) {
    return "cubemap/day";
  }

  const char *cstr = sEnv->GetStringUTFChars(jStr, nullptr);
  std::string str = std::string(cstr);
  sEnv->ReleaseStringUTFChars(jStr, cstr);

  return "cubemap/" + str;
}

int32_t
VRBrowser::GetPointerColor() {
  if (!ValidateMethodID(sEnv, sActivity, sGetPointerColor, __FUNCTION__)) { return 16777215; }
  jint jHexColor = (jint) sEnv->CallIntMethod(sActivity, sGetPointerColor);
  CheckJNIException(sEnv, __FUNCTION__);

  return (int32_t )jHexColor;
}

bool
VRBrowser::AreLayersEnabled() {
  if (!ValidateMethodID(sEnv, sActivity, sAreLayersEnabled, __FUNCTION__)) { return false; }
  jboolean enabled = sEnv->CallBooleanMethod(sActivity, sAreLayersEnabled);
  CheckJNIException(sEnv, __FUNCTION__);

  return enabled;
}

void
VRBrowser::SetDeviceType(const jint aType) {
  if (!ValidateMethodID(sEnv, sActivity, sSetDeviceType, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(sActivity, sSetDeviceType, aType);
  CheckJNIException(sEnv, __FUNCTION__);
}

void
VRBrowser::HaltActivity(const jint aReason) {
  if (!ValidateMethodID(sEnv, sActivity, sHaltActivity, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(sActivity, sHaltActivity, aReason);
  CheckJNIException(sEnv, __FUNCTION__);
}

void
VRBrowser::HandlePoorPerformance() {
  if (!ValidateMethodID(sEnv, sActivity, sHandlePoorPerformance, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(sActivity, sHandlePoorPerformance);
  CheckJNIException(sEnv, __FUNCTION__);
}

} // namespace crow
