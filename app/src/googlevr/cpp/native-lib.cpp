/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include <jni.h>
#include <string>

#include "BrowserWorld.h"
#include "DeviceDelegateGoogleVR.h"

static crow::DeviceDelegateGoogleVRPtr sDevice;

using namespace crow;

#define JNI_METHOD(return_type, method_name) \
  JNIEXPORT return_type JNICALL              \
    Java_org_mozilla_vrbrowser_PlatformActivity_##method_name

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
(JNIEnv* aEnv, jobject aActivity, jobject aAssetManager, jlong aGVRContext) {
  if (!sDevice) {
    sDevice = crow::DeviceDelegateGoogleVR::Create(BrowserWorld::Instance().GetRenderContext(), (void*) aGVRContext);
  }
  sDevice->InitializeGL();
  sDevice->Resume();
  BrowserWorld::Instance().RegisterDeviceDelegate(sDevice);
  BrowserWorld::Instance().InitializeJava(aEnv, aActivity, aAssetManager);
  BrowserWorld::Instance().InitializeGL();
}

JNI_METHOD(void, activityDestroyed)
(JNIEnv*, jobject) {
  BrowserWorld::Instance().ShutdownJava();
  BrowserWorld::Instance().RegisterDeviceDelegate(nullptr);
  BrowserWorld::Destroy();
  sDevice = nullptr;
}

JNI_METHOD(void, drawGL)
(JNIEnv*, jobject) {
  BrowserWorld::Instance().Draw();
}

jint JNI_OnLoad(JavaVM*, void*) {
  return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM*, void*) {
  sDevice = nullptr;
}

} // extern "C"
