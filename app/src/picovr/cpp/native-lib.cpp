/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include <jni.h>
#include <string>

#include "BrowserWorld.h"
#include "DeviceDelegatePicoVR.h"
#include "VRBrowserPico.h"
#include "vrb/GLError.h"
#include "vrb/Logger.h"
#include "vrb/RunnableQueue.h"

static vrb::RunnableQueuePtr sQueue;
static crow::DeviceDelegatePicoVRPtr sDevice;

using namespace crow;

namespace {
bool gDestroyed = false;
}

#define JNI_METHOD(return_type, method_name) \
  JNIEXPORT return_type JNICALL              \
    Java_org_mozilla_vrbrowser_PlatformActivity_##method_name

extern "C" {

JNI_METHOD(void, nativeOnCreate)
(JNIEnv* env, jobject) {
  gDestroyed = false;
}

JNI_METHOD(void, nativeInitialize)
(JNIEnv* aEnv, jobject aActivity, jint width, jint height, jobject aAssetManager, jint type, jint focusInex) {
  gDestroyed = false;
  sQueue->AttachToThread();
  VRBrowserPico::InitializeJava(aEnv, aActivity);
  if (!sDevice) {
    sDevice = crow::DeviceDelegatePicoVR::Create(BrowserWorld::Instance().GetRenderContext());
  }
  sDevice->SetType(type);
  sDevice->SetFocused(focusInex);
  sDevice->SetRenderSize(width, height);
  BrowserWorld::Instance().RegisterDeviceDelegate(sDevice);
  BrowserWorld::Instance().InitializeJava(aEnv, aActivity, aAssetManager);
  BrowserWorld::Instance().InitializeGL();
  BrowserWorld::Instance().Resume();
}

JNI_METHOD(void, nativeShutdown)
(JNIEnv*, jobject) {
  BrowserWorld::Instance().ShutdownGL();
}

JNI_METHOD(void, nativeDestroy)
(JNIEnv*, jobject) {
  gDestroyed = true;
  sQueue->Clear();
  BrowserWorld::Instance().ShutdownJava();
  BrowserWorld::Instance().RegisterDeviceDelegate(nullptr);
  sDevice = nullptr;
  BrowserWorld::Destroy();
  VRBrowserPico::ShutdownJava();
}

JNI_METHOD(void, nativePause)
(JNIEnv*, jobject) {
  if (gDestroyed) {
    return;
  }
  BrowserWorld::Instance().Pause();
  if (sDevice != nullptr)
    sDevice->Pause();
}

JNI_METHOD(void, nativeResume)
(JNIEnv*, jobject) {
  if (gDestroyed) {
    return;
  }
  BrowserWorld::Instance().Resume();
  if (sDevice != nullptr)
    sDevice->Resume();
}

JNI_METHOD(void, nativeStartFrame)
(JNIEnv*, jobject, jfloat ipd, jfloat fov, jfloat px, jfloat py, jfloat pz, jfloat qx, jfloat qy, jfloat qz, jfloat qw) {
  if (gDestroyed) {
    return;
  }
  sQueue->ProcessRunnables();
  sDevice->UpdateIpd(ipd);
  sDevice->UpdateFov(fov);
  sDevice->UpdatePosition(vrb::Vector(px, py, pz));
  sDevice->UpdateOrientation(vrb::Quaternion(qx, qy, qz, qw));
  BrowserWorld::Instance().StartFrame();
}

JNI_METHOD(void, nativeDrawEye)
(JNIEnv*, jobject, jint eye) {
  if (gDestroyed) {
    return;
  }
  VRB_GL_CHECK(glEnable(GL_BLEND));
  VRB_GL_CHECK(glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA));
  BrowserWorld::Instance().Draw(eye == 0 ? device::Eye::Left : device::Eye::Right);
}

JNI_METHOD(void, nativeEndFrame)
(JNIEnv*, jobject) {
  if (gDestroyed) {
    return;
  }
  BrowserWorld::Instance().EndFrame();
}

JNI_METHOD(void, nativeSetFocusedController)
(JNIEnv*, jobject, jint index) {
  if (gDestroyed) {
    return;
  }
  sDevice->SetFocused(index);
}

JNI_METHOD(void, nativeUpdateControllerPose)
(JNIEnv*, jobject, jint index, jboolean dof6, jfloat px, jfloat py, jfloat pz, jfloat qx, jfloat qy, jfloat qz, jfloat qw) {
  if (gDestroyed) {
    return;
  }
  sDevice->UpdateControllerPose(index, dof6, vrb::Vector(px, py, pz),
                                vrb::Quaternion(qx, qy, qz, qw));
}

JNI_METHOD(void, nativeUpdateControllerState)
(JNIEnv*, jobject, jint index, jboolean connected, jint buttons, jfloat grip, jfloat axisX, jfloat axisY, jboolean touched, jint batteryLevel) {
  if (gDestroyed) {
    return;
  }
  sDevice->UpdateControllerConnected(index, connected);
  sDevice->UpdateControllerButtons(index, buttons, grip, axisX, axisY, touched);
  sDevice->UpdateControllerBatteryLevel(index, batteryLevel);
}

JNI_METHOD(void, nativeRecenter)
(JNIEnv* env, jobject) {
  if (gDestroyed) {
    return;
  }
  sDevice->Recenter();
}

JNI_METHOD(void, queueRunnable)
(JNIEnv* aEnv, jobject, jobject aRunnable) {
  if (gDestroyed) {
    return;
  }
  sQueue->AddRunnable(aEnv, aRunnable);
}

jint JNI_OnLoad(JavaVM* aVM, void*) {
  sQueue = vrb::RunnableQueue::Create(aVM);
  return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM*, void*) {
  sDevice = nullptr;
  sQueue = nullptr;
}

} // extern "C"
