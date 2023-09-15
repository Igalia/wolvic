/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include <jni.h>
#include <string>

#include "BrowserWorld.h"
#include "DeviceDelegateNoAPI.h"
#include "VRBrowser.h"
#include "vrb/GLError.h"
#include "vrb/Logger.h"
#include "JNIUtil.h"

using namespace crow;

#define JNI_METHOD(return_type, method_name) \
  JNIEXPORT return_type JNICALL              \
    Java_com_igalia_wolvic_PlatformActivity_##method_name

namespace {
struct AppContext {
    crow::DeviceDelegateNoAPIPtr mDevice;
    JavaContext mJavaContext;
};
typedef std::shared_ptr<AppContext> AppContextPtr;

AppContextPtr sAppContext;
}

extern "C" {

JNI_METHOD(void, activityPaused)
(JNIEnv*, jobject) {
  if (sAppContext->mDevice) {
    sAppContext->mDevice->Pause();
  }
  BrowserWorld::Instance().Pause();
  BrowserWorld::Instance().ShutdownGL();
}

JNI_METHOD(void, activityResumed)
(JNIEnv*, jobject) {
  if (sAppContext->mDevice) {
    sAppContext->mDevice->Resume();
  }
  BrowserWorld::Instance().InitializeGL();
  BrowserWorld::Instance().Resume();
}

JNI_METHOD(void, activityCreated)
(JNIEnv* aEnv, jobject aActivity, jobject aAssetManager) {
  sAppContext->mJavaContext.activity = aEnv->NewGlobalRef(aActivity);
  sAppContext->mJavaContext.env = aEnv;
  sAppContext->mJavaContext.vm->AttachCurrentThread(&sAppContext->mJavaContext.env, nullptr);

  crow::VRBrowser::InitializeJava(aEnv, aActivity);

  sAppContext->mDevice = crow::DeviceDelegateNoAPI::Create(BrowserWorld::Instance().GetRenderContext());
  sAppContext->mDevice->Resume();
  sAppContext->mDevice->InitializeJava(aEnv, aActivity);

  BrowserWorld::Instance().RegisterDeviceDelegate(sAppContext->mDevice);
  BrowserWorld::Instance().InitializeJava(aEnv, aActivity, aAssetManager);
  BrowserWorld::Instance().InitializeGL();
}

JNI_METHOD(void, updateViewport)
(JNIEnv*, jobject, jint aWidth, jint aHeight) {
  if (sAppContext->mDevice) {
    sAppContext->mDevice->SetViewport(aWidth, aHeight);
  } else {
    VRB_LOG("FAILED TO SET VIEWPORT");
  }
}

JNI_METHOD(void, activityDestroyed)
(JNIEnv*, jobject) {
  BrowserWorld::Instance().ShutdownJava();
  BrowserWorld::Instance().RegisterDeviceDelegate(nullptr);
  BrowserWorld::Destroy();
  if (sAppContext->mDevice) {
    sAppContext->mDevice->ShutdownJava();
    sAppContext->mDevice = nullptr;
  }
}

JNI_METHOD(void, drawGL)
(JNIEnv*, jobject) {
  BrowserWorld::Instance().Draw();
}

JNI_METHOD(void, moveAxis)
(JNIEnv*, jobject, jfloat aX, jfloat aY, jfloat aZ) {
  sAppContext->mDevice->MoveAxis(aX, aY, aZ);
}

JNI_METHOD(void, rotateHeading)
(JNIEnv*, jobject, jfloat aHeading) {
  sAppContext->mDevice->RotateHeading(aHeading);
}

JNI_METHOD(void, rotatePitch)
(JNIEnv*, jobject, jfloat aPitch) {
  sAppContext->mDevice->RotatePitch(aPitch);
}

JNI_METHOD(void, touchEvent)
(JNIEnv*, jobject, jboolean aDown, jfloat aX, jfloat aY) {
  sAppContext->mDevice->TouchEvent(aDown, aX, aY);
}

JNI_METHOD(void, controllerButtonPressed)
(JNIEnv*, jobject, jboolean aDown) {
  sAppContext->mDevice->ControllerButtonPressed(aDown);
}

jint JNI_OnLoad(JavaVM* aVm, void*) {
  if (sAppContext) {
    return JNI_VERSION_1_6;
  }
  sAppContext = std::make_shared<AppContext>();
  sAppContext->mJavaContext.vm = aVm;
  return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM*, void*) {
  sAppContext.reset();
}

} // extern "C"
