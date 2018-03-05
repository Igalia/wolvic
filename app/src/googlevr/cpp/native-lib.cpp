/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include <jni.h>
#include <string>
#include <GLES2/gl2.h>

#include "BrowserWorld.h"
#include "DeviceDelegateGoogleVR.h"
#include "vrb/GLError.h"

static crow::BrowserWorldPtr sWorld;
static crow::DeviceDelegateGoogleVRPtr sDevice;

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
    //sDevice->InitializeGL();
    sDevice->Resume();
  }
  sWorld->InitializeGL();
  sWorld->Resume();
}

JNI_METHOD(void, activityCreated)
(JNIEnv* aEnv, jobject aActivity, jobject aAssetManager, int64_t aGVRContext) {
  if (!sDevice) {
    sDevice = crow::DeviceDelegateGoogleVR::Create(sWorld->GetWeakContext(), (void*) aGVRContext);
  }
  sDevice->InitializeGL();
  sDevice->Resume();
  sWorld->RegisterDeviceDelegate(sDevice);
  sWorld->InitializeJava(aEnv, aActivity, aAssetManager);
  sWorld->InitializeGL();
}

JNI_METHOD(void, activityDestroyed)
(JNIEnv* env, jobject) {
  sWorld->ShutdownJava();
  sWorld->RegisterDeviceDelegate(nullptr);
  sDevice = nullptr;
}

JNI_METHOD(void, drawGL)
(JNIEnv*, jobject) {
  sWorld->Draw();
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
