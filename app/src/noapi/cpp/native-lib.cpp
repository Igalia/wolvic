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

static crow::BrowserWorldPtr sWorld;
static crow::DeviceDelegateNoAPIPtr sDevice;

#define JNI_METHOD(return_type, method_name) \
  JNIEXPORT return_type JNICALL              \
    Java_org_mozilla_vrbrowser_PlatformActivity_##method_name

extern "C" {

JNI_METHOD(void, activityPaused)
(JNIEnv*, jobject) {
  if (sDevice) {
    sDevice->Pause();
  }
  sWorld->Pause();
  sWorld->ShutdownGL();
}

JNI_METHOD(void, activityResumed)
(JNIEnv*, jobject) {
  if (sDevice) {
    sDevice->Resume();
  }
  sWorld->InitializeGL();
  sWorld->Resume();
}

JNI_METHOD(void, activityCreated)
(JNIEnv* aEnv, jobject aActivity, jobject aAssetManager) {
  if (!sDevice) {
    sDevice = crow::DeviceDelegateNoAPI::Create(sWorld->GetWeakContext());
  }
  sDevice->Resume();
  sWorld->RegisterDeviceDelegate(sDevice);
  sWorld->InitializeJava(aEnv, aActivity, aAssetManager);
  sWorld->InitializeGL();
}

JNI_METHOD(void, updateViewport)
(JNIEnv*, jobject, int aWidth, int aHeight) {
  if (sDevice) {
    sDevice->SetViewport(aWidth, aHeight);
  } else {
    VRB_LOG("FAILED TO SET VIEWPORT");
  }
}

JNI_METHOD(void, activityDestroyed)
(JNIEnv*, jobject) {
  sWorld->ShutdownJava();
  sWorld->RegisterDeviceDelegate(nullptr);
  sDevice = nullptr;
}

JNI_METHOD(void, drawGL)
(JNIEnv*, jobject) {
  sWorld->Draw();
}

JNI_METHOD(void, moveAxis)
(JNIEnv*, jobject, float aX, float aY, float aZ) {
  sDevice->MoveAxis(aX, aY, aZ);
}

JNI_METHOD(void, touchEvent)
(JNIEnv*, jobject, bool aDown, float aX, float aY) {
  sDevice->TouchEvent(aDown, aX, aY);
}

jint JNI_OnLoad(JavaVM* aVm, void*) {
  sWorld = crow::BrowserWorld::Create();
  return JNI_VERSION_1_6;
}

void JNI_OnUnLoad(JavaVM* vm, void* reserved) {
  sWorld = nullptr;
  sDevice = nullptr;
}

} // extern "C"
