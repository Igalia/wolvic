/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include <jni.h>
#include <string>

#include "BrowserWorld.h"
#include "DeviceDelegateNoAPI.h"
#include "vrb/GLError.h"
#include "vrb/Logger.h"

static crow::DeviceDelegateNoAPIPtr sDevice;

using namespace crow;

#define JNI_METHOD(return_type, method_name) \
  JNIEXPORT return_type JNICALL              \
    Java_com_igalia_wolvic_PlatformActivity_##method_name

extern "C" {

JNI_METHOD(void, activityPaused)
(JNIEnv*, jobject) {
  if (sDevice) {
    sDevice->Pause();
  }
  BrowserWorld::Instance().Pause();
  BrowserWorld::Instance().ShutdownGL();
}

JNI_METHOD(void, activityResumed)
(JNIEnv*, jobject) {
  if (sDevice) {
    sDevice->Resume();
  }
  BrowserWorld::Instance().InitializeGL();
  BrowserWorld::Instance().Resume();
}

JNI_METHOD(void, activityCreated)
(JNIEnv* aEnv, jobject aActivity, jobject aAssetManager) {
  if (!sDevice) {
    sDevice = crow::DeviceDelegateNoAPI::Create(BrowserWorld::Instance().GetRenderContext());
  }
  sDevice->Resume();
  sDevice->InitializeJava(aEnv, aActivity);
  BrowserWorld::Instance().RegisterDeviceDelegate(sDevice);
  BrowserWorld::Instance().InitializeJava(aEnv, aActivity, aAssetManager);
  BrowserWorld::Instance().InitializeGL();
}

JNI_METHOD(void, updateViewport)
(JNIEnv*, jobject, jint aWidth, jint aHeight) {
  if (sDevice) {
    sDevice->SetViewport(aWidth, aHeight);
  } else {
    VRB_LOG("FAILED TO SET VIEWPORT");
  }
}

JNI_METHOD(void, activityDestroyed)
(JNIEnv*, jobject) {
  BrowserWorld::Instance().ShutdownJava();
  BrowserWorld::Instance().RegisterDeviceDelegate(nullptr);
  BrowserWorld::Destroy();
  if (sDevice) {
    sDevice->ShutdownJava();
    sDevice = nullptr;
  }
}

JNI_METHOD(void, drawGL)
(JNIEnv*, jobject) {
  BrowserWorld::Instance().Draw();
}

JNI_METHOD(void, moveAxis)
(JNIEnv*, jobject, jfloat aX, jfloat aY, jfloat aZ) {
  sDevice->MoveAxis(aX, aY, aZ);
}

JNI_METHOD(void, rotateHeading)
(JNIEnv*, jobject, jfloat aHeading) {
  sDevice->RotateHeading(aHeading);
}

JNI_METHOD(void, rotatePitch)
(JNIEnv*, jobject, jfloat aPitch) {
  sDevice->RotatePitch(aPitch);
}

JNI_METHOD(void, touchEvent)
(JNIEnv*, jobject, jboolean aDown, jfloat aX, jfloat aY) {
  sDevice->TouchEvent(aDown, aX, aY);
}

JNI_METHOD(void, controllerButtonPressed)
(JNIEnv*, jobject, jboolean aDown) {
  sDevice->ControllerButtonPressed(aDown);
}

jint JNI_OnLoad(JavaVM*, void*) {
  return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM*, void*) {
  sDevice = nullptr;
}

} // extern "C"
