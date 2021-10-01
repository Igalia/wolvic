/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "VRBrowser.h"
#include "vrb/ConcreteClass.h"
#include "vrb/Logger.h"
#include "JNIUtil.h"

namespace {

const char* const kDispatchCreateWidgetName = "dispatchCreateWidget";
const char* const kDispatchCreateWidgetSignature = "(ILandroid/graphics/SurfaceTexture;II)V";
const char* const kDispatchCreateWidgetLayerName = "dispatchCreateWidgetLayer";
const char* const kDispatchCreateWidgetLayerSignature = "(ILandroid/view/Surface;IIJ)V";
const char* const kHandleMotionEventName = "handleMotionEvent";
const char* const kHandleMotionEventSignature = "(IIZZFF)V";
const char* const kHandleScrollEventName = "handleScrollEvent";
const char* const kHandleScrollEventSignature = "(IIFF)V";
const char* const kHandleAudioPoseName = "handleAudioPose";
const char* const kHandleAudioPoseSignature = "(FFFFFFF)V";
const char* const kHandleGestureName = "handleGesture";
const char* const kHandleGestureSignature = "(I)V";
const char* const kHandleResizeName = "handleResize";
const char* const kHandleResizeSignature = "(IFF)V";
const char* const kHandleMoveEndName = "handleMoveEnd";
const char* const kHandleMoveEndSignature = "(IFFFF)V";
const char* const kHandleBackEventName = "handleBack";
const char* const kHandleBackEventSignature = "()V";
const char* const kRegisterExternalContextName = "registerExternalContext";
const char* const kRegisterExternalContextSignature = "(J)V";
const char* const kOnEnterWebXRName = "onEnterWebXR";
const char* const kOnEnterWebXRSignature = "()V";
const char* const kOnExitWebXRName = "onExitWebXR";
const char* const kOnExitWebXRSignature = "(J)V";
const char* const kOnDismissWebXRInterstitialName = "onDismissWebXRInterstitial";
const char* const kOnDismissWebXRInterstitialSignature = "()V";
const char* const kOnWebXRRenderStateChangeName = "onWebXRRenderStateChange";
const char* const kOnWebXRRenderStateChangeSignature = "(Z)V";
const char* const kRenderPointerLayerName = "renderPointerLayer";
const char* const kRenderPointerLayerSignature = "(Landroid/view/Surface;J)V";
const char* const kGetStorageAbsolutePathName = "getStorageAbsolutePath";
const char* const kGetStorageAbsolutePathSignature = "()Ljava/lang/String;";
const char* const kIsOverrideEnvPathEnabledName = "isOverrideEnvPathEnabled";
const char* const kIsOverrideEnvPathEnabledSignature = "()Z";
const char* const kGetActiveEnvironment = "getActiveEnvironment";
const char* const kGetActiveEnvironmentSignature = "()Ljava/lang/String;";
const char* const kGetPointerColor = "getPointerColor";
const char* const kGetPointerColorSignature = "()I";
const char* const kAreLayersEnabled = "areLayersEnabled";
const char* const kAreLayersEnabledSignature = "()Z";
const char* const kSetDeviceType = "setDeviceType";
const char* const kSetDeviceTypeSignature = "(I)V";
const char* const kHaltActivity = "haltActivity";
const char* const kHaltActivitySignature = "(I)V";
const char* const kHandlePoorPerformance = "handlePoorPerformance";
const char* const kHandlePoorPerformanceSignature = "()V";
const char* const kOnAppLink = "onAppLink";
const char* const kOnAppLinkSignature = "(Ljava/lang/String;)V";
const char* const kDisableLayers = "disableLayers";
const char* const kDisableLayersSignature = "()V";
const char* const kAppendAppNotesToCrashReport = "appendAppNotesToCrashReport";
const char* const kAppendAppNotesToCrashReportSignature = "(Ljava/lang/String;)V";
const char* const kUpdateControllerBatteryLevelsName = "updateControllerBatteryLevels";
const char* const kUpdateControllerBatteryLevelsSignature = "(II)V";

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
jmethodID sOnEnterWebXR = nullptr;
jmethodID sOnExitWebXR = nullptr;
jmethodID sOnDismissWebXRInterstitial = nullptr;
jmethodID sOnWebXRRenderStateChange = nullptr;
jmethodID sRenderPointerLayer = nullptr;
jmethodID sGetStorageAbsolutePath = nullptr;
jmethodID sIsOverrideEnvPathEnabled = nullptr;
jmethodID sGetActiveEnvironment = nullptr;
jmethodID sGetPointerColor = nullptr;
jmethodID sAreLayersEnabled = nullptr;
jmethodID sSetDeviceType = nullptr;
jmethodID sHaltActivity = nullptr;
jmethodID sHandlePoorPerformance = nullptr;
jmethodID sOnAppLink = nullptr;
jmethodID sDisableLayers = nullptr;
jmethodID sAppendAppNotesToCrashReport = nullptr;
jmethodID sUpdateControllerBatteryLevels = nullptr;
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
  sOnEnterWebXR = FindJNIMethodID(sEnv, sBrowserClass, kOnEnterWebXRName, kOnEnterWebXRSignature);
  sOnExitWebXR = FindJNIMethodID(sEnv, sBrowserClass, kOnExitWebXRName, kOnExitWebXRSignature);
  sOnDismissWebXRInterstitial = FindJNIMethodID(sEnv, sBrowserClass, kOnDismissWebXRInterstitialName, kOnDismissWebXRInterstitialSignature);
  sOnWebXRRenderStateChange = FindJNIMethodID(sEnv, sBrowserClass, kOnWebXRRenderStateChangeName, kOnWebXRRenderStateChangeSignature);
  sRenderPointerLayer = FindJNIMethodID(sEnv, sBrowserClass, kRenderPointerLayerName, kRenderPointerLayerSignature);
  sGetStorageAbsolutePath = FindJNIMethodID(sEnv, sBrowserClass, kGetStorageAbsolutePathName, kGetStorageAbsolutePathSignature);
  sIsOverrideEnvPathEnabled = FindJNIMethodID(sEnv, sBrowserClass, kIsOverrideEnvPathEnabledName, kIsOverrideEnvPathEnabledSignature);
  sGetActiveEnvironment = FindJNIMethodID(sEnv, sBrowserClass, kGetActiveEnvironment, kGetActiveEnvironmentSignature);
  sGetPointerColor = FindJNIMethodID(sEnv, sBrowserClass, kGetPointerColor, kGetPointerColorSignature);
  sAreLayersEnabled = FindJNIMethodID(sEnv, sBrowserClass, kAreLayersEnabled, kAreLayersEnabledSignature);
  sSetDeviceType = FindJNIMethodID(sEnv, sBrowserClass, kSetDeviceType, kSetDeviceTypeSignature);
  sHaltActivity = FindJNIMethodID(sEnv, sBrowserClass, kHaltActivity, kHaltActivitySignature);
  sHandlePoorPerformance = FindJNIMethodID(sEnv, sBrowserClass, kHandlePoorPerformance, kHandlePoorPerformanceSignature);
  sOnAppLink = FindJNIMethodID(sEnv, sBrowserClass, kOnAppLink, kOnAppLinkSignature);
  sDisableLayers = FindJNIMethodID(sEnv, sBrowserClass, kDisableLayers, kDisableLayersSignature);
  sAppendAppNotesToCrashReport = FindJNIMethodID(sEnv, sBrowserClass, kAppendAppNotesToCrashReport, kAppendAppNotesToCrashReportSignature);
  sUpdateControllerBatteryLevels = FindJNIMethodID(sEnv, sBrowserClass, kUpdateControllerBatteryLevelsName, kUpdateControllerBatteryLevelsSignature);
}

