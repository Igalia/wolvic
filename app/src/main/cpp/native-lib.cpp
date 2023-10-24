/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include <jni.h>
#include <string>
#include <GLES2/gl2.h>

#include "BrowserWorld.h"
#include "vrb/Logger.h"
#include "vrb/GLError.h"
#include "BrowserEGLContext.h"
#include <android_native_app_glue.h>
#include <cstdlib>
#include <vrb/RunnableQueue.h>
#if defined(OPENXR)
#include "DeviceDelegateOpenXR.h"
#elif defined(OCULUSVR)
#include "DeviceDelegateOculusVR.h"
#endif

#define STR(x) #x
#define TO_STRING(x) STR(x)

#if defined(OCULUSVR) && defined(STORE_BUILD)
#include "OVR_Platform.h"
#endif

#include <android/looper.h>
#include <unistd.h>
#include "VRBrowser.h"

#define JNI_METHOD(return_type, method_name) \
  JNIEXPORT return_type JNICALL              \
    Java_com_igalia_wolvic_PlatformActivity_##method_name

using namespace crow;

#if defined(OPENXR)
typedef DeviceDelegateOpenXR PlatformDeviceDelegate;
typedef DeviceDelegateOpenXRPtr PlatformDeviceDelegatePtr;
#elif defined(OCULUSVR)
typedef DeviceDelegateOculusVR PlatformDeviceDelegate;
typedef DeviceDelegateOculusVRPtr PlatformDeviceDelegatePtr;
#endif

namespace {

jobject
GetAssetManager(JNIEnv *aEnv, jobject aActivity) {
  jclass clazz = aEnv->GetObjectClass(aActivity);
  jmethodID method = aEnv->GetMethodID(clazz, "getAssets", "()Landroid/content/res/AssetManager;");
  jobject result = aEnv->CallObjectMethod(aActivity, method);
  if (!result) {
    VRB_ERROR("Failed to get AssetManager instance!");
  }
  return result;
}


struct AppContext {
  vrb::RunnableQueuePtr mQueue;
  BrowserEGLContextPtr mEgl;
  PlatformDeviceDelegatePtr mDevice;
  JavaContext mJavaContext;
};
typedef std::shared_ptr<AppContext> AppContextPtr;

AppContextPtr sAppContext;
}

void
CommandCallback(android_app *aApp, int32_t aCmd) {
  AppContext *ctx = (AppContext *) aApp->userData;

  switch (aCmd) {
    // A new ANativeWindow is ready for use. Upon receiving this command,
    // android_app->window will contain the new window surface.
    case APP_CMD_INIT_WINDOW:
      VRB_LOG("APP_CMD_INIT_WINDOW %p", aApp->window);
      if (!ctx->mEgl) {
        ctx->mEgl = BrowserEGLContext::Create();
        ctx->mEgl->Initialize(aApp->window);
        ctx->mEgl->MakeCurrent();
        VRB_GL_CHECK(glEnable(GL_DEPTH_TEST));
        VRB_GL_CHECK(glEnable(GL_CULL_FACE));
      } else {
        ctx->mEgl->UpdateNativeWindow(aApp->window);
        ctx->mEgl->MakeCurrent();
      }
      break;

    // The existing ANativeWindow needs to be terminated.  Upon receiving this command,
    // android_app->window still contains the existing window;
    // after calling android_app_exec_cmd it will be set to NULL.
    case APP_CMD_TERM_WINDOW:
      VRB_LOG("APP_CMD_TERM_WINDOW");
      if (ctx->mEgl) {
        ctx->mEgl->UpdateNativeWindow(nullptr);
      }
      break;
    // The app's activity has been paused.
    case APP_CMD_PAUSE:
      VRB_LOG("APP_CMD_PAUSE");
      BrowserWorld::Instance().Pause();
      break;

    // The app's activity has been resumed.
    case APP_CMD_RESUME:
      VRB_LOG("APP_CMD_RESUME");
      BrowserWorld::Instance().Resume();
      break;

    // the app's activity is being destroyed,
    // and waiting for the app thread to clean up and exit before proceeding.
    case APP_CMD_DESTROY:
      VRB_LOG("APP_CMD_DESTROY");
      break;

    default:
      break;
  }
}

