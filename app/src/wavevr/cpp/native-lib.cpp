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

using namespace crow;

static bool sJavaInitialized = false;
static vrb::RunnableQueuePtr sQueue;
static DeviceDelegateWaveVRPtr sDevice;

#define JNI_METHOD(return_type, method_name) \
  JNIEXPORT return_type JNICALL              \
    Java_org_mozilla_vrbrowser_PlatformActivity_##method_name

extern "C" {

JNI_METHOD(void, activityPaused)
(JNIEnv*, jobject) {
  BrowserWorld::Instance().Pause();
}

JNI_METHOD(void, activityResumed)
(JNIEnv*, jobject) {
  BrowserWorld::Instance().Resume();
}

int main(int argc, char *argv[]) {
  sQueue->AttachToThread();
  VRB_LOG("Call WVR_Init");

  WVR_InitError eError = WVR_Init(WVR_AppType_VRContent);
  if (eError != WVR_InitError_None) {
    VRB_LOG("Unable to init VR runtime: %s", WVR_GetInitErrorString(eError));
    return 1;
  }

  sDevice = DeviceDelegateWaveVR::Create(BrowserWorld::Instance().GetRenderContext());
  BrowserWorld::Instance().RegisterDeviceDelegate(sDevice);
  while(!sJavaInitialized) {
    sQueue->ProcessRunnables();
  }

  WVR_InputAttribute inputIdAndTypes[] = {
      {WVR_InputId_Alias1_Menu, WVR_InputType_Button, WVR_AnalogType_None},
      {WVR_InputId_Alias1_Touchpad, WVR_InputType_Button | WVR_InputType_Touch | WVR_InputType_Analog, WVR_AnalogType_2D},
      {WVR_InputId_Alias1_Trigger, WVR_InputType_Button , WVR_AnalogType_None},
      {WVR_InputId_Alias1_Digital_Trigger, WVR_InputType_Button , WVR_AnalogType_None},
      {WVR_InputId_Alias1_Grip, WVR_InputType_Button , WVR_AnalogType_None}
  };
  WVR_SetInputRequest(WVR_DeviceType_HMD, inputIdAndTypes, sizeof(inputIdAndTypes) / sizeof(*inputIdAndTypes));
  WVR_SetInputRequest(WVR_DeviceType_Controller_Right, inputIdAndTypes, sizeof(inputIdAndTypes) / sizeof(*inputIdAndTypes));
  WVR_SetInputRequest(WVR_DeviceType_Controller_Left, inputIdAndTypes, sizeof(inputIdAndTypes) / sizeof(*inputIdAndTypes));

  // Must initialize render runtime before all OpenGL code.
  WVR_RenderInitParams_t param;
  param = { WVR_GraphicsApiType_OpenGL, WVR_RenderConfig_Default };

  WVR_RenderError pError = WVR_RenderInit(&param);
  if (pError != WVR_RenderError_None) {
    VRB_LOG("Present init failed - Error[%d]", pError);
  }
  sDevice->InitializeRender();
  VRB_GL_CHECK(glEnable(GL_DEPTH_TEST));
  VRB_GL_CHECK(glEnable(GL_CULL_FACE));
  VRB_GL_CHECK(glEnable(GL_BLEND));
  // VRB_GL_CHECK(glDisable(GL_CULL_FACE));
  VRB_LOG("Java Initialized.");
  BrowserWorld::Instance().InitializeGL();
  BrowserWorld::Instance().Resume();
  while (sDevice->IsRunning()) {
    sQueue->ProcessRunnables();
    //VRB_LOG("About to DRAW!");
    VRB_GL_CHECK(glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT));
    BrowserWorld::Instance().Draw();
  }
  BrowserWorld::Instance().RegisterDeviceDelegate(nullptr);
  sDevice = nullptr;
  BrowserWorld::Instance().ShutdownGL();
  BrowserWorld::Instance().ShutdownJava();
  sQueue->Clear();
  sJavaInitialized = false;
  BrowserWorld::Destroy();
  WVR_Quit();
  return 0;
}

JNI_METHOD(void, queueRunnable)
(JNIEnv* aEnv, jobject, jobject aRunnable) {
  sQueue->AddRunnable(aEnv, aRunnable);
}

JNI_METHOD(void, initializeJava)
(JNIEnv* aEnv, jobject aActivity, jobject aAssets) {
  sJavaInitialized = true;
  BrowserWorld::Instance().InitializeJava(aEnv, aActivity, aAssets);
}

jint JNI_OnLoad(JavaVM* aVm, void*) {
  sQueue = vrb::RunnableQueue::Create(aVm);
  WVR_RegisterMain(main);
  return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM* vm, void* reserved) {
  sQueue = nullptr;
}

} // extern "C"