JNIEnv * VRBrowser::Env()
{
  return sEnv;
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
  sOnEnterWebXR = nullptr;
  sOnExitWebXR = nullptr;
  sOnDismissWebXRInterstitial = nullptr;
  sOnWebXRRenderStateChange = nullptr;
  sRenderPointerLayer = nullptr;
  sGetStorageAbsolutePath = nullptr;
  sIsOverrideEnvPathEnabled = nullptr;
  sGetActiveEnvironment = nullptr;
  sGetPointerColor = nullptr;
  sAreLayersEnabled = nullptr;
  sSetDeviceType = nullptr;
  sHaltActivity = nullptr;
  sOnAppLink = nullptr;
  sDisableLayers = nullptr;
  sEnv = nullptr;
  sAppendAppNotesToCrashReport = nullptr;
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
VRBrowser::OnEnterWebXR() {
  if (!ValidateMethodID(sEnv, sActivity, sOnEnterWebXR, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(sActivity, sOnEnterWebXR);
  CheckJNIException(sEnv, __FUNCTION__);
}

void
VRBrowser::OnExitWebXR(const std::function<void()>& aCallback) {
  if (!ValidateMethodID(sEnv, sActivity, sOnExitWebXR, __FUNCTION__)) { return; }
  jlong callback = 0;
  if (aCallback) {
    callback = reinterpret_cast<jlong>(new std::function<void()>(aCallback));
  }
  sEnv->CallVoidMethod(sActivity, sOnExitWebXR, callback);
  CheckJNIException(sEnv, __FUNCTION__);
}

void VRBrowser::OnDismissWebXRInterstitial() {
  if (!ValidateMethodID(sEnv, sActivity, sOnDismissWebXRInterstitial, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(sActivity, sOnDismissWebXRInterstitial);
  CheckJNIException(sEnv, __FUNCTION__);
}

void VRBrowser::OnWebXRRenderStateChange(const bool aRendering) {
  if (!ValidateMethodID(sEnv, sActivity, sOnWebXRRenderStateChange, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(sActivity, sOnWebXRRenderStateChange, (jboolean) aRendering);
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

  return str;
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

void
VRBrowser::OnAppLink(const std::string& aJSON) {
  if (!ValidateMethodID(sEnv, sActivity, sOnAppLink, __FUNCTION__)) { return; }
  jstring json = sEnv->NewStringUTF(aJSON.c_str());
  sEnv->CallVoidMethod(sActivity, sOnAppLink, json);
  sEnv->DeleteLocalRef(json);
  CheckJNIException(sEnv, __FUNCTION__);
}

void
VRBrowser::DisableLayers() {
  if (!ValidateMethodID(sEnv, sActivity, sDisableLayers, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(sActivity, sDisableLayers);
  CheckJNIException(sEnv, __FUNCTION__);
}

void
VRBrowser::AppendAppNotesToCrashLog(const std::string& aNotes) {
  if (!ValidateMethodID(sEnv, sActivity, sAppendAppNotesToCrashReport, __FUNCTION__)) { return; }
  jstring notes = sEnv->NewStringUTF(aNotes.c_str());
  sEnv->CallVoidMethod(sActivity, sAppendAppNotesToCrashReport, notes);
  sEnv->DeleteLocalRef(notes);
  CheckJNIException(sEnv, __FUNCTION__);
}

void
VRBrowser::UpdateControllerBatteryLevels(const jint aLeftBatteryLevel, const jint aRightBatteryLevel) {
  if (!ValidateMethodID(sEnv, sActivity, sUpdateControllerBatteryLevels, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(sActivity, sUpdateControllerBatteryLevels, aLeftBatteryLevel, aRightBatteryLevel);
  CheckJNIException(sEnv, __FUNCTION__);
}


} // namespace crow
