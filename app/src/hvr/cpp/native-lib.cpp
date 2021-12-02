/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include <jni.h>
#include <string>
#include <GLES2/gl2.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include "BrowserWorld.h"
#include "vrb/Logger.h"
#include "vrb/GLError.h"
#include "BrowserEGLContext.h"
#include <cstdlib>
#include <vrb/RunnableQueue.h>
#include "DeviceDelegateOpenXR.h"
#include <thread>

#include <android/looper.h>
#include <unistd.h>
#include "VRBrowser.h"

#define JNI_METHOD(return_type, method_name) \
  JNIEXPORT return_type JNICALL              \
    Java_org_mozilla_vrbrowser_PlatformActivity_##method_name

using namespace crow;

typedef DeviceDelegateOpenXR PlatformDeviceDelegate;
typedef DeviceDelegateOpenXRPtr PlatformDeviceDelegatePtr;

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
  pthread_t mThreadId { 0 };
  JavaContext mJavaContext;

  void RenderThread() {
    // Attach JNI thread
    mJavaContext.vm->AttachCurrentThread(&mJavaContext.env, nullptr);
    mQueue->AttachToThread();

    // Create Browser context
    crow::VRBrowser::InitializeJava(mJavaContext.env, mJavaContext.activity);

    // Create device delegate
    mDevice = PlatformDeviceDelegate::Create(BrowserWorld::Instance().GetRenderContext(), &mJavaContext);
    BrowserWorld::Instance().RegisterDeviceDelegate(mDevice);

    // Initialize java
    auto assetManager = GetAssetManager(mJavaContext.env, mJavaContext.activity);
    BrowserWorld::Instance().InitializeJava(mJavaContext.env, mJavaContext.activity, assetManager);
    mJavaContext.env->DeleteLocalRef(assetManager);

    while (true) {
      mQueue->ProcessRunnables();

      if (mDevice->ShouldExitRenderLoop()) {
        break;
      }

      //LOGI("sessionRunning : %d",sessionRunning);
      if (mDevice->IsInVRMode() && mEgl && mEgl->IsSurfaceReady() && !BrowserWorld::Instance().IsPaused()) {
        BrowserWorld::Instance().Draw();
      }
      else {
        // Throttle loop since xrWaitFrame won't be called.
        timespec total_time;
        timespec left_time;
        total_time.tv_sec = 0;
        total_time.tv_nsec = (long)(250000000) ;
        nanosleep(&total_time, &left_time);

        // OpenXR requires to wait for the XR_SESSION_STATE_READY to start presenting
        // We need to call ProcessEvents to make sure we receive the event.
        mDevice->ProcessEvents();
      }
    }

    BrowserWorld::Instance().ShutdownJava();
    BrowserWorld::Destroy();
    mEgl->Destroy();
    mEgl.reset();
    mDevice.reset();
    mJavaContext.vm->DetachCurrentThread();
  }
};
typedef std::shared_ptr<AppContext> AppContextPtr;

AppContextPtr sAppContext;

void* StartRenderThread(void*) {
  sAppContext->RenderThread();
  pthread_exit(nullptr);
  return nullptr;
}
}

extern "C" {

JNI_METHOD(void, nativeOnCreate)
(JNIEnv *aEnv, jobject activity) {
  sAppContext->mJavaContext.activity = aEnv->NewGlobalRef(activity);
  pthread_create(&sAppContext->mThreadId, nullptr, StartRenderThread, sAppContext.get());
}

JNI_METHOD(void, nativeOnDestroy)
(JNIEnv *aEnv, jobject) {
  pthread_join(sAppContext->mThreadId, nullptr);
  if (sAppContext && sAppContext->mJavaContext.activity) {
    aEnv->DeleteGlobalRef(sAppContext->mJavaContext.activity);
    sAppContext->mJavaContext.activity = nullptr;
  }
}


JNI_METHOD(void, nativeOnPause)
(JNIEnv *aEnv, jobject) {
  BrowserWorld::Instance().Pause();
}

JNI_METHOD(void, nativeOnResume)
(JNIEnv *aEnv, jobject) {
  BrowserWorld::Instance().Resume();
}

JNI_METHOD(void, nativeOnSurfaceChanged)
(JNIEnv *aEnv, jobject, jobject surface) {

  ANativeWindow* window = ANativeWindow_fromSurface(aEnv, surface);
  if (!sAppContext->mEgl) {
    sAppContext->mEgl = BrowserEGLContext::Create();
    sAppContext->mEgl->Initialize(window);
    sAppContext->mEgl->MakeCurrent();
    VRB_GL_CHECK(glEnable(GL_DEPTH_TEST));
    VRB_GL_CHECK(glEnable(GL_CULL_FACE));
    BrowserWorld::Instance().InitializeGL();
  } else {
    sAppContext->mEgl->UpdateNativeWindow(window);
    sAppContext->mEgl->MakeCurrent();
  }

   sAppContext->mDevice->EnterVR(*sAppContext->mEgl);
}

JNI_METHOD(void, nativeOnSurfaceDestroyed)
(JNIEnv *aEnv, jobject) {
  if (sAppContext && sAppContext->mDevice) {
    sAppContext->mDevice->LeaveVR();
    BrowserWorld::Instance().ShutdownGL();
  }
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
  sAppContext->mJavaContext.vm = aVm;
  sAppContext->mQueue = vrb::RunnableQueue::Create(aVm);
  return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM* vm, void* reserved) {
  sAppContext = nullptr;
}

} // extern "C"