extern "C" {

void
android_main(android_app *aAppState) {
  if (!ALooper_forThread()) {
    ALooper_prepare(0);
  }

  // Attach JNI thread
  JNIEnv *jniEnv;
  (*aAppState->activity->vm).AttachCurrentThread(&jniEnv, nullptr);

  // Set up activity & SurfaceView life cycle callbacks
  aAppState->userData = sAppContext.get();
  aAppState->onAppCmd = CommandCallback;


  if (!sAppContext) {
    sAppContext = std::make_shared<AppContext>();
    sAppContext->mQueue = vrb::RunnableQueue::Create(aAppState->activity->vm);
  }

  sAppContext->mQueue->AttachToThread();

  // Create Browser context
  crow::VRBrowser::InitializeJava(jniEnv, aAppState->activity->clazz);

  // Create device delegate
  sAppContext->mJavaContext.env = jniEnv;
  sAppContext->mJavaContext.vm = aAppState->activity->vm;
  sAppContext->mJavaContext.activity = aAppState->activity->clazz;

#if defined(OCULUSVR) && defined(STORE_BUILD)
  if (!ovr_IsPlatformInitialized()) {
      VRB_LOG("Performing entitlement with appId %s", TO_STRING(META_APP_ID));
      auto result = ovr_PlatformInitializeAndroidAsynchronous(TO_STRING(META_APP_ID), sAppContext->mJavaContext.activity, jniEnv);
      if (result == invalidRequestID) {
          // Initialization failed which means either the oculus service isn’t on the machine or they’ve hacked their DLL.
          VRB_ERROR("ovr_PlatformInitializeAndroidAsynchronous failed: %d", (int32_t) result);
          VRBrowser::HaltActivity(0);
      } else {
          VRB_LOG("ovr_PlatformInitializeAndroidAsynchronous succeeded");
          ovr_Entitlement_GetIsViewerEntitled();
      }
  } else {
      ovr_Entitlement_GetIsViewerEntitled();
  }
#endif

  sAppContext->mDevice = PlatformDeviceDelegate::Create(BrowserWorld::Instance().GetRenderContext(), &sAppContext->mJavaContext);
  BrowserWorld::Instance().RegisterDeviceDelegate(sAppContext->mDevice);

  // Initialize java
  auto assetManager = GetAssetManager(jniEnv, aAppState->activity->clazz);
  BrowserWorld::Instance().InitializeJava(jniEnv, aAppState->activity->clazz, assetManager);
  jniEnv->DeleteLocalRef(assetManager);

  auto MaybeInitGLAndEnterVR = [aAppState]() {
    if (!aAppState->window || !sAppContext->mEgl || BrowserWorld::Instance().IsGLInitialized())
      return;

    BrowserWorld::Instance().InitializeGL();
    sAppContext->mDevice->EnterVR(*sAppContext->mEgl);
  };
  // If 0 returns immediately without blocking. If negative, waits indefinitely for events.
  auto computeALooperTimeout = [aAppState]() {
      return BrowserWorld::Instance().IsPaused() && !sAppContext->mDevice->IsInVRMode() && aAppState->destroyRequested == 0 ? -1 : 0;
  };

  // EnterVR if the APP_CMD_INIT_WINDOW has been already received. Can be triggered in OpenXR
  // backend just by creating the XrInstance when the DeviceDelegate is created
  MaybeInitGLAndEnterVR();

  // Main render loop
  while (aAppState->destroyRequested == 0) {
    int events;
    android_poll_source *pSource;

    // Loop until all events are read. If the activity is paused use a blocking call to read events.
    while (ALooper_pollAll(computeALooperTimeout(), nullptr, &events, (void **) &pSource) >= 0) {
      // Process event.
      if (pSource) {
        pSource->process(aAppState, pSource);
      }
    }
#if defined(OPENXR)
    // OpenXR requires to wait for the XR_SESSION_STATE_READY to start presenting
    // We need to call ProcessEvents to make sure we receive the event.
    sAppContext->mDevice->ProcessEvents();
    if (sAppContext->mDevice->ShouldExitRenderLoop()) {
      ANativeActivity_finish(aAppState->activity);
      continue;
    }
#endif

    MaybeInitGLAndEnterVR();

    if (sAppContext->mEgl) {
      sAppContext->mEgl->MakeCurrent();
    }
    sAppContext->mQueue->ProcessRunnables();

    if (!BrowserWorld::Instance().IsPaused() && sAppContext->mDevice->IsInVRMode()) {
      BrowserWorld::Instance().Draw();
    }
  }

  sAppContext->mEgl->MakeCurrent();
  sAppContext->mQueue->ProcessRunnables();
  sAppContext->mDevice->OnDestroy();
  BrowserWorld::Instance().ShutdownGL();
  BrowserWorld::Instance().ShutdownJava();
  BrowserWorld::Destroy();
  sAppContext->mEgl->Destroy();
  sAppContext->mEgl.reset();
  sAppContext->mDevice.reset();

  aAppState->activity->vm->DetachCurrentThread();
  VRB_LOG("Exiting native thread");
}

JNI_METHOD(void, queueRunnable)
(JNIEnv *aEnv, jobject, jobject aRunnable) {
  if (sAppContext) {
    sAppContext->mQueue->AddRunnable(aEnv, aRunnable);
  } else {
    VRB_ERROR("Failed to queue Runnable from UI thread. Render thread AppContext has not been initialized.")
  }
}

JNI_METHOD(jboolean, platformExit)
(JNIEnv *, jobject) {
  if (sAppContext && sAppContext->mDevice) {
    return (jboolean) sAppContext->mDevice->ExitApp();
  }
  return (jboolean) false;
}

jint JNI_OnLoad(JavaVM* aVm, void*) {
  if (sAppContext) {
    return JNI_VERSION_1_6;
  }
  sAppContext = std::make_shared<AppContext>();
  sAppContext->mQueue = vrb::RunnableQueue::Create(aVm);
  return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM* vm, void* reserved) {
  sAppContext = nullptr;
}


} // extern "C"
