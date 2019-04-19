/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "VRBrowser.h"
#include "vrb/ConcreteClass.h"
#include "vrb/Logger.h"
#include "JNIUtil.h"

namespace {

static const char* kDispatchCreateWidgetName = "dispatchCreateWidget";
static const char* kDispatchCreateWidgetSignature = "(ILandroid/graphics/SurfaceTexture;II)V";
static const char* kDispatchCreateWidgetLayerName = "dispatchCreateWidgetLayer";
static const char* kDispatchCreateWidgetLayerSignature = "(ILandroid/view/Surface;IIJ)V";
static const char* kHandleMotionEventName = "handleMotionEvent";
static const char* kHandleMotionEventSignature = "(IIZFF)V";
static const char* kHandleScrollEventName = "handleScrollEvent";
static const char* kHandleScrollEventSignature = "(IIFF)V";
static const char* kHandleAudioPoseName = "handleAudioPose";
static const char* kHandleAudioPoseSignature = "(FFFFFFF)V";
static const char* kHandleGestureName = "handleGesture";
static const char* kHandleGestureSignature = "(I)V";
static const char* kHandleResizeName = "handleResize";
static const char* kHandleResizeSignature = "(IFF)V";
static const char* kHandleBackEventName = "handleBack";
static const char* kHandleBackEventSignature = "()V";
static const char* kRegisterExternalContextName = "registerExternalContext";
static const char* kRegisterExternalContextSignature = "(J)V";
static const char* kPauseCompositorName = "pauseGeckoViewCompositor";
static const char* kPauseCompositorSignature = "()V";
static const char* kResumeCompositorName = "resumeGeckoViewCompositor";
static const char* kResumeCompositorSignature = "()V";
static const char* kRenderPointerLayerName = "renderPointerLayer";
static const char* kRenderPointerLayerSignature = "(Landroid/view/Surface;J)V";
static const char* kGetStorageAbsolutePathName = "getStorageAbsolutePath";
static const char* kGetStorageAbsolutePathSignature = "()Ljava/lang/String;";
static const char* kIsOverrideEnvPathEnabledName = "isOverrideEnvPathEnabled";
static const char* kIsOverrideEnvPathEnabledSignature = "()Z";
static const char* kGetActiveEnvironment = "getActiveEnvironment";
static const char* kGetActiveEnvironmentSignature = "()Ljava/lang/String;";
static const char* kGetPointerColor = "getPointerColor";
static const char* kGetPointerColorSignature = "()I";
static const char* kAreLayersEnabled = "areLayersEnabled";
static const char* kAreLayersEnabledSignature = "()Z";
static const char* kSetDeviceType = "setDeviceType";
static const char* kSetDeviceTypeSignature = "(I)V";

static JNIEnv* sEnv;
static jclass sBrowserClass;
static jobject sActivity;
static jmethodID sDispatchCreateWidget;
static jmethodID sDispatchCreateWidgetLayer;
static jmethodID sHandleMotionEvent;
static jmethodID sHandleScrollEvent;
static jmethodID sHandleAudioPose;
static jmethodID sHandleGesture;
static jmethodID sHandleResize;
static jmethodID sHandleBack;
static jmethodID sRegisterExternalContext;
static jmethodID sPauseCompositor;
static jmethodID sResumeCompositor;
static jmethodID sRenderPointerLayer;
static jmethodID sGetStorageAbsolutePath;
static jmethodID sIsOverrideEnvPathEnabled;
static jmethodID sGetActiveEnvironment;
static jmethodID sGetPointerColor;
static jmethodID sAreLayersEnabled;
static jmethodID sSetDeviceType;
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
VRBrowser::HandleMotionEvent(jint aWidgetHandle, jint aController, jboolean aPressed, jfloat aX, jfloat aY) {
  if (!ValidateMethodID(sEnv, sActivity, sHandleMotionEvent, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(sActivity, sHandleMotionEvent, aWidgetHandle, aController, aPressed, aX, aY);
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

} // namespace crow
