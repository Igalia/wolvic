/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include <jni.h>
#include <string>
#include <GLES3/gl3.h>
#include <wvr/wvr.h>
#include <wvr/wvr_render.h>
#include <wvr/wvr_device.h>
#include <wvr/wvr_projection.h>
#include <wvr/wvr_overlay.h>
#include <wvr/wvr_system.h>
#include <wvr/wvr_events.h>

#include "BrowserWorld.h"
#include "DeviceDelegateWaveVR.h"
#include "vrb/Logger.h"
#include "vrb/GLError.h"
#include "vrb/RunnableQueue.h"

static vrb::RunnableQueuePtr sQueue;
static BrowserWorldPtr sWorld;
static DeviceDelegateWaveVRPtr sDevice;

#define JNI_METHOD(return_type, method_name) \
  JNIEXPORT return_type JNICALL              \
    Java_org_mozilla_vrbrowser_VRBrowserActivity_##method_name

extern "C" {

JNI_METHOD(void, activityPaused)
(JNIEnv*, jobject) {
  sWorld->Pause();
}

JNI_METHOD(void, activityResumed)
(JNIEnv*, jobject) {
  sWorld->Resume();
}

int main(int argc, char *argv[]) {

  bool quit = false;
  VRB_LOG("Call WVR_Init");
  WVR_InitError eError = WVR_Init(WVR_AppType_VRContent);
  if (eError != WVR_InitError_None) {
    VRB_LOG("Unable to init VR runtime: %s", WVR_GetInitErrorString(eError));
    return 1;
  }

  // Must initialize render runtime before all OpenGL code.
  WVR_RenderInitParams_t param;
  param = { WVR_GraphicsApiType_OpenGL, WVR_RenderConfig_Timewarp_Asynchronous };

  WVR_RenderError pError = WVR_RenderInit(&param);
  if (pError != WVR_RenderError_None) {
    VRB_LOG("Present init failed - Error[%d]", pError);
  }
  sDevice = DeviceDelegateWaveVR::Create(sWorld->GetWeakContext());
  sWorld->RegisterDeviceDelegate(sDevice);
  VRB_CHECK(glClearColor(0.0, 0.0, 0.0, 1.0));
  VRB_CHECK(glEnable(GL_DEPTH_TEST));
  VRB_CHECK(glEnable(GL_CULL_FACE));
  // VRB_CHECK(glDisable(GL_CULL_FACE));
  sWorld->InitializeGL();
  while (!quit) {
    sQueue->ProcessRunnables();
    VRB_CHECK(glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT));
    sWorld->Draw();
  }
  sWorld->ShutdownGL();
  return 0;
}

JNI_METHOD(void, queueRunnable)
(JNIEnv* aEnv, jobject, jobject aRunnable) {
  sQueue->AddRunnable(aEnv, aRunnable);
}

JNI_METHOD(void, initializeJava)
(JNIEnv* aEnv, jobject aActivity, jobject aAssets) {
  sWorld->InitializeJava(aEnv, aActivity, aAssets);
  WVR_RegisterMain(main);
}

jint JNI_OnLoad(JavaVM* aVm, void*) {
  sQueue = vrb::RunnableQueue::Create(aVm);
  sWorld = BrowserWorld::Create();
  return JNI_VERSION_1_6;
}

jint JNI_OnUnLoad(JavaVM* vm, void* reserved) {
  sWorld->ShutdownJava();
  sQueue = nullptr;
  sWorld = nullptr;
}

} // extern "C"